package dev.flake.features.combatdrone;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class CombatDroneVisuals {
	private CombatDroneVisuals() {}

	public static void spawn(ServerPlayer source, Vec3 dronePos, float yaw, int targetId) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(
				dronePos, source.getId(), CombatVfxPayload.Phase.COMBAT_DRONE_ON, yaw, targetId, dronePos),
				dronePos, CombatDroneRuntime.DURATION_TICKS + 10);
	}

	public static void strike(ServerPlayer source, Vec3 dronePos, float yaw, int targetId, Vec3 targetCenter) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(
				dronePos, source.getId(), CombatVfxPayload.Phase.COMBAT_DRONE_HIT, yaw, targetId, targetCenter),
				dronePos, 28);
	}

	public static void despawn(ServerPlayer source, Vec3 dronePos) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(
				dronePos, source.getId(), CombatVfxPayload.Phase.COMBAT_DRONE_OFF, 0.0f, -1, dronePos),
				dronePos, 10);
	}
}
