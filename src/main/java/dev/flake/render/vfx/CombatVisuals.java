package dev.flake.render.vfx;

import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Shared delivery, preferences and post-effect leases; features own message contents and timing. */
public final class CombatVisuals {
	private static final int BROADCAST_RADIUS = 48;
	private static final Identifier EFFECT = Identifier.fromNamespaceAndPath("flake", "shockwave");
	private static final class State {
		final HashMap<UUID, Integer> active = new HashMap<>();
		final HashSet<UUID> disabled = new HashSet<>();
	}
	private static final ServerState<State> STATES = new ServerState<>(State::new);

	private CombatVisuals() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(CombatVfxPayload.TYPE, CombatVfxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(CombatVisuals::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			removeEffects(handler.player);
			var state = STATES.of(server);
			state.active.remove(handler.player.getUUID());
			state.disabled.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (var player : server.getPlayerList().getPlayers()) removeEffects(player);
			STATES.remove(server);
		});
	}

	/** A non-positive lease delivers a state change without extending the post pass. */
	public static void broadcast(ServerPlayer anchor, CombatVfxPayload payload, Vec3 origin, int duration) {
		var server = anchor.level().getServer();
		if (server == null) return;
		for (var player : server.getPlayerList().getPlayers()) {
			if (player.level() != anchor.level() || player.distanceToSqr(origin) > BROADCAST_RADIUS * BROADCAST_RADIUS) continue;
			send(player, payload, duration);
		}
	}

	/** Retire state even for observers that have left the original activation radius. */
	public static void sendToLevel(ServerPlayer anchor, CombatVfxPayload payload) {
		for (var player : anchor.level().getServer().getPlayerList().getPlayers()) {
			if (player.level() == anchor.level()) send(player, payload, 0);
		}
	}

	public static void send(ServerPlayer player, CombatVfxPayload payload, int duration) {
		if (!isEnabled(player) || !ServerPlayNetworking.canSend(player, CombatVfxPayload.TYPE)) return;
		ServerPlayNetworking.send(player, payload);
		if (duration > 0) {
			player.addPostEffect(EFFECT);
			STATES.of(player).active.merge(player.getUUID(), player.level().getServer().getTickCount() + duration, Math::max);
		}
	}

	public static void cancel(ServerPlayer source) {
		cancel(source, source.getId());
	}

	/** Cancellation deliberately ignores dimension, distance and effect preferences. */
	public static void cancel(ServerPlayer source, int sourceId) {
		var payload = new CombatVfxPayload(source.position(), sourceId, CombatVfxPayload.Phase.CANCEL);
		for (var player : source.level().getServer().getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, CombatVfxPayload.TYPE)) ServerPlayNetworking.send(player, payload);
		}
	}

	public static boolean isEnabled(ServerPlayer player) {
		return !STATES.of(player).disabled.contains(player.getUUID());
	}

	/** Compatibility for UUID-only callers; new callers must supply the server or player. */
	public static boolean isEnabled(UUID playerId) {
		return STATES.values().stream().noneMatch(state -> state.disabled.contains(playerId));
	}

	public static boolean isEnabled(MinecraftServer server, UUID playerId) {
		return !STATES.of(server).disabled.contains(playerId);
	}

	public static boolean toggle(ServerPlayer player) {
		var state = STATES.of(player);
		if (state.disabled.remove(player.getUUID())) return true;
		state.disabled.add(player.getUUID());
		if (ServerPlayNetworking.canSend(player, CombatVfxPayload.TYPE)) {
			ServerPlayNetworking.send(player, new CombatVfxPayload(player.position(), player.getId(), CombatVfxPayload.Phase.CLEAR));
		}
		if (state.active.remove(player.getUUID()) != null) removeEffects(player);
		return false;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		STATES.of(server).active.entrySet().removeIf(entry -> {
			var player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) return true;
			if (now >= entry.getValue()) {
				removeEffects(player);
				return true;
			}
			return false;
		});
	}

	private static void removeEffects(ServerPlayer player) {
		player.removePostEffect(EFFECT);
	}
}
