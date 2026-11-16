package dev.flake.features.weather;

import dev.flake.Flake;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Synchronizes region-scoped sakura weather zones from server to clients. */
public record SakuraWeatherPayload(List<SakuraWeather.Region> regions) implements CustomPacketPayload {
	public static final Type<SakuraWeatherPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "weather_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SakuraWeatherPayload> CODEC =
			StreamCodec.ofMember(SakuraWeatherPayload::write, SakuraWeatherPayload::read);

	public SakuraWeatherPayload {
		regions = regions == null ? List.of() : List.copyOf(regions);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		int count = Math.min(256, regions.size());
		buf.writeVarInt(count);
		for (int i = 0; i < count; i++) {
			SakuraWeather.Region r = regions.get(i);
			buf.writeUtf(r.name(), 64);
			buf.writeUtf(r.dimension(), 128);
			buf.writeBoolean(r.shape() == SakuraWeather.Shape.BOX);
			buf.writeDouble(r.x1());
			buf.writeDouble(r.y1());
			buf.writeDouble(r.z1());
			buf.writeDouble(r.x2());
			buf.writeDouble(r.y2());
			buf.writeDouble(r.z2());
			buf.writeFloat(r.radius());
			buf.writeFloat(r.intensity());
		}
	}

	private static SakuraWeatherPayload read(RegistryFriendlyByteBuf buf) {
		int count = Math.max(0, Math.min(256, buf.readVarInt()));
		List<SakuraWeather.Region> list = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String name = buf.readUtf(64);
			String dim = buf.readUtf(128);
			SakuraWeather.Shape shape = buf.readBoolean() ? SakuraWeather.Shape.BOX : SakuraWeather.Shape.SPHERE;
			double x1 = buf.readDouble();
			double y1 = buf.readDouble();
			double z1 = buf.readDouble();
			double x2 = buf.readDouble();
			double y2 = buf.readDouble();
			double z2 = buf.readDouble();
			float radius = buf.readFloat();
			float intensity = buf.readFloat();
			list.add(new SakuraWeather.Region(name, dim, shape, x1, y1, z1, x2, y2, z2, radius, intensity));
		}
		return new SakuraWeatherPayload(list);
	}

	@Override
	public Type<SakuraWeatherPayload> type() {
		return TYPE;
	}
}
