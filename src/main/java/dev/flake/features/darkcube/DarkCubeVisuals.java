package dev.flake.features.darkcube;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DarkCubeVisuals {
	private static final int DURATION = 80;

	private DarkCubeVisuals() {}

	/** Purely visual; the server already resolved the target, damage and cooldown. */
	public static void bolt(ServerPlayer source, LivingEntity target) {
		var look = source.getLookAngle();
		var right = look.cross(new Vec3(0, 1, 0)).normalize();
		var muzzle = source.getEyePosition().add(look.scale(0.55)).add(right.scale(0.28)).add(0, -0.28, 0);
		var aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
		var payload = new CombatVfxPayload(muzzle, source.getId(), CombatVfxPayload.Phase.BOLT, 0.0f, target.getId(), aim);
		CombatVisuals.broadcast(source, payload, muzzle, DURATION);
	}
}
