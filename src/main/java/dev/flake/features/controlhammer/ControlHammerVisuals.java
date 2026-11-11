package dev.flake.features.controlhammer;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class ControlHammerVisuals {
	private static final int DURATION = (int) Math.ceil(HammerAnimation.DURATION * 20) + 2;

	private ControlHammerVisuals() {}

	/** A tight world-space pressure burst at the confirmed contact point. */
	public static void hammerStun(ServerPlayer source, LivingEntity target) {
		if (target == null) return;
		var contact = target.position().add(0, target.getBbHeight() * 0.55, 0);
		var payload = new CombatVfxPayload(contact, source.getId(), CombatVfxPayload.Phase.HAMMER_STUN,
				0.0f, target.getId(), contact);
		CombatVisuals.broadcast(source, payload, contact, DURATION);
	}
}
