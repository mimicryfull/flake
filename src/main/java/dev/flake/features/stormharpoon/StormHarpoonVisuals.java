package dev.flake.features.stormharpoon;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class StormHarpoonVisuals {
	private StormHarpoonVisuals() {}

	public static void strike(ServerPlayer source, Vec3 eyeOrigin, Vec3 hitPos, int hitId) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(eyeOrigin, source.getId(),
				CombatVfxPayload.Phase.STORM_HARPOON, 0.0f, hitId, hitPos), hitPos, 12);
	}
}
