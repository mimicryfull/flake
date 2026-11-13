package dev.flake.features.mirrordagger;

import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * «Дзеркальний кинджал» (Mirror Dagger): швидка атака (+25% до швидкості, 2.0 замість 1.6) і 5.5 шкоди.
 * Пасивне комбо: 3 послідовних ближніх удари по одній цілі заряджають «дзеркальне пробиття» (до 5 с або до удару).
 * Наступний ближній удар по цій цілі ігнорує 30% її зменшення шкоди (броня, опір, блокування щитом).
 *
 * <p>Формула компенсації зменшення шкоди:
 * Якщо броня ({@code armorMult}) та ефект Опору ({@code resMult = 1 - 0.2 * level}) пропускають сумарно
 * частку {@code m = clamp(armorMult * resMult, 0.08, 1.0)}, то частка зменшення дорівнює {@code r = 1 - m}.
 * Ігнорування 30% цього зменшення означає нову підсумкову частку {@code m_target = 1 - 0.70 * r = 0.30 + 0.70 * m}.
 * Тому вхідна шкода перед розрахунком броні й Опору множиться на {@code scale = min(3.5, 0.70 + 0.30 / m)}.
 * Якщо удар повністю заблокований щитом, 30% базової шкоди проходять крізь щит як проникаюча шкода.
 */
public final class MirrorDaggerRuntime {
	public static final int COMBO_HITS = 3;
	public static final int PIERCE_DURATION_TICKS = 100;
	public static final float REDUCTION_IGNORE_FRACTION = 0.30f;
	public static final float MAX_PIERCE_MULTIPLIER = 3.5f;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "mirror_dagger");

	private record ComboState(UUID targetId, int hits) {}
	private record ArmedPierce(UUID targetId, int untilTick) {}
	private record PendingShieldPierce(UUID victimId, float baseAmount, int tick) {}

	private static final class State {
		final Map<UUID, ComboState> combos = new HashMap<>();
		final Map<UUID, ArmedPierce> armed = new HashMap<>();
		final Map<UUID, PendingShieldPierce> pendingShield = new HashMap<>();
		final Set<UUID> skipComboIncrement = new HashSet<>();
		boolean applyingShieldPierce;
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static boolean registered;

	public static Item ITEM;

	private MirrorDaggerRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isDagger(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	/**
	 * Pure damage-scaling formula for ignoring 30% of armor + Resistance mitigation.
	 * Returns 1.0f when the target has no damage reduction (m == 1.0f).
	 */
	public static float pierceMultiplier(float armor, float toughness, int resistanceAmplifier, float incomingDamage) {
		float safeDamage = Math.max(0.0f, incomingDamage);
		float safeArmor = Math.max(0.0f, armor);
		float safeToughness = Math.max(0.0f, toughness);

		float effectiveArmor = Math.max(safeArmor / 5.0f, safeArmor - (4.0f * safeDamage) / (safeToughness + 8.0f));
		float armorReduction = Math.clamp(effectiveArmor / 25.0f, 0.0f, 0.80f);
		float armorMult = 1.0f - armorReduction;

		float resReduction = resistanceAmplifier >= 0
				? Math.clamp(0.20f * (resistanceAmplifier + 1), 0.0f, 0.92f)
				: 0.0f;
		float resMult = 1.0f - resReduction;

		float mitigatedMult = Math.clamp(armorMult * resMult, 0.08f, 1.0f);
		float scale = (1.0f - REDUCTION_IGNORE_FRACTION) + REDUCTION_IGNORE_FRACTION / mitigatedMult;
		return Math.min(MAX_PIERCE_MULTIPLIER, scale);
	}

	public static float scaleMeleeDamage(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0f) return amount;
		if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker) return amount;
		if (!isDagger(attacker.getMainHandItem())) return amount;
		MinecraftServer server = attacker.level().getServer();
		if (server == null) return amount;

		State state = STATES.of(server);
		if (state.applyingShieldPierce) return amount;

		UUID attackerId = attacker.getUUID();
		ArmedPierce pierce = state.armed.get(attackerId);
		if (pierce == null) return amount;

		int now = server.getTickCount();
		if (now > pierce.untilTick()) {
			state.armed.remove(attackerId);
			return amount;
		}
		if (!pierce.targetId().equals(victim.getUUID())) return amount;

		state.armed.remove(attackerId);
		state.skipComboIncrement.add(attackerId);
		state.pendingShield.put(attackerId, new PendingShieldPierce(victim.getUUID(), amount, now));

		float armor = victim.getArmorValue();
		float toughness = (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		var resistance = victim.getEffect(MobEffects.RESISTANCE);
		int resAmp = resistance != null ? resistance.getAmplifier() : -1;
		float scale = pierceMultiplier(armor, toughness, resAmp, amount);

		if (victim.level() instanceof ServerLevel level) {
			level.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
					SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.45f, 1.55f);
			level.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
					SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.85f, 1.25f);
		}
		return amount * scale;
	}

	public static void afterMeleeDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
		if (blocked || taken <= 0f) return;
		if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker) return;
		if (!isDagger(attacker.getMainHandItem())) return;
		MinecraftServer server = attacker.level().getServer();
		if (server == null) return;

		State state = STATES.of(server);
		if (state.applyingShieldPierce) return;

		UUID attackerId = attacker.getUUID();
		UUID victimId = victim.getUUID();
		state.pendingShield.remove(attackerId);

		if (state.skipComboIncrement.remove(attackerId)) {
			state.combos.put(attackerId, new ComboState(victimId, 0));
			return;
		}

		ComboState prev = state.combos.get(attackerId);
		if (prev == null || !prev.targetId().equals(victimId)) {
			state.armed.remove(attackerId);
			state.combos.put(attackerId, new ComboState(victimId, 1));
			return;
		}

		int nextHits = prev.hits() + 1;
		if (nextHits >= COMBO_HITS) {
			int now = server.getTickCount();
			state.armed.put(attackerId, new ArmedPierce(victimId, now + PIERCE_DURATION_TICKS));
			state.combos.put(attackerId, new ComboState(victimId, 0));
			if (attacker.level() instanceof ServerLevel level) {
				level.playSound(null, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(),
						SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.70f, 1.65f);
			}
		} else {
			state.combos.put(attackerId, new ComboState(victimId, nextHits));
		}
	}

	public static void register() {
		if (registered) return;
		registered = true;

		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (!blocked) return;
			if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
			MinecraftServer server = attacker.level().getServer();
			if (server == null) return;

			State state = STATES.of(server);
			if (state.applyingShieldPierce) return;
			PendingShieldPierce pending = state.pendingShield.remove(attacker.getUUID());
			state.skipComboIncrement.remove(attacker.getUUID());
			if (pending == null || !pending.victimId().equals(victim.getUUID())) return;
			if (pending.tick() != server.getTickCount()) return;
			if (!(victim.level() instanceof ServerLevel level) || !victim.isAlive()) return;

			float shieldPierceDamage = pending.baseAmount() * REDUCTION_IGNORE_FRACTION;
			if (shieldPierceDamage <= 0f) return;
			state.applyingShieldPierce = true;
			try {
				victim.hurtServer(level, attacker.damageSources().indirectMagic(attacker, attacker), shieldPierceDamage);
			} finally {
				state.applyingShieldPierce = false;
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			State state = STATES.of(server);
			int now = server.getTickCount();
			state.armed.entrySet().removeIf(entry -> entry.getValue().untilTick() <= now);
			state.pendingShield.entrySet().removeIf(entry -> entry.getValue().tick() < now);
			state.skipComboIncrement.clear();
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}

	public static void reset(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		State state = STATES.of(server);
		UUID id = player.getUUID();
		state.combos.remove(id);
		state.armed.remove(id);
		state.pendingShield.remove(id);
		state.skipComboIncrement.remove(id);
		state.combos.values().removeIf(combo -> combo.targetId().equals(id));
		state.armed.values().removeIf(pierce -> pierce.targetId().equals(id));
		state.pendingShield.values().removeIf(pending -> pending.victimId().equals(id));
	}
}
