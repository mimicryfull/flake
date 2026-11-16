package dev.flake.features.passives;

import dev.flake.Flake;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server request to perform the Cloud in a Bottle mid-air double jump. */
public record CloudJumpPayload() implements CustomPacketPayload {
	public static final CloudJumpPayload INSTANCE = new CloudJumpPayload();
	public static final Type<CloudJumpPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "cloud_jump"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CloudJumpPayload> CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<CloudJumpPayload> type() {
		return TYPE;
	}
}
