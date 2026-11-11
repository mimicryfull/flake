package dev.flake.features.voidpaladin;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class VoidPaladinVisuals {
	private static final int DURATION = (int) Math.ceil(CritAnimation.DURATION * 20) + 2;

	private VoidPaladinVisuals() {}

	/** A short world-space crimson rupture at the hit point, visible to everyone nearby. */
	public static void crit(ServerPlayer attacker, LivingEntity target) {
		if (attacker.level().getServer() == null) return;
		var origin = target.position().add(0, target.getBbHeight() * 0.62, 0);
		var direction = origin.subtract(attacker.getEyePosition());
		var aim = direction.lengthSqr() > 0.0001 ? direction.normalize() : attacker.getLookAngle();
		var payload = new CombatVfxPayload(origin, attacker.getId(), CombatVfxPayload.Phase.CRIT, 0.0f, target.getId(), aim);
		CombatVisuals.broadcast(attacker, payload, origin, DURATION);
	}
}
