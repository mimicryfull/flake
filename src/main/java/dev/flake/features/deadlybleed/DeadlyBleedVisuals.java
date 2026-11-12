package dev.flake.features.deadlybleed;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class DeadlyBleedVisuals {
	private static final int DURATION = 14;

	private DeadlyBleedVisuals() {}

	/** Reuses the Void Paladin crescent slash through the orange palette branch of the crit shader. */
	public static void crit(ServerPlayer source, LivingEntity target) {
		var centre = target.position().add(0, target.getBbHeight() * 0.6, 0);
		var aim = source.getLookAngle();
		CombatVisuals.broadcast(source, new CombatVfxPayload(centre, source.getId(),
				CombatVfxPayload.Phase.BLEED_CRIT, 0.0f, target.getId(), aim), centre, DURATION);
	}
}
