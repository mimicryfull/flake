package dev.flake.features.lobby;

import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.LoadoutService;
import dev.flake.text.FlakeI18n;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Lobby action application and its per-server input throttle, independent of packet delivery. */
public final class LobbyActions {
	private static final Map<MinecraftServer, Map<UUID, Integer>> LAST_ACTION = new HashMap<>();
	private LobbyActions() {}

	public record Result(boolean broadcast, String error) {}

	/** A null result means the lobby subsystem is not available. */
	public static Result execute(MinecraftServer server, ServerPlayer player, LobbyActionPayload action) {
		var manager = LobbyManager.of(server);
		if (manager == null) return null;
		var lastAction = LAST_ACTION.computeIfAbsent(server, key -> new HashMap<>());
		int now = server.getTickCount();
		Integer last = lastAction.get(player.getUUID());
		if (last != null && now - last < 2 && action.action() != LobbyActionPayload.QUERY) {
			return new Result(false, FlakeI18n.tr("flake.lobby.err.rate_limit"));
		}
		lastAction.put(player.getUUID(), now);
		String roomId = action.roomId() == null || action.roomId().isBlank() ? "" : action.roomId();
		var room = manager.roomOf(player.getUUID());
		String error = switch (action.action()) {
			case LobbyActionPayload.QUERY -> "";
			case LobbyActionPayload.CREATE -> room == null
					? manager.create(player, action.name(), action.mode(), action.visibility(), action.arena(), action.teamCount(), action.teamSize())
					: FlakeI18n.tr("flake.lobby.err.already_in_room");
			case LobbyActionPayload.JOIN -> room == null ? joinById(manager, player, roomId) : FlakeI18n.tr("flake.lobby.err.already_in_room");
			case LobbyActionPayload.LEAVE -> {
				DuelRuntime.stopFor(player);
				yield manager.leave(player);
			}
			case LobbyActionPayload.READY, LobbyActionPayload.NOT_READY -> room == null
					? FlakeI18n.tr("flake.lobby.err.join_first")
					: room.ready(player.getUUID(), action.action() == LobbyActionPayload.READY,
							LoadoutService.of(server).get(player.getUUID()).errors().isEmpty());
			case LobbyActionPayload.SWITCH_TEAM -> room == null ? FlakeI18n.tr("flake.lobby.err.join_first") : room.switchTeam(player.getUUID(), action.teamCount());
			case LobbyActionPayload.START -> room == null ? FlakeI18n.tr("flake.lobby.err.join_first") : room.start(player.getUUID());
			case LobbyActionPayload.STOP -> room == null ? FlakeI18n.tr("flake.lobby.err.no_match") : room.stopMatch(player.getUUID());
			case LobbyActionPayload.SETTINGS -> room == null ? FlakeI18n.tr("flake.lobby.err.join_first")
					: room.updateSettings(player.getUUID(), action.mode(), action.visibility(), action.arena(), action.teamCount(), action.teamSize());
			case LobbyActionPayload.KICK -> room == null ? FlakeI18n.tr("flake.lobby.err.not_in") : withTarget(action.target(), target -> room.kick(player.getUUID(), target));
			case LobbyActionPayload.INVITE -> room == null ? FlakeI18n.tr("flake.lobby.err.not_in") : withTarget(action.target(), target -> room.invite(player.getUUID(), target));
			case LobbyActionPayload.UNINVITE -> room == null ? FlakeI18n.tr("flake.lobby.err.not_in") : withTarget(action.target(), target -> room.revokeInvite(player.getUUID(), target));
			case LobbyActionPayload.TRANSFER -> room == null ? FlakeI18n.tr("flake.lobby.err.not_in") : withTarget(action.target(), target -> room.transferOwnership(player.getUUID(), target));
			case LobbyActionPayload.DELETE -> manager.delete(player);
			default -> FlakeI18n.tr("flake.lobby.err.unknown_action");
		};
		return new Result(true, error);
	}

	public static void disconnect(MinecraftServer server, UUID id) {
		var actions = LAST_ACTION.get(server);
		if (actions != null) actions.remove(id);
	}

	public static void clear(MinecraftServer server) { LAST_ACTION.remove(server); }

	private static String joinById(LobbyManager manager, ServerPlayer player, String roomId) {
		if (roomId.isBlank()) {
			var first = manager.visibleRooms(player.getUUID()).stream().filter(r -> r.joinableBy(player.getUUID())).findFirst().orElse(null);
			if (first == null) return FlakeI18n.tr("flake.lobby.err.no_open_rooms");
			return manager.join(player, first.id());
		}
		try {
			return manager.join(player, UUID.fromString(roomId));
		} catch (IllegalArgumentException invalid) {
			return FlakeI18n.tr("flake.lobby.err.not_found");
		}
	}

	private static String withTarget(String raw, java.util.function.Function<UUID, String> action) {
		try {
			return action.apply(UUID.fromString(raw));
		} catch (IllegalArgumentException | NullPointerException invalid) {
			return FlakeI18n.tr("flake.lobby.err.not_found");
		}
	}
}
