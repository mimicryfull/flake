package dev.flake.render.vfx;

import dev.flake.Flake;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Shared VFX wire envelope. Features define phase-specific field meanings: yaw is an angle except
 * for BLOOD_SIPHON (actual healed HP); aim can be a contact point, direction or receiving position.
 */
public record CombatVfxPayload(Vec3 origin, int sourceId, Phase phase, float yaw, int targetId, Vec3 aim) implements CustomPacketPayload {
	public static final Type<CombatVfxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "shockwave"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CombatVfxPayload> CODEC = StreamCodec.composite(
			Vec3.STREAM_CODEC, CombatVfxPayload::origin,
			ByteBufCodecs.VAR_INT, CombatVfxPayload::sourceId,
			ByteBufCodecs.VAR_INT.map(id -> Phase.values()[id], Phase::ordinal), CombatVfxPayload::phase,
			ByteBufCodecs.FLOAT, CombatVfxPayload::yaw,
			ByteBufCodecs.VAR_INT, CombatVfxPayload::targetId,
			Vec3.STREAM_CODEC, CombatVfxPayload::aim,
			CombatVfxPayload::new).cast();

	public CombatVfxPayload(Vec3 origin, int sourceId, Phase phase, float yaw) {
		this(origin, sourceId, phase, yaw, -1, Vec3.ZERO);
	}

	public CombatVfxPayload(Vec3 origin, int sourceId, Phase phase) {
		this(origin, sourceId, phase, 0.0f);
	}

	@Override public Type<CombatVfxPayload> type() { return TYPE; }

	/** Ordinals are wire IDs: keep the existing order when reorganizing feature code. */
	public enum Phase {
		CHARGE, BURST, CANCEL, CLEAR, QUAKE, IMPACT, BOLT, MASK_ON, MASK_OFF, CRYSTAL_ON, CRYSTAL_OFF, CRIT,
		SHIELD_RAM, SHIELD_RAM_SHIELD, BULWARK_ON, BULWARK_OFF, BULWARK_HIT,
		BLOOD_LUNGE, BLOODLUST_ON, BLOODLUST_OFF, BLOOD_SIPHON, HAMMER_STUN,
		AXE_THROW, BLEED_CRIT, SPEAR_DASH, BROADSWORD_SWEEP
	}
}
