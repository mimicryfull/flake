package dev.flake.features.weather;

import dev.flake.Flake;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Synchronizes world-wide custom weather (e.g. falling sakura petals) from server to clients. */
public record SakuraWeatherPayload(String mode, float intensity) implements CustomPacketPayload {
	public static final Type<SakuraWeatherPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "weather_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SakuraWeatherPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SakuraWeatherPayload::mode,
			ByteBufCodecs.FLOAT, SakuraWeatherPayload::intensity,
			SakuraWeatherPayload::new).cast();

	@Override
	public Type<SakuraWeatherPayload> type() {
		return TYPE;
	}
}
