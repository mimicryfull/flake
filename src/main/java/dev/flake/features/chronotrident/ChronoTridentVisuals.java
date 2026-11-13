package dev.flake.features.chronotrident;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class ChronoTridentVisuals {
	private static final int DURATION = (int) Math.ceil(ChronosphereAnimation.DURATION_SECONDS * 20) + 4;

	private ChronoTridentVisuals() {}

	public static void chronosphere(ServerPlayer source, Vec3 center) {
		var payload = new CombatVfxPayload(center, source.getId(), CombatVfxPayload.Phase.CHRONOSPHERE, 0.0f, -1, center);
		CombatVisuals.broadcast(source, payload, center, DURATION);
	}
}
