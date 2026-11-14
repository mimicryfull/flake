package dev.flake.features.technician;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-side VFX broadcasts for Technician's Oreshnik Homing Missile and Strike UAV. */
public final class TechnicianVisuals {
	private TechnicianVisuals() {}

	public static void launchMissile(ServerPlayer owner, Vec3 pos, Vec3 dir, int targetId) {
		CombatVisuals.broadcast(owner, new CombatVfxPayload(
				pos, owner.getId(), CombatVfxPayload.Phase.TECHNICIAN_MISSILE_ON,
				0.0f, targetId, dir),
				pos, TechnicianRuntime.MISSILE_FLIGHT_TICKS + 10);
	}

	public static void syncMissile(ServerPlayer owner, Vec3 pos, Vec3 dir, int targetId, int remainingTicks) {
		CombatVisuals.broadcast(owner, new CombatVfxPayload(
				pos, owner.getId(), CombatVfxPayload.Phase.TECHNICIAN_MISSILE_SYNC,
				0.0f, targetId, dir),
				pos, Math.max(12, remainingTicks + 8));
	}

	public static void missileBoom(ServerPlayer owner, Vec3 pos, float blastRadius) {
		CombatVisuals.broadcast(owner, new CombatVfxPayload(
				pos, owner.getId(), CombatVfxPayload.Phase.TECHNICIAN_MISSILE_BOOM,
				blastRadius, -1, pos),
				pos, 32);
	}

	public static void missileOff(ServerLevel level, ServerPlayer owner, int ownerId, Vec3 pos) {
		broadcastOff(level, owner, ownerId, pos, CombatVfxPayload.Phase.TECHNICIAN_MISSILE_OFF);
	}

	public static void launchUav(ServerPlayer owner, Vec3 uavPos, Vec3 lockPos, int targetId) {
		CombatVisuals.broadcast(owner, new CombatVfxPayload(
				uavPos, owner.getId(), CombatVfxPayload.Phase.TECHNICIAN_UAV_ON,
				owner.getYRot(), targetId, lockPos),
				uavPos, TechnicianRuntime.UAV_FLIGHT_TICKS + 10);
	}

	public static void syncUav(ServerPlayer owner, Vec3 uavPos, Vec3 lockPos, int targetId, float yawDeg, int remainingTicks) {
		CombatVisuals.broadcast(owner, new CombatVfxPayload(
				uavPos, owner.getId(), CombatVfxPayload.Phase.TECHNICIAN_UAV_SYNC,
				yawDeg, targetId, lockPos),
				uavPos, Math.max(14, remainingTicks + 8));
	}

	public static void uavStrike(ServerPlayer owner, Vec3 uavPos, Vec3 strikePos, int targetId) {
		CombatVisuals.broadcast(owner, new CombatVfxPayload(
				uavPos, owner.getId(), CombatVfxPayload.Phase.TECHNICIAN_UAV_STRIKE,
				(float) TechnicianRuntime.UAV_BOMB_RADIUS, targetId, strikePos),
				strikePos, 28);
	}

	public static void uavBoom(ServerPlayer owner, Vec3 boomPos) {
		CombatVisuals.broadcast(owner, new CombatVfxPayload(
				boomPos, owner.getId(), CombatVfxPayload.Phase.TECHNICIAN_UAV_BOOM,
				(float) TechnicianRuntime.UAV_DIVE_RADIUS, -1, boomPos),
				boomPos, 32);
	}

	public static void uavOff(ServerLevel level, ServerPlayer owner, int ownerId, Vec3 pos) {
		broadcastOff(level, owner, ownerId, pos, CombatVfxPayload.Phase.TECHNICIAN_UAV_OFF);
	}

	private static void broadcastOff(ServerLevel level, ServerPlayer owner, int ownerId, Vec3 pos,
			CombatVfxPayload.Phase phase) {
		ServerPlayer anchor = owner != null ? owner : (level.players().isEmpty() ? null : level.players().getFirst());
		if (anchor == null) return;
		CombatVisuals.sendToLevel(anchor, new CombatVfxPayload(pos, ownerId, phase));
	}
}
