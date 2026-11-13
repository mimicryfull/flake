package dev.flake.features.stormstaff;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class StormStaffVisuals {
	private static final int DURATION = 12;

	private StormStaffVisuals() {}

	public static void ray(ServerPlayer source, Vec3 muzzle, Vec3 end) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(muzzle, source.getId(),
				CombatVfxPayload.Phase.STORM_STAFF_RAY, 0.0f, -1, end), muzzle, DURATION);
	}
}
