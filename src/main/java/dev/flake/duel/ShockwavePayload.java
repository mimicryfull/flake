package dev.flake.duel;

import dev.flake.ExampleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record ShockwavePayload(Vec3 origin, int sourceId, Phase phase, float yaw, int targetId, Vec3 aim) implements CustomPacketPayload {
	public static final Type<ShockwavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ExampleMod.MOD_ID, "shockwave"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShockwavePayload> CODEC = StreamCodec.composite(
			Vec3.STREAM_CODEC, ShockwavePayload::origin,
			ByteBufCodecs.VAR_INT, ShockwavePayload::sourceId,
			ByteBufCodecs.VAR_INT.map(id -> Phase.values()[id], Phase::ordinal), ShockwavePayload::phase,
			ByteBufCodecs.FLOAT, ShockwavePayload::yaw,
			ByteBufCodecs.VAR_INT, ShockwavePayload::targetId,
			Vec3.STREAM_CODEC, ShockwavePayload::aim,
			ShockwavePayload::new).cast();

	public ShockwavePayload(Vec3 origin, int sourceId, Phase phase, float yaw) {
		this(origin, sourceId, phase, yaw, -1, Vec3.ZERO);
	}

	public ShockwavePayload(Vec3 origin, int sourceId, Phase phase) {
		this(origin, sourceId, phase, 0.0f);
	}

	@Override public Type<ShockwavePayload> type() { return TYPE; }

	public enum Phase {
		CHARGE, BURST, CANCEL, CLEAR, QUAKE, IMPACT, BOLT, MASK_ON, MASK_OFF, CRYSTAL_ON, CRYSTAL_OFF, CRIT,
		SHIELD_RAM, SHIELD_RAM_SHIELD, BULWARK_ON, BULWARK_OFF, BULWARK_HIT,
		BLOOD_LUNGE, BLOODLUST_ON, BLOODLUST_OFF, BLOOD_SIPHON, HAMMER_STUN
	}
}
