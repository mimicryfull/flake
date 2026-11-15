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
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;

/**
 * Barrel Armor (Iron tier, 4/4 set bonus):
 * Passively increases movement speed by +20%, and after every 30% max HP lost emits a
 * dark-brown shock pulse (4.2m radius) that knocks opponents away.
 */
public final class BarrelArmor implements Lifecycle {
	public static final double SPEED_BONUS = 0.20;
	public static final float HP_TRIGGER_FRACTION = 0.30f;
	public static final double PULSE_RADIUS = 4.2;
	public static final double KNOCKBACK_HORIZONTAL = 1.05;
	public static final double KNOCKBACK_VERTICAL = 0.36;

	private static final Identifier SPEED_MOD = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "barrel_armor_speed");
	public static final CustomArmorSet SET = new CustomArmorSet("barrel", ArmorMaterial.BARREL);

	private static final class State {
		final Map<UUID, Float> accumulatedLoss = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static boolean installed;

	private BarrelArmor() {}

	public static void registerItems() { SET.registerItems(); }
	public static Item itemFor(ArmorSlot slot) { return SET.itemFor(slot); }

	/** Pure damage threshold required per Barrel knockback pulse. */
	public static float thresholdDamage(float maxHealth) {
		return Math.max(1.0f, maxHealth * HP_TRIGGER_FRACTION);
	}

	public static void install() {
		if (installed) return;
		installed = true;
		ContentCatalog.registerLifecycle(new BarrelArmor());
		CombatEquipment.register((player, loadout) -> {
			SET.applyKit(player, loadout);
			syncSpeed(player);
		});
		CombatModifiers.afterDamage((victim, source, taken, blocked) -> {
			if (blocked || taken <= 0f || !(victim instanceof ServerPlayer hit)) return;
			if (!SET.hasActiveFullSet(hit) || DuelRuntime.blocksAbility(hit.getUUID())) return;
			onDamageTaken(hit, taken);
		});
	}

	@Override
	public void register() {
		ServerTickEvents.END_SERVER_TICK.register(BarrelArmor::tick);
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
		STATES.of(player).accumulatedLoss.remove(player.getUUID());
		syncSpeed(player);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			syncSpeed(player);
		}
	}

	private static void syncSpeed(ServerPlayer player) {
		CustomArmorSet.syncAttribute(
				player,
				Attributes.MOVEMENT_SPEED,
				SPEED_MOD,
				SPEED_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
				SET.hasActiveFullSet(player));
	}

	private static void onDamageTaken(ServerPlayer hit, float taken) {
		var state = STATES.of(hit);
		UUID id = hit.getUUID();
		float threshold = thresholdDamage(hit.getMaxHealth());
		float total = state.accumulatedLoss.getOrDefault(id, 0f) + taken;
		if (total < threshold) {
			state.accumulatedLoss.put(id, total);
			return;
		}
		state.accumulatedLoss.put(id, total % threshold);
		triggerPulse(hit);
	}

	private static void triggerPulse(ServerPlayer wearer) {
		if (!(wearer.level() instanceof ServerLevel level)) return;
		CustomArmorSet.broadcastAuraState(wearer, Phase.BARREL_PULSE, 1.0f, 28);
		level.playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(),
				SoundEvents.BARREL_OPEN, SoundSource.PLAYERS, 0.85f, 0.72f);
		level.playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(),
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.45f, 1.25f);
		for (var target : CustomArmorSet.targetsAround(wearer, level, PULSE_RADIUS)) {
			CustomArmorSet.knockAway(wearer.position(), target, KNOCKBACK_HORIZONTAL, KNOCKBACK_VERTICAL);
		}
	}
}
