package dev.flake.features.lobby;

import dev.flake.features.arena.ArenaRepository;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.text.FlakeI18n;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-scoped registry of independent lobby rooms. Bound to one {@link MinecraftServer}:
 * switching worlds or servers can never carry rooms, members or match state across.
 */
public final class LobbyManager {
	private static final Map<MinecraftServer, LobbyManager> INSTANCES = new LinkedHashMap<>();
	private static boolean installed;

	private final MinecraftServer server;
	private final ArenaRepository arenas;
	private final Map<UUID, LobbyRoom> rooms = new LinkedHashMap<>();
	private final Map<UUID, UUID> byPlayer = new LinkedHashMap<>();

	private LobbyManager(MinecraftServer server) {
		this.server = server;
		this.arenas = ArenaRepository.forServer(server);
	}

	public static void install() {
		if (installed) return;
		installed = true;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> INSTANCES.put(server, new LobbyManager(server)));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			var manager = INSTANCES.get(server);
			if (manager != null) manager.shutdown();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			INSTANCES.remove(server);
			DuelRuntime.removeAll(server);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			var manager = INSTANCES.get(server);
			if (manager != null && server.getTickCount() % 5 == 0) manager.tick();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			var manager = INSTANCES.get(server);
			if (manager != null) manager.disconnect(handler.player);
		});
	}

	public static LobbyManager of(MinecraftServer server) {
		if (server == null) return null;
		// Self-heal if a server started before the lifecycle hook ran (dedicated reload, tests).
		return INSTANCES.computeIfAbsent(server, LobbyManager::new);
	}

	public MinecraftServer server() { return server; }
	public ArenaRepository arenas() { return arenas; }
	public List<String> arenaNames() {
		var names = new ArrayList<>(arenas.names());
		if (names.isEmpty()) names.add(ArenaRepository.DEFAULT);
		return names;
	}

	public LobbyRoom roomOf(UUID player) {
		UUID roomId = byPlayer.get(player);
		return roomId == null ? null : rooms.get(roomId);
	}

	public LobbyRoom room(UUID roomId) { return rooms.get(roomId); }

	public List<LobbyRoom> rooms() { return List.copyOf(rooms.values()); }

	/** Rooms a viewer may see: public rooms, plus private rooms they own, joined or were invited to. */
	public List<LobbyRoom> visibleRooms(UUID viewer) {
		var list = new ArrayList<LobbyRoom>();
		for (var room : rooms.values()) if (room.visibleTo(viewer)) list.add(room);
		list.sort(Comparator.comparing(LobbyRoom::createdAt));
		return list;
	}

	// ---- Lifecycle --------------------------------------------------------------------------------

	public String create(ServerPlayer player, String name, String mode, String visibility, String arena, int teams, int size) {
		var id = player.getUUID();
		if (roomOf(id) != null) return FlakeI18n.tr("flake.lobby.err.already_in_room");
		var title = LobbyRoom.sanitizeName(name);
		if (title.isEmpty()) return FlakeI18n.tr("flake.lobby.err.name_length");
		if (rooms.size() >= 128) return FlakeI18n.tr("flake.lobby.err.too_many");
		var room = new LobbyRoom(this, UUID.randomUUID(), title, id, server.getTickCount());
		String error = room.join(id);
		if (!error.isEmpty()) return error;
		String settingsError = room.updateSettings(id, mode, visibility, arena, teams, size);
		if (!settingsError.isEmpty()) {
			room.close("Створення скасовано.");
			return settingsError;
		}
		rooms.put(room.id(), room);
		byPlayer.put(id, room.id());
		return "";
	}

	public String join(ServerPlayer player, UUID roomId) {
		var id = player.getUUID();
		if (roomOf(id) != null) return FlakeI18n.tr("flake.lobby.err.already_in_room");
		var room = rooms.get(roomId);
		if (room == null) return FlakeI18n.tr("flake.lobby.err.not_found");
		if (Dev.busy(room)) return FlakeI18n.tr("flake.lobby.err.wait_duel");
		String error = room.join(id);
		if (!error.isEmpty()) return error;
		byPlayer.put(id, room.id());
		return "";
	}

	/** Leaves the player's current room; closing a room that became empty. */
	public String leave(ServerPlayer player) {
		var id = player.getUUID();
		var room = roomOf(id);
		if (room == null) return FlakeI18n.tr("flake.lobby.err.not_in");
		String error = room.leave(id);
		if (!error.isEmpty()) return error;
		byPlayer.remove(id);
		dropIfEmpty(room);
		return "";
	}

	public void disconnect(ServerPlayer player) {
		var room = roomOf(player.getUUID());
		if (room == null) return;
		room.forceRemove(player.getUUID());
		byPlayer.remove(player.getUUID());
		dropIfEmpty(room);
	}

	/** Owner deletes their own room; the match, if any, is stopped and everyone is unseated. */
	public String delete(ServerPlayer player) {
		var room = roomOf(player.getUUID());
		if (room == null) return FlakeI18n.tr("flake.lobby.err.not_in");
		if (!room.isOwner(player.getUUID())) return FlakeI18n.tr("flake.lobby.err.not_owner");
		for (var member : new ArrayList<>(room.lobby().players().keySet())) byPlayer.remove(member);
		rooms.remove(room.id());
		room.close(FlakeI18n.tr("flake.lobby.notice.deleted"));
		return "";
	}

	/** Server stop: cancel every match and drop every room. */
	public void shutdown() {
		for (var room : List.copyOf(rooms.values())) room.close("Сервер зупиняється; речі повертаються.");
		rooms.clear();
		byPlayer.clear();
	}

	private void tick() {
		for (var room : List.copyOf(rooms.values())) {
			room.processPendingLeaves();
			if (room.empty()) {
				rooms.remove(room.id());
				room.close("Порожня кімната закрита.");
				continue;
			}
			if (!room.lobby().locked()) room.ensureMatch();
		}
		byPlayer.entrySet().removeIf(entry -> {
			var room = rooms.get(entry.getValue());
			return room == null || !room.isMember(entry.getKey());
		});
	}

	private void dropIfEmpty(LobbyRoom room) {
		if (!room.empty()) return;
		rooms.remove(room.id());
		room.close("Порожня кімната закрита.");
		byPlayer.values().removeIf(id -> id.equals(room.id()));
	}

	/** Convenience for non-UI call sites (commands). */
	public static LobbyManager ofPlayer(ServerPlayer player) {
		return player == null ? null : of(player.level().getServer());
	}

	private static final class Dev {
		static boolean busy(LobbyRoom room) {
			var match = room.match();
			return match != null && match.match() != null;
		}
	}
}
