package dev.flake.features.loadout;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server answer to {@link BuildApplyPayload}; sent only when the write did not succeed. */
public record BuildResultPayload(Status status, long revision, String message) implements CustomPacketPayload {
	public enum Status { OK, INVALID, STALE, SAVE_FAILED, LOCKED }

	public static final Type<BuildResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("flake", "build_result"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildResultPayload> CODEC = StreamCodec.ofMember(
			BuildResultPayload::write, BuildResultPayload::read);

	public BuildResultPayload {
		if (message == null) message = "";
		if (message.length() > 256) message = message.substring(0, 256);
	}

	@Override public Type<BuildResultPayload> type() { return TYPE; }

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(status.ordinal());
		buf.writeVarLong(revision);
		buf.writeUtf(message, 256);
	}

	private static BuildResultPayload read(RegistryFriendlyByteBuf buf) {
		int ordinal = Math.clamp(buf.readVarInt(), 0, Status.values().length - 1);
		return new BuildResultPayload(Status.values()[ordinal], buf.readVarLong(), buf.readUtf(256));
	}
}
