package dev.flake.features.lobby;

import dev.flake.features.duel.MatchRuntime;
import dev.flake.features.loadout.LoadoutService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/** Visibility-aware lobby read model. No network sends or application mutations. */
public final class LobbyProjection {
	private static final int ROSTER_CAP = 256;
	private LobbyProjection() {}

	public static LobbySnapshotPayload snapshot(MinecraftServer server, ServerPlayer viewer, String error) {
		var manager = LobbyManager.of(server);
		var viewerId = viewer.getUUID();
		var room = manager == null ? null : manager.roomOf(viewerId);
		var members = room == null ? java.util.Map.<UUID, Boolean>of() : room.lobby().players();
		// Own-room members precede other online players in the bounded tab/browser projection.
		var players = new ArrayList<LobbySnapshotPayload.Player>(Math.min(ROSTER_CAP, server.getPlayerList().getPlayerCount()));
		for (var memberId : members.keySet()) {
			var candidate = server.getPlayerList().getPlayer(memberId);
			players.add(playerRow(server, memberId, candidate, true, Boolean.TRUE.equals(members.get(memberId)), room, manager));
		}
		var others = server.getPlayerList().getPlayers().stream()
				.filter(candidate -> !members.containsKey(candidate.getUUID()))
				.sorted(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER))
				.limit(Math.max(0, ROSTER_CAP - players.size())).toList();
		for (var candidate : others) players.add(playerRow(server, candidate.getUUID(), candidate, false, false, room, manager));
		var summaries = new ArrayList<LobbySnapshotPayload.RoomSummary>();
		if (manager != null) {
			for (var candidate : manager.visibleRooms(viewerId)) {
				var ownerPlayer = server.getPlayerList().getPlayer(candidate.owner());
				summaries.add(new LobbySnapshotPayload.RoomSummary(candidate.id().toString(), candidate.title(),
						ownerPlayer == null ? "" : ownerPlayer.getName().getString(), candidate.mode(), candidate.arena(),
						candidate.size(), candidate.maxPlayers(), candidate.state(), candidate.visibility() == LobbyRoom.Visibility.PRIVATE,
						candidate.isInvited(viewerId), candidate.joinableBy(viewerId), candidate.isMember(viewerId)));
			}
		}
		MatchRuntime runtime = room == null ? null : room.match();
		return new LobbySnapshotPayload(
				room == null ? "" : room.id().toString(),
				room == null ? "" : room.title(),
				room == null || room.owner() == null ? "" : room.owner().toString(),
				room == null ? "DUEL" : room.mode(),
				room == null ? "PUBLIC" : room.visibility().name(),
				room == null ? "" : room.arena(),
				room == null ? 2 : room.teamCount(),
				room == null ? 1 : room.teamSize(),
				room == null ? 0 : room.maxPlayers(),
				room != null && room.isOwner(viewerId),
				room != null && room.isMember(viewerId),
				Boolean.TRUE.equals(members.get(viewerId)), players, summaries,
				manager == null ? List.of() : manager.arenaNames(), error,
				runtime == null ? "PREPARING" : runtime.phase(), runtime == null ? "" : runtime.status());
	}

	private static LobbySnapshotPayload.Player playerRow(MinecraftServer server, UUID id, ServerPlayer online,
			boolean member, boolean ready, LobbyRoom room, LobbyManager manager) {
		var build = LoadoutService.of(server).get(id);
		var playerRoom = manager == null ? null : manager.roomOf(id);
		return new LobbySnapshotPayload.Player(id,
				online == null ? id.toString().substring(0, 8) : online.getName().getString(), build.fighterClass().name(),
				online == null ? 0 : online.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)),
				online == null ? 0 : online.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)),
				online == null ? 0 : online.connection.latency(), member, ready, build.errors().isEmpty(),
				member && room != null ? room.lobby().teamOf(id) : -1,
				member && room != null && room.isOwner(id), member && room != null && room.isInvited(id),
				playerRoom == null ? "" : playerRoom.title());
	}
}
