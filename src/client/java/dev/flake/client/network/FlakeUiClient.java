package dev.flake.client.network;

import dev.flake.features.lobby.client.FlakeTabOverlay;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flake.features.loadout.Loadout;
import dev.flake.features.loadout.BuildApplyPayload;
import dev.flake.features.loadout.BuildResultPayload;
import dev.flake.features.hud.HudStatePayload;
import dev.flake.features.lobby.LobbyActionPayload;
import dev.flake.features.lobby.LobbySnapshotPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Client cache for room snapshots, HUD state and build results. All writes happen on the client thread. */
public final class FlakeUiClient implements ClientModInitializer {
	private static final long FRESH_NANOS = 5_000_000_000L;
	private static LobbySnapshotPayload snapshot;
	private static long received;
	private static HudStatePayload hud;
	private static long hudReceived;
	private static long pending;
	private static String error = "";
	private static long errorUntil;
	private static BuildResultPayload buildResult;
	private static long buildResultAt;
	private static String localRoomId = "";
	public static KeyMapping nextPageKey;
	public static KeyMapping previousPageKey;

	@Override public void onInitializeClient() {
		var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("flake", "lobby"));
		nextPageKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.flake.tab_next", InputConstants.KEY_PAGEDOWN, category));
		previousPageKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.flake.tab_previous", InputConstants.KEY_PAGEUP, category));
		ClientPlayNetworking.registerGlobalReceiver(LobbySnapshotPayload.TYPE, (payload, context) -> context.client().execute(() -> {
			var previousRoom = snapshot == null ? "" : snapshot.roomId();
			snapshot = payload;
			received = System.nanoTime();
			pending = 0;
			if (!payload.roomId().isEmpty()) localRoomId = payload.roomId();
			else if (!previousRoom.isEmpty() && previousRoom.equals(localRoomId) && payload.roomId().isEmpty()) localRoomId = "";
			if (!payload.error().isEmpty()) { error = payload.error(); errorUntil = received + FRESH_NANOS; }
		}));
		ClientPlayNetworking.registerGlobalReceiver(HudStatePayload.TYPE, (payload, context) -> context.client().execute(() -> {
			hud = payload;
			hudReceived = System.nanoTime();
		}));
		ClientPlayNetworking.registerGlobalReceiver(BuildResultPayload.TYPE, (payload, context) -> context.client().execute(() -> {
			buildResult = payload;
			buildResultAt = System.nanoTime();
			if (payload.status() != BuildResultPayload.Status.OK && !payload.message().isEmpty()) {
				error = payload.message();
				errorUntil = System.nanoTime() + FRESH_NANOS;
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			snapshot = null; received = 0; hud = null; hudReceived = 0; pending = 0; error = ""; errorUntil = 0;
			buildResult = null; buildResultAt = 0; localRoomId = ""; FlakeTabOverlay.reset();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (nextPageKey.consumeClick()) if (client.options.keyPlayerList.isDown()) FlakeTabOverlay.page(1);
			while (previousPageKey.consumeClick()) if (client.options.keyPlayerList.isDown()) FlakeTabOverlay.page(-1);
		});
	}

	public static LobbySnapshotPayload snapshot() { return snapshot; }
	public static HudStatePayload hud() { return hud; }
	public static BuildResultPayload buildResult() { return buildResult; }
	public static boolean buildResultFresh() { return buildResult != null && System.nanoTime() - buildResultAt < FRESH_NANOS; }
	public static boolean hudFresh() { return hud != null && System.nanoTime() - hudReceived < FRESH_NANOS; }
	public static float hudElapsedSeconds() {
		return hud == null ? 0f : Math.min(0.25f, Math.max(0f, (System.nanoTime() - hudReceived) / 1_000_000_000.0f));
	}
	public static boolean supported() { return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(LobbyActionPayload.TYPE); }
	public static boolean fresh() { return snapshot != null && System.nanoTime() - received < FRESH_NANOS; }
	public static boolean pending() { return pending != 0 && System.nanoTime() - pending < 2_000_000_000L; }
	public static String error() { return System.nanoTime() < errorUntil ? error : ""; }
	public static LobbySnapshotPayload.Player self() {
		var player = Minecraft.getInstance().player;
		return snapshot == null || player == null ? null : snapshot.players().stream().filter(p -> p.id().equals(player.getUUID())).findFirst().orElse(null);
	}

	// ---- Actions ------------------------------------------------------------------------------------

	private static boolean send(LobbyActionPayload payload) {
		if (!supported() || pending()) return false;
		error = ""; errorUntil = 0; pending = System.nanoTime();
		ClientPlayNetworking.send(payload);
		return true;
	}

	/** Legacy helper kept for existing call sites; the name is only used by QUERY. */
	public static void action(int action, String name) {
		send(action == LobbyActionPayload.CREATE ? new LobbyActionPayload(action, "", name, "DUEL", "PUBLIC", "", 2, 1, "")
				: LobbyActionPayload.simple(action));
	}

	public static void query() { send(LobbyActionPayload.query()); }
	public static void createLobby(String name, String mode, String visibility, String arena, int teamCount, int teamSize) {
		send(new LobbyActionPayload(LobbyActionPayload.CREATE, "", name, mode, visibility, arena, teamCount, teamSize, ""));
	}
	public static void join(String roomId) { send(LobbyActionPayload.room(LobbyActionPayload.JOIN, roomId)); }
	public static void leave() { send(LobbyActionPayload.simple(LobbyActionPayload.LEAVE)); }
	public static void ready(boolean ready) { send(LobbyActionPayload.simple(ready ? LobbyActionPayload.READY : LobbyActionPayload.NOT_READY)); }
	public static void switchTeam(int teamIndex) { send(LobbyActionPayload.team(teamIndex)); }
	public static void startMatch() { send(LobbyActionPayload.simple(LobbyActionPayload.START)); }
	public static void stopMatch() { send(LobbyActionPayload.simple(LobbyActionPayload.STOP)); }
	public static void kick(String targetId) { send(LobbyActionPayload.target(LobbyActionPayload.KICK, targetId)); }
	public static void invite(String targetId) { send(LobbyActionPayload.target(LobbyActionPayload.INVITE, targetId)); }
	public static void uninvite(String targetId) { send(LobbyActionPayload.target(LobbyActionPayload.UNINVITE, targetId)); }
	public static void transfer(String targetId) { send(LobbyActionPayload.target(LobbyActionPayload.TRANSFER, targetId)); }
	public static void deleteRoom() { send(LobbyActionPayload.simple(LobbyActionPayload.DELETE)); }
	/** One atomic build write; the revision in the HUD is what the server compares against. */
	public static boolean applyBuild(Loadout build) {
		if (build == null || !supported() || !ClientPlayNetworking.canSend(BuildApplyPayload.TYPE)) return false;
		var state = hud;
		long revision = state == null ? -1L : state.buildRevision();
		ClientPlayNetworking.send(BuildApplyPayload.of(revision, build));
		return true;
	}

	public static void settings(String mode, String visibility, String arena, int teams, int size) {
		send(LobbyActionPayload.settings(mode, visibility, arena, teams, size));
	}
}
