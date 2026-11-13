package dev.flake.bootstrap;

import dev.flake.features.hud.HudStatePayload;
import dev.flake.features.lobby.LobbyActionPayload;
import dev.flake.features.lobby.LobbySnapshotPayload;
import dev.flake.features.lobby.LobbyManager;
import dev.flake.features.loadout.BuildApplyPayload;
import dev.flake.features.loadout.BuildResultPayload;

import dev.flake.features.hud.HudProjection;
import dev.flake.features.lobby.LobbyActions;
import dev.flake.features.lobby.LobbyProjection;
import dev.flake.features.loadout.BuildApplication;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Packet registration, server-thread scheduling and delivery of feature-owned read models. */
public final class FlakeNetworking implements ModInitializer {
	@Override public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(LobbyActionPayload.TYPE, LobbyActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BuildApplyPayload.TYPE, BuildApplyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BuildResultPayload.TYPE, BuildResultPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LobbySnapshotPayload.TYPE, LobbySnapshotPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HudStatePayload.TYPE, HudStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(dev.flake.combat.CombatFeedbackPayload.TYPE, dev.flake.combat.CombatFeedbackPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LobbyActionPayload.TYPE, (payload, context) ->
				context.server().execute(() -> act(context.server(), context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(BuildApplyPayload.TYPE, (payload, context) ->
				context.server().execute(() -> applyBuild(context.server(), context.player(), payload)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 2 == 0) broadcastHud(server);
			if (server.getTickCount() % 10 == 0) broadcast(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LobbyActions.disconnect(server, handler.player.getUUID());
			HudProjection.disconnect(server, handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LobbyActions.clear(server);
			HudProjection.clear(server);
		});
	}

	private static void act(MinecraftServer server, ServerPlayer player, LobbyActionPayload action) {
		var result = LobbyActions.execute(server, player, action);
		if (result == null) return;
		if (result.broadcast()) {
			// Action responses historically deliver lobby snapshots before HUD updates.
			broadcast(server);
			broadcastHud(server);
		}
		if (!result.error().isEmpty()) send(player, LobbyProjection.snapshot(server, player, result.error()));
	}

	private static void applyBuild(MinecraftServer server, ServerPlayer player, BuildApplyPayload payload) {
		var reply = BuildApplication.apply(player, payload);
		if (ServerPlayNetworking.canSend(player, BuildResultPayload.TYPE)) ServerPlayNetworking.send(player, reply.payload());
		if (reply.synchronize()) sync(server);
	}

	public static void sync(MinecraftServer server) {
		if (server == null || LobbyManager.of(server) == null) return;
		broadcastHud(server);
		broadcast(server);
	}

	private static void broadcast(MinecraftServer server) {
		if (LobbyManager.of(server) == null) return;
		for (var player : server.getPlayerList().getPlayers()) send(player, LobbyProjection.snapshot(server, player, ""));
	}

	private static void broadcastHud(MinecraftServer server) {
		for (var player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, HudStatePayload.TYPE)) ServerPlayNetworking.send(player, HudProjection.project(server, player));
		}
	}

	private static void send(ServerPlayer player, LobbySnapshotPayload payload) {
		if (ServerPlayNetworking.canSend(player, LobbySnapshotPayload.TYPE)) ServerPlayNetworking.send(player, payload);
	}
}
