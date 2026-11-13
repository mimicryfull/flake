package dev.flake.features.chaosbroadsword;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;

public final class ChaosBroadswordVisuals {
	private static final int DURATION = 10;

	private ChaosBroadswordVisuals() {}

	public static void sweep(ServerPlayer source) {
		var origin = source.position().add(0, 0.05, 0);
		CombatVisuals.broadcast(source, new CombatVfxPayload(origin, source.getId(),
				CombatVfxPayload.Phase.BROADSWORD_SWEEP, (float) Math.toRadians(source.getYRot()), -1, origin.add(0, 0.9, 0)), origin, DURATION);
	}
}
