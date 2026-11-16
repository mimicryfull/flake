package dev.flake.features.passives;

import dev.flake.Flake;
import dev.flake.combat.Stuns;
import dev.flake.content.ItemRegistration;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.Loadout.PassiveItem;
import dev.flake.features.loadout.LoadoutService;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import dev.flake.runtime.ServerState;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative Cloud in a Bottle passive runtime:
 * validates mid-air double-jump eligibility, resets fall distance, applies upward impulse, and broadcasts cloud VFX.
 */
public final class CloudBottleRuntime {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "cloud_bottle");
	public static final double DOUBLE_JUMP_Y_VEL = 0.48;

	public static Item ITEM;

	private static final class State {
		final Set<UUID> airJumpUsed = new HashSet<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static boolean registered;

	private CloudBottleRuntime() {}

	public static void registerItems() {
		ITEM = ItemRegistration.register(ID, key -> new Item(new Item.Properties().setId(key)));
	}

	public static ItemStack stack() {
		return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM);
	}

	public static void register() {
		if (registered) return;
		registered = true;
		PayloadTypeRegistry.serverboundPlay().register(CloudJumpPayload.TYPE, CloudJumpPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CloudJumpPayload.TYPE, (payload, context) ->
				context.server().execute(() -> handleJumpRequest(context.player())));

		ServerTickEvents.END_SERVER_TICK.register(CloudBottleRuntime::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				STATES.of(server).airJumpUsed.remove(handler.player.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) reset(player);
		});
	}

	public static void reset(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server != null) STATES.of(server).airJumpUsed.remove(player.getUUID());
	}

	public static boolean hasCloudBottle(ServerPlayer player) {
		UUID id = player.getUUID();
		if (DuelRuntime.fighting(id)) {
			return DuelRuntime.passiveOf(id) == PassiveItem.CLOUD_BOTTLE;
		}
		if (DuelRuntime.protectedInventory(id)) return false;
		MinecraftServer server = player.level().getServer();
		if (server != null && LoadoutService.of(server).get(id).passiveItem() == PassiveItem.CLOUD_BOTTLE) {
			return true;
		}
		if (ITEM == null) return false;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(ITEM)) return true;
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		State state = STATES.of(server);
		if (state.airJumpUsed.isEmpty()) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
				state.airJumpUsed.remove(player.getUUID());
			}
		}
	}

	private static void handleJumpRequest(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying) return;
		if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) return;
		if (DuelRuntime.blocksAbility(player.getUUID())) return;
		MinecraftServer server = player.level().getServer();
		if (server == null || Stuns.remaining(player, server.getTickCount()) > 0) return;
		if (!hasCloudBottle(player)) return;

		State state = STATES.of(server);
		if (!state.airJumpUsed.add(player.getUUID())) return;

		player.resetFallDistance();
		Vec3 vel = player.getDeltaMovement();
		player.setDeltaMovement(vel.x, DOUBLE_JUMP_Y_VEL, vel.z);

		Vec3 feet = player.position();
		CombatVisuals.broadcast(player,
				new CombatVfxPayload(feet, player.getId(), CombatVfxPayload.Phase.CLOUD_JUMP),
				feet, 14);
		player.level().playSound(null, feet.x, feet.y, feet.z,
				SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.55f, 1.32f);
	}
}
