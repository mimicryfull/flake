package dev.flake.features.axeofcumshot;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class AxeOfCumshotVisuals {
	private static final int THROW_DURATION = 12;
	private static final int IMPACT_DURATION = 10;

	private AxeOfCumshotVisuals() {}

	public static void throwAxe(ServerPlayer source, LivingEntity target) {
		var look = source.getLookAngle();
		var start = source.getEyePosition().add(look.scale(0.55)).add(0, -0.25, 0);
		var aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
		CombatVisuals.broadcast(source, new CombatVfxPayload(start, source.getId(),
				CombatVfxPayload.Phase.AXE_THROW, 0.0f, target.getId(), aim), start, THROW_DURATION);
		CombatVisuals.broadcast(source, new CombatVfxPayload(aim, source.getId(),
				CombatVfxPayload.Phase.HAMMER_STUN, 0.0f, target.getId(), aim), aim, IMPACT_DURATION);
	}
}
