package dev.flake.features.sparksyringe;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;

public final class SparkSyringeVisuals {
	private SparkSyringeVisuals() {}

	public static void cleanse(ServerPlayer source) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(source.position(), source.getId(),
				CombatVfxPayload.Phase.SPARK_SYRINGE), source.position(), 19);
	}
}
