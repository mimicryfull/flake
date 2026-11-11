package dev.flake.features.vampire;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class VampireVisuals {
	private static final int BLOODLUST_DURATION = (int) Math.ceil((GhoulAnimation.BUFF_SECONDS + GhoulAnimation.EXIT) * 20) + 4;
	private static final int LUNGE_DURATION = (int) Math.ceil(GhoulAnimation.LUNGE_SECONDS * 20) + 4;

	private VampireVisuals() {}

	public static void bloodlust(ServerPlayer source, boolean on) {
		var phase = on ? CombatVfxPayload.Phase.BLOODLUST_ON : CombatVfxPayload.Phase.BLOODLUST_OFF;
		var payload = new CombatVfxPayload(source.position(), source.getId(), phase);
		if (on) CombatVisuals.broadcast(source, payload, source.position(), BLOODLUST_DURATION);
		else CombatVisuals.sendToLevel(source, payload);
	}

	/** A valid lunge has already launched; rejected damage must not produce a contact flash. */
	public static void bloodLunge(ServerPlayer source, LivingEntity target, Vec3 direction, boolean hit) {
		var contact = target.position().add(0, target.getBbHeight() * 0.58, 0)
				.subtract(direction.scale(target.getBbWidth() * 0.35));
		var payload = new CombatVfxPayload(source.position(), source.getId(), CombatVfxPayload.Phase.BLOOD_LUNGE,
				(float) Math.atan2(direction.z, direction.x), hit ? target.getId() : -1, contact);
		CombatVisuals.broadcast(source, payload, source.position(), LUNGE_DURATION);
	}

	/** Only called after eligible Bloodlust healing increased actual health, never for Leech alone. */
	public static void bloodSiphon(ServerPlayer receiver, LivingEntity victim, float healed) {
		var extraction = victim.position().add(0, victim.getBbHeight() * 0.58, 0);
		var receiving = receiver.position().add(0, receiver.getBbHeight() * 0.58, 0);
		var outward = receiving.subtract(extraction);
		if (outward.lengthSqr() > 0.0001) extraction = extraction.add(outward.normalize().scale(victim.getBbWidth() * 0.5 + 0.035));
		CombatVisuals.broadcast(receiver, siphonMessage(extraction, receiver.getId(), victim.getId(), receiving, healed), extraction, 18);
	}

	/** The existing wire layout stores actual healed HP in yaw, not an angle. */
	public static CombatVfxPayload siphonMessage(Vec3 extraction, int receiverId, int victimId, Vec3 receiving, float healed) {
		return new CombatVfxPayload(extraction, receiverId, CombatVfxPayload.Phase.BLOOD_SIPHON, healed, victimId, receiving);
	}
}
