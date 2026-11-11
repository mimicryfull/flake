package dev.flake.features.bastion;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class BastionVisuals {
	private static final int BULWARK_DURATION = (int) Math.ceil(BastionAnimation.Bulwark.BUFF_SECONDS * 20) + 14;
	private static final int RAM_DURATION = (int) Math.ceil(BastionAnimation.Ram.DURATION * 20) + 4;

	private BastionVisuals() {}

	public static void bulwark(ServerPlayer source, boolean on) {
		var phase = on ? CombatVfxPayload.Phase.BULWARK_ON : CombatVfxPayload.Phase.BULWARK_OFF;
		CombatVisuals.broadcast(source, new CombatVfxPayload(source.position(), source.getId(), phase),
				source.position(), on ? BULWARK_DURATION : 0);
	}

	/** Aim stores a fixed, server-confirmed contact point (not the victim's moving position). */
	public static void shieldRam(ServerPlayer source, LivingEntity target, Vec3 direction, boolean shield) {
		var phase = shield ? CombatVfxPayload.Phase.SHIELD_RAM_SHIELD : CombatVfxPayload.Phase.SHIELD_RAM;
		float yaw = (float) Math.atan2(direction.z, direction.x);
		int targetId = target == null ? -1 : target.getId();
		var contact = target == null ? source.position().add(direction.scale(0.9)).add(0, 0.95, 0)
				: target.position().add(0, target.getBbHeight() * 0.52, 0).subtract(direction.scale(target.getBbWidth() * 0.35));
		var payload = new CombatVfxPayload(source.position(), source.getId(), phase, yaw, targetId, contact);
		CombatVisuals.broadcast(source, payload, source.position(), RAM_DURATION);
	}

	/** A Bulwark owner just took a real hit; unknown damage has no invented incoming direction. */
	public static void bulwarkHit(ServerPlayer victim, Vec3 sourcePosition) {
		var direction = sourcePosition == null ? Vec3.ZERO : sourcePosition.subtract(victim.position()).normalize();
		var payload = new CombatVfxPayload(victim.position(), victim.getId(), CombatVfxPayload.Phase.BULWARK_HIT,
				0, -1, direction);
		CombatVisuals.broadcast(victim, payload, victim.position(), BULWARK_DURATION);
	}
}
