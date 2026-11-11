package dev.flake.features.lobby;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One client intent. The server validates ownership, visibility and match state before acting. */
public record LobbyActionPayload(
		int action,
		String roomId,
		String name,
		String mode,
		String visibility,
		String arena,
		int teamCount,
		int teamSize,
		String target
) implements CustomPacketPayload {
	public static final int QUERY = 0, CREATE = 1, JOIN = 2, LEAVE = 3, READY = 4, NOT_READY = 5, SWITCH_TEAM = 6,
			START = 7, STOP = 8, KICK = 9, INVITE = 10, TRANSFER = 11, SETTINGS = 12, DELETE = 13, UNINVITE = 14;

	public static final Type<LobbyActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("flake", "lobby_action_v2"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LobbyActionPayload> CODEC = StreamCodec.of(
			(buf, value) -> {
				buf.writeVarInt(value.action);
				buf.writeUtf(value.roomId, 36);
				buf.writeUtf(value.name, 32);
				buf.writeUtf(value.mode, 16);
				buf.writeUtf(value.visibility, 8);
				buf.writeUtf(value.arena, 24);
				buf.writeVarInt(value.teamCount);
				buf.writeVarInt(value.teamSize);
				buf.writeUtf(value.target, 36);
			},
			buf -> new LobbyActionPayload(
					buf.readVarInt(),
					buf.readUtf(36),
					buf.readUtf(32),
					buf.readUtf(16),
					buf.readUtf(8),
					buf.readUtf(24),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readUtf(36)
			));

	public LobbyActionPayload {
		if (roomId == null) roomId = "";
		if (name == null) name = "";
		if (mode == null) mode = "";
		if (visibility == null) visibility = "";
		if (arena == null) arena = "";
		if (target == null) target = "";
	}

	public static LobbyActionPayload query() { return simple(QUERY); }
	public static LobbyActionPayload simple(int action) { return new LobbyActionPayload(action, "", "", "", "", "", 0, 0, ""); }
	public static LobbyActionPayload room(int action, String roomId) { return new LobbyActionPayload(action, roomId, "", "", "", "", 0, 0, ""); }
	public static LobbyActionPayload target(int action, String target) { return new LobbyActionPayload(action, "", "", "", "", "", 0, 0, target); }
	public static LobbyActionPayload team(int team) { return new LobbyActionPayload(SWITCH_TEAM, "", "", "", "", "", team, 0, ""); }
	public static LobbyActionPayload settings(String mode, String visibility, String arena, int teams, int size) {
		return new LobbyActionPayload(SETTINGS, "", "", mode, visibility, arena, teams, size, "");
	}

	@Override public Type<LobbyActionPayload> type() { return TYPE; }
}
