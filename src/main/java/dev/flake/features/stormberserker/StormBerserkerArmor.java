package dev.flake.features.stormberserker;

import dev.flake.combat.CombatEquipment;
import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Stuns;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.Loadout;
import dev.flake.features.loadout.LoadoutService;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Vertical slice for the Storm Berserker armor set:
 * <ul>
 *   <li><b>Per-piece trade-off</b>: +3% attack speed per piece (+12% for 4/4) at the cost of +2% incoming damage per piece (+8% for 4/4).</li>
 *   <li><b>4/4 Full Set Bonus</b>: Killing or stunning an enemy grants +1 stack (max 3 stacks, 4s / 80 ticks duration);
 *       each stack grants +8% outgoing damage (weapon and skill damage).</li>
 * </ul>
 */
public final class StormBerserkerArmor implements Lifecycle {
	public static final double ATTACK_SPEED_PER_PIECE = 0.03;
	public static final float INCOMING_DAMAGE_PER_PIECE = 0.02f;
	public static final int MAX_STACKS = 3;
	public static final int STACK_DURATION_TICKS = 80;
	public static final float OUTGOING_DAMAGE_PER_STACK = 0.08f;
	public static final String CUSTOM_DATA_KEY = "flake_storm_berserker";
	private static final int STUN_ATTRIBUTION_WINDOW_TICKS = 40;
	private static final int STUN_DEDUP_TICKS = 10;

	private static final Identifier ATTACK_SPEED_MOD = Identifier.fromNamespaceAndPath("flake", "storm_berserker_attack_speed");

	private record RecentHit(UUID attackerId, int tick) {}

	private static final class State {
		final Set<UUID> fullSets = new HashSet<>();
		final Map<UUID, Integer> pieces = new HashMap<>();
		final Map<UUID, Integer> stacks = new HashMap<>();
		final Map<UUID, Integer> expiresAt = new HashMap<>();
		final Map<UUID, RecentHit> recentAttackers = new HashMap<>();
		final Map<UUID, Integer> lastStunCreditTick = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static boolean installed;
	private static boolean registered;

	private StormBerserkerArmor() {}

	public static void install() {
		if (installed) return;
		installed = true;
		ContentCatalog.registerLifecycle(new StormBerserkerArmor());
		CombatEquipment.register(StormBerserkerArmor::applyKit);
		CombatModifiers.damageScale(StormBerserkerArmor::scaleDamage);
		CombatModifiers.outgoingSkillDamage((attacker, target, damage) -> scaleOutgoingSkillDamage(attacker, damage));
	}

	/** Creates a copper armor piece tagged for the Storm Berserker set. */
	public static ItemStack createPiece(Loadout.ArmorSlot slot) {
		Item item = switch (slot) {
			case HELMET -> Items.COPPER_HELMET;
			case CHESTPLATE -> Items.COPPER_CHESTPLATE;
			case LEGGINGS -> Items.COPPER_LEGGINGS;
			case BOOTS -> Items.COPPER_BOOTS;
		};
		var stack = new ItemStack(item);
		var tag = new CompoundTag();
		tag.putBoolean(CUSTOM_DATA_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	public static boolean isStormBerserkerPiece(ItemStack stack) {
		if (stack.isEmpty()) return false;
		var data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(CUSTOM_DATA_KEY, false);
	}

	/** Pure calculation of the attack speed multiplier delta for {@code pieces} Storm Berserker pieces. */
	public static double attackSpeedModifierForPieces(int pieces) {
		return Math.clamp(pieces, 0, 4) * ATTACK_SPEED_PER_PIECE;
	}

	/** Pure calculation of the incoming damage multiplier for {@code pieces} Storm Berserker pieces. */
	public static float incomingDamageMultiplierForPieces(int pieces) {
		return 1.0f + Math.clamp(pieces, 0, 4) * INCOMING_DAMAGE_PER_PIECE;
	}

	/** Pure calculation of incoming damage scaled by equipped Storm Berserker pieces. */
	public static float scaleIncomingDamage(int pieces, float damage) {
		if (damage <= 0f || !Float.isFinite(damage)) return 0f;
		return damage * incomingDamageMultiplierForPieces(pieces);
	}

	/** Pure calculation of the outgoing damage multiplier for {@code stacks} Berserker stacks. */
	public static float outgoingDamageMultiplierForStacks(int stacks) {
		return 1.0f + Math.clamp(stacks, 0, MAX_STACKS) * OUTGOING_DAMAGE_PER_STACK;
	}

	/** Pure calculation of outgoing damage scaled by active Berserker stacks. */
	public static float scaleOutgoingDamage(int stacks, float damage) {
		if (damage <= 0f || !Float.isFinite(damage)) return 0f;
		return damage * outgoingDamageMultiplierForStacks(stacks);
	}

	/** Pure calculation of stack count after gaining a stack at {@code nowTick}. */
	public static int nextStackCount(int currentStacks, int nowTick, int expiresAtTick) {
		if (nowTick >= expiresAtTick || currentStacks <= 0) return 1;
		return Math.min(MAX_STACKS, currentStacks + 1);
	}

	/** Pure check returning active stacks at {@code nowTick}, or 0 if expired. */
	public static int activeStackCount(int storedStacks, int nowTick, int expiresAtTick) {
		return nowTick < expiresAtTick ? Math.clamp(storedStacks, 0, MAX_STACKS) : 0;
	}

	public static int activePieces(ServerPlayer player) {
		UUID id = player.getUUID();
		Integer stored = STATES.of(player).pieces.get(id);
		if (stored != null) return stored;
		if (!DuelRuntime.fighting(id)) {
			int worn = countWornPieces(player);
			if (worn > 0) return worn;
			var build = LoadoutService.of(player.level().getServer()).get(id);
			return build != null && worn == 4 ? build.stormBerserkerPieces() : 0;
		}
		return 0;
	}

	public static boolean hasActiveFullSet(ServerPlayer player) {
		UUID id = player.getUUID();
		if (STATES.of(player).fullSets.contains(id)) return true;
		if (!DuelRuntime.fighting(id)) {
			return countWornPieces(player) == 4;
		}
		return false;
	}

	private static int countWornPieces(ServerPlayer player) {
		int count = 0;
		if (isStormBerserkerPiece(player.getItemBySlot(EquipmentSlot.HEAD))) count++;
		if (isStormBerserkerPiece(player.getItemBySlot(EquipmentSlot.CHEST))) count++;
		if (isStormBerserkerPiece(player.getItemBySlot(EquipmentSlot.LEGS))) count++;
		if (isStormBerserkerPiece(player.getItemBySlot(EquipmentSlot.FEET))) count++;
		return count;
	}

	public static int activeStacks(ServerPlayer player) {
		if (!hasActiveFullSet(player)) return 0;
		var server = player.level().getServer();
		if (server == null) return 0;
		var state = STATES.of(player);
		UUID id = player.getUUID();
		int now = server.getTickCount();
		int active = activeStackCount(state.stacks.getOrDefault(id, 0), now, state.expiresAt.getOrDefault(id, 0));
		if (active == 0 && state.stacks.containsKey(id)) {
			state.stacks.remove(id);
			state.expiresAt.remove(id);
		}
		return active;
	}

	private static float scaleDamage(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0f || !Float.isFinite(amount)) return amount;
		if (source.getEntity() instanceof ServerPlayer attacker) {
			int stacks = activeStacks(attacker);
			if (stacks > 0) amount = scaleOutgoingDamage(stacks, amount);
		}
		if (victim instanceof ServerPlayer hit) {
			int pieces = activePieces(hit);
			if (pieces > 0) amount = scaleIncomingDamage(pieces, amount);
		}
		return amount;
	}

	public static float scaleOutgoingSkillDamage(ServerPlayer attacker, float damage) {
		int stacks = activeStacks(attacker);
		return stacks > 0 ? scaleOutgoingDamage(stacks, damage) : damage;
	}

	/** Applies per-piece attack speed modifiers and initializes 4/4 set tracking at duel kit preparation. */
	public static void applyKit(ServerPlayer player, Loadout build) {
		resetPlayer(player);
		if (build == null) return;
		int pieces = build.stormBerserkerPieces();
		var state = STATES.of(player);
		if (pieces > 0) {
			state.pieces.put(player.getUUID(), pieces);
			var attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
			if (attackSpeed != null) {
				attackSpeed.removeModifier(ATTACK_SPEED_MOD);
				attackSpeed.addTransientModifier(new AttributeModifier(
						ATTACK_SPEED_MOD,
						attackSpeedModifierForPieces(pieces),
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
				));
			}
		}
		if (build.hasFullStormBerserkerSet()) {
			state.fullSets.add(player.getUUID());
		}
	}

	@Override
	public void register() {
		if (registered) return;
		registered = true;

		CombatModifiers.afterDamage((victim, source, taken, blocked) -> {
			if (blocked || taken <= 0f) return;
			if (!(source.getEntity() instanceof ServerPlayer attacker) || victim == attacker) return;
			var server = victim.level().getServer();
			if (server == null) return;
			STATES.of(server).recentAttackers.put(victim.getUUID(), new RecentHit(attacker.getUUID(), server.getTickCount()));
		});

		Stuns.registerOnStun((target, ticks) -> {
			if (ticks <= 0) return;
			var server = target.level().getServer();
			if (server == null) return;
			int now = server.getTickCount();
			var state = STATES.of(server);
			ServerPlayer stunner = resolveStunner(target, state, now, server);
			if (stunner == null || stunner == target) return;
			Integer prevCredit = state.lastStunCreditTick.get(target.getUUID());
			if (prevCredit != null && now - prevCredit < STUN_DEDUP_TICKS) return;
			if (grantStack(stunner, state, now)) {
				state.lastStunCreditTick.put(target.getUUID(), now);
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			var server = entity.level().getServer();
			if (server == null) return;
			var state = STATES.of(server);
			ServerPlayer killer = null;
			if (source.getEntity() instanceof ServerPlayer sp && sp != entity) {
				killer = sp;
			} else if (entity.getLastHurtByMob() instanceof ServerPlayer sp && sp != entity) {
				killer = sp;
			}
			if (killer != null) {
				grantStack(killer, state, server.getTickCount());
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> resetPlayer(handler.player));
	}

	private static ServerPlayer resolveStunner(LivingEntity target, State state, int now, net.minecraft.server.MinecraftServer server) {
		RecentHit recent = state.recentAttackers.get(target.getUUID());
		if (recent != null && now - recent.tick() <= STUN_ATTRIBUTION_WINDOW_TICKS) {
			ServerPlayer sp = server.getPlayerList().getPlayer(recent.attackerId());
			if (sp != null) return sp;
		}
		if (target.getLastHurtByMob() instanceof ServerPlayer sp
				&& target.tickCount - target.getLastHurtByMobTimestamp() <= STUN_ATTRIBUTION_WINDOW_TICKS) {
			return sp;
		}
		return null;
	}

	private static boolean grantStack(ServerPlayer player, State state, int now) {
		if (!player.isAlive() || !hasActiveFullSet(player)) return false;
		UUID id = player.getUUID();
		int current = state.stacks.getOrDefault(id, 0);
		int expires = state.expiresAt.getOrDefault(id, 0);
		int next = nextStackCount(current, now, expires);
		state.stacks.put(id, next);
		state.expiresAt.put(id, now + STACK_DURATION_TICKS);
		return true;
	}

	public static void resetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		var state = STATES.of(player);
		state.fullSets.remove(id);
		state.pieces.remove(id);
		state.stacks.remove(id);
		state.expiresAt.remove(id);
		state.recentAttackers.remove(id);
		state.recentAttackers.values().removeIf(hit -> hit.attackerId().equals(id));
		state.lastStunCreditTick.remove(id);
		var attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed != null) attackSpeed.removeModifier(ATTACK_SPEED_MOD);
	}

	@Override
	public void reset(ServerPlayer player) {
		resetPlayer(player);
	}
}
