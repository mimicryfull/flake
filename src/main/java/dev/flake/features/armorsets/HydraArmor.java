package dev.flake.features.armorsets;

import dev.flake.Flake;
import dev.flake.combat.CombatEquipment;
import dev.flake.combat.CombatModifiers;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;

/**
 * Hydra Armor (Iron tier, 4/4 set bonus):
 * Grants +20.0 HP (+10 hearts) max health and +20% health regeneration rate, escalating to +40%
 * when health is at or below 10% max HP, accompanied by crimson/dark-red aura effects and
 * a 20% opacity burgundy first-person tint.
 */
public final class HydraArmor implements Lifecycle {
	public static final double MAX_HEALTH_BONUS = 20.0;
	public static final float BASE_REGEN_MULTIPLIER = 1.20f;
	public static final float FRENZY_REGEN_MULTIPLIER = 1.40f;
	public static final float FRENZY_HEALTH_RATIO = 0.10f;
	public static final int PASSIVE_REGEN_INTERVAL_TICKS = 20;
	public static final float BASE_PASSIVE_HEAL = 0.50f;

	private static final int VFX_SYNC_INTERVAL_TICKS = 15;
	private static final int VFX_LEASE_TICKS = 35;

	private static final Identifier HEALTH_MOD = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "hydra_armor_health");
	public static final CustomArmorSet SET = new CustomArmorSet("hydra", ArmorMaterial.HYDRA);

	private static final class State {
		final Map<UUID, Integer> nextRegenTick = new HashMap<>();
		final Map<UUID, Integer> nextVfxTick = new HashMap<>();
		final Set<UUID> activeAura = new HashSet<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static boolean installed;

	private HydraArmor() {}

	public static void registerItems() { SET.registerItems(); }
	public static Item itemFor(ArmorSlot slot) { return SET.itemFor(slot); }

	/** True when remaining health is at or below 10% of max health. */
	public static boolean isFrenzy(float health, float maxHealth) {
		return maxHealth > 0f && health <= maxHealth * FRENZY_HEALTH_RATIO;
	}

	/** Pure healing rate multiplier: 1.20x above 10% HP, 1.40x at or below 10% max HP. */
	public static float regenScale(float health, float maxHealth) {
		return isFrenzy(health, maxHealth) ? FRENZY_REGEN_MULTIPLIER : BASE_REGEN_MULTIPLIER;
	}

	public static void install() {
		if (installed) return;
		installed = true;
		ContentCatalog.registerLifecycle(new HydraArmor());
		CombatEquipment.register((player, loadout) -> {
			SET.applyKit(player, loadout);
			syncHealth(player);
		});
		CombatModifiers.healingScale((entity, amount) -> {
			if (entity instanceof ServerPlayer player && SET.hasActiveFullSet(player)) {
				return regenScale(player.getHealth(), player.getMaxHealth());
			}
			return 1.0f;
		});
	}

	@Override
	public void register() {
		ServerTickEvents.END_SERVER_TICK.register(HydraArmor::tick);
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
		state.nextRegenTick.remove(id);
		state.nextVfxTick.remove(id);
		if (state.activeAura.remove(id)) {
			CustomArmorSet.broadcastAuraState(player, Phase.HYDRA_AURA_OFF, 0f, 0);
		}
		syncHealth(player);
	}

	private static void syncHealth(ServerPlayer player) {
		boolean active = SET.hasActiveFullSet(player);
		CustomArmorSet.syncAttribute(
				player,
				Attributes.MAX_HEALTH,
				HEALTH_MOD,
				MAX_HEALTH_BONUS,
				AttributeModifier.Operation.ADD_VALUE,
				active);
		if (!active && player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void tick(MinecraftServer server) {
		var state = STATES.of(server);
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			syncHealth(player);
			UUID id = player.getUUID();
			boolean active = SET.hasActiveFullSet(player);
			if (!active) {
				state.nextRegenTick.remove(id);
				state.nextVfxTick.remove(id);
				if (state.activeAura.remove(id)) {
					CustomArmorSet.broadcastAuraState(player, Phase.HYDRA_AURA_OFF, 0f, 0);
				}
				continue;
			}
			boolean frenzy = isFrenzy(player.getHealth(), player.getMaxHealth());
			if (state.activeAura.add(id) || now >= state.nextVfxTick.getOrDefault(id, 0)) {
				state.nextVfxTick.put(id, now + VFX_SYNC_INTERVAL_TICKS);
				CustomArmorSet.broadcastAuraState(player, Phase.HYDRA_AURA, frenzy ? 1.0f : 0.0f, VFX_LEASE_TICKS);
			}
			if (DuelRuntime.blocksAbility(id) || player.getHealth() >= player.getMaxHealth()) continue;
			int due = state.nextRegenTick.computeIfAbsent(id, k -> now + PASSIVE_REGEN_INTERVAL_TICKS);
			if (now >= due) {
				state.nextRegenTick.put(id, now + PASSIVE_REGEN_INTERVAL_TICKS);
				player.heal(BASE_PASSIVE_HEAL * regenScale(player.getHealth(), player.getMaxHealth()));
			}
		}
	}
}
