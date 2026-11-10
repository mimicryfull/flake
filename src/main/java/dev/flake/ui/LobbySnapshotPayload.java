package dev.flake.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Full client view: the viewer's own room (if any) plus the browsable room list.
 * All lists are bounded so a hostile server cannot force unbounded allocations on the client.
 */
public record LobbySnapshotPayload(
		String roomId,
		String title,
		String owner,
		String mode,
		String visibility,
		String arena,
		int teamCount,
		int teamSize,
		int maxPlayers,
		boolean viewerIsOwner,
		boolean viewerIsMember,
		boolean viewerReady,
		List<Player> players,
		List<RoomSummary> rooms,
		List<String> arenas,
		String error,
		String phase,
		String status
) implements CustomPacketPayload {
	public static final int MAX_PLAYERS = 512;
	public static final int MAX_ROOMS = 128;
	public static final int MAX_ARENAS = 64;
	public static final Type<LobbySnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("flake", "lobby_snapshot_v2"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LobbySnapshotPayload> CODEC = StreamCodec.ofMember(
			LobbySnapshotPayload::write, LobbySnapshotPayload::read);

	public LobbySnapshotPayload {
		roomId = clip(roomId, 36);
		title = clip(title, 32);
		owner = clip(owner, 36);
		mode = clip(mode, 16);
		visibility = clip(visibility, 8);
		arena = clip(arena, 24);
		error = clip(error, 256);
		phase = clip(phase, 32);
		status = clip(status, 512);
		players = players == null ? List.of() : List.copyOf(players);
		rooms = rooms == null ? List.of() : List.copyOf(rooms);
		arenas = arenas == null ? List.of() : List.copyOf(arenas);
	}

	public boolean inRoom() { return !roomId.isEmpty(); }

	@Override public Type<LobbySnapshotPayload> type() { return TYPE; }

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(roomId, 36);
		buf.writeUtf(title, 32);
		buf.writeUtf(owner, 36);
		buf.writeUtf(mode, 16);
		buf.writeUtf(visibility, 8);
		buf.writeUtf(arena, 24);
		buf.writeVarInt(teamCount);
		buf.writeVarInt(teamSize);
		buf.writeVarInt(maxPlayers);
		buf.writeBoolean(viewerIsOwner);
		buf.writeBoolean(viewerIsMember);
		buf.writeBoolean(viewerReady);
		buf.writeUtf(error, 256);
		buf.writeUtf(phase, 32);
		buf.writeUtf(status, 512);
		buf.writeVarInt(Math.min(arenas.size(), MAX_ARENAS));
		int arenaWritten = 0;
		for (var arena : arenas) {
			if (arenaWritten++ >= MAX_ARENAS) break;
			buf.writeUtf(arena, 24);
		}
		buf.writeVarInt(Math.min(players.size(), MAX_PLAYERS));
		int written = 0;
		for (var player : players) {
			if (written++ >= MAX_PLAYERS) break;
			buf.writeUUID(player.id);
			buf.writeUtf(player.name, 64);
			buf.writeUtf(player.fighterClass, 32);
			buf.writeVarInt(player.kills);
			buf.writeVarInt(player.deaths);
			buf.writeVarInt(player.ping);
			buf.writeBoolean(player.member);
			buf.writeBoolean(player.ready);
			buf.writeBoolean(player.validBuild);
			buf.writeVarInt(player.team);
			buf.writeBoolean(player.owner);
			buf.writeBoolean(player.invited);
			buf.writeUtf(player.roomTitle, 32);
		}
		buf.writeVarInt(Math.min(rooms.size(), MAX_ROOMS));
		int roomsWritten = 0;
		for (var room : rooms) {
			if (roomsWritten++ >= MAX_ROOMS) break;
			buf.writeUtf(room.id, 36);
			buf.writeUtf(room.title, 32);
			buf.writeUtf(room.ownerName, 64);
			buf.writeUtf(room.mode, 16);
			buf.writeUtf(room.arena, 24);
			buf.writeVarInt(room.players);
			buf.writeVarInt(room.maxPlayers);
			buf.writeUtf(room.state, 32);
			buf.writeBoolean(room.privateRoom);
			buf.writeBoolean(room.invited);
			buf.writeBoolean(room.joinable);
			buf.writeBoolean(room.mine);
		}
	}

	private static LobbySnapshotPayload read(RegistryFriendlyByteBuf buf) {
		String roomId = buf.readUtf(36);
		String title = buf.readUtf(32);
		String owner = buf.readUtf(36);
		String mode = buf.readUtf(16);
		String visibility = buf.readUtf(8);
		String arena = buf.readUtf(24);
		int teamCount = buf.readVarInt();
		int teamSize = buf.readVarInt();
		int maxPlayers = buf.readVarInt();
		boolean viewerIsOwner = buf.readBoolean();
		boolean viewerIsMember = buf.readBoolean();
		boolean viewerReady = buf.readBoolean();
		String error = buf.readUtf(256);
		String phase = buf.readUtf(32);
		String status = buf.readUtf(512);
		int arenaCount = Math.clamp(buf.readVarInt(), 0, MAX_ARENAS);
		var arenas = new ArrayList<String>(arenaCount);
		for (int i = 0; i < arenaCount; i++) arenas.add(buf.readUtf(24));
		int count = Math.clamp(buf.readVarInt(), 0, MAX_PLAYERS);
		var players = new ArrayList<Player>(count);
		for (int i = 0; i < count; i++) {
			players.add(new Player(
					buf.readUUID(),
					buf.readUtf(64),
					buf.readUtf(32),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readBoolean(),
					buf.readBoolean(),
					buf.readBoolean(),
					buf.readVarInt(),
					buf.readBoolean(),
					buf.readBoolean(),
					buf.readUtf(32)
			));
		}
		int roomCount = Math.clamp(buf.readVarInt(), 0, MAX_ROOMS);
		var rooms = new ArrayList<RoomSummary>(roomCount);
		for (int i = 0; i < roomCount; i++) {
			rooms.add(new RoomSummary(
					buf.readUtf(36),
					buf.readUtf(32),
					buf.readUtf(64),
					buf.readUtf(16),
					buf.readUtf(24),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readUtf(32),
					buf.readBoolean(),
					buf.readBoolean(),
					buf.readBoolean(),
					buf.readBoolean()
			));
		}
		return new LobbySnapshotPayload(roomId, title, owner, mode, visibility, arena, teamCount, teamSize, maxPlayers,
				viewerIsOwner, viewerIsMember, viewerReady, players, rooms, arenas, error, phase, status);
	}

	private static String clip(String value, int max) {
		if (value == null) return "";
		return value.length() <= max ? value : value.substring(0, max);
	}

	public record Player(
			UUID id,
			String name,
			String fighterClass,
			int kills,
			int deaths,
			int ping,
			boolean member,
			boolean ready,
			boolean validBuild,
			int team,
			boolean owner,
			boolean invited,
			String roomTitle
	) {
		public Player {
			if (roomTitle == null) roomTitle = "";
			if (roomTitle.length() > 32) roomTitle = roomTitle.substring(0, 32);
		}
	}

	public record RoomSummary(
			String id,
			String title,
			String ownerName,
			String mode,
			String arena,
			int players,
			int maxPlayers,
			String state,
			boolean privateRoom,
			boolean invited,
			boolean joinable,
			boolean mine
	) {}
}