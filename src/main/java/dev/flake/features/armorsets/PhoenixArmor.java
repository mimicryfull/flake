package dev.flake.features.armorsets;

import dev.flake.combat.CombatEquipment;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.Loadout.ArmorMaterial;
import dev.flake.features.loadout.Loadout.ArmorSlot;
import dev.flake.render.vfx.CombatVfxPayload.Phase;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;

/**
 * Phoenix Armor (Iron tier, 4/4 set bonus):
 * Emits a pulsing yellow-orange aura every 2 seconds (4.0m radius) dealing 2.0 HP to opponents,
 * increasing to 4.0 HP when the wearer is below 30% max HP.
 */
public final class PhoenixArmor implements Lifecycle {
	public static final double AURA_RADIUS = 4.0;
	public static final int DAMAGE_INTERVAL_TICKS = 40;
	public static final float BASE_AURA_DAMAGE = 2.0f;
	public static final float LOW_HP_AURA_DAMAGE = 4.0f;
	public static final float LOW_HP_THRESHOLD = 0.30f;

	private static final int VFX_SYNC_INTERVAL_TICKS = 15;
	private static final int VFX_LEASE_TICKS = 35;

	public static final CustomArmorSet SET = new CustomArmorSet("phoenix", ArmorMaterial.PHOENIX);

	private static final class State {
		final Map<UUID, Integer> nextDamageTick = new HashMap<>();
		final Map<UUID, Integer> nextVfxTick = new HashMap<>();
		final Set<UUID> activeAura = new HashSet<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static boolean installed;

	private PhoenixArmor() {}

	public static void registerItems() { SET.registerItems(); }
	public static Item itemFor(ArmorSlot slot) { return SET.itemFor(slot); }

	/** Pure calculation of Phoenix aura damage for wearer HP. */
	public static float auraDamage(float health, float maxHealth) {
		if (maxHealth <= 0f) return BASE_AURA_DAMAGE;
		return health * 10.0f < maxHealth * 3.0f ? LOW_HP_AURA_DAMAGE : BASE_AURA_DAMAGE;
	}

	public static void install() {
		if (installed) return;
		installed = true;
		ContentCatalog.registerLifecycle(new PhoenixArmor());
		CombatEquipment.register(SET::applyKit);
	}

	@Override
	public void register() {
		ServerTickEvents.END_SERVER_TICK.register(PhoenixArmor::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> resetPlayer(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SET.clearServer(server);
			STATES.remove(server);
		});
	}

	@Override
	public void reset(ServerPlayer player) { resetPlayer(player); }

	public static void resetPlayer(ServerPlayer player) {
		SET.resetPlayer(player);
		var state = STATES.of(player);
		UUID id = player.getUUID();
		state.nextDamageTick.remove(id);
		state.nextVfxTick.remove(id);
		if (state.activeAura.remove(id)) {
			CustomArmorSet.broadcastAuraState(player, Phase.PHOENIX_AURA_OFF, 0f, 0);
		}
	}

	private static void tick(MinecraftServer server) {
		var state = STATES.of(server);
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			boolean active = SET.hasActiveFullSet(player) && !DuelRuntime.blocksAbility(id);
			if (!active) {
				state.nextDamageTick.remove(id);
				state.nextVfxTick.remove(id);
				if (state.activeAura.remove(id)) {
					CustomArmorSet.broadcastAuraState(player, Phase.PHOENIX_AURA_OFF, 0f, 0);
				}
				continue;
			}
			boolean lowHp = player.getHealth() < player.getMaxHealth() * LOW_HP_THRESHOLD;
			if (state.activeAura.add(id) || now >= state.nextVfxTick.getOrDefault(id, 0)) {
				state.nextVfxTick.put(id, now + VFX_SYNC_INTERVAL_TICKS);
				CustomArmorSet.broadcastAuraState(player, Phase.PHOENIX_AURA, lowHp ? 1.0f : 0.0f, VFX_LEASE_TICKS);
			}
			int due = state.nextDamageTick.computeIfAbsent(id, k -> now + DAMAGE_INTERVAL_TICKS);
			if (now >= due) {
				state.nextDamageTick.put(id, now + DAMAGE_INTERVAL_TICKS);
				applyAuraPulse(player, lowHp);
			}
		}
	}

	private static void applyAuraPulse(ServerPlayer wearer, boolean lowHp) {
		if (!(wearer.level() instanceof ServerLevel level)) return;
		float damage = auraDamage(wearer.getHealth(), wearer.getMaxHealth());
		var targets = CustomArmorSet.targetsAround(wearer, level, AURA_RADIUS);
		if (targets.isEmpty()) return;
		boolean hitAny = false;
		for (var target : targets) {
			hitAny |= target.hurtServer(level, wearer.damageSources().onFire(), damage);
		}
		if (hitAny) {
			level.playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(),
					SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, lowHp ? 0.36f : 0.22f, lowHp ? 1.35f : 1.10f);
		}
	}
}
