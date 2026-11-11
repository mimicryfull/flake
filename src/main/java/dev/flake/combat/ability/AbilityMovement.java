package dev.flake.combat.ability;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared launch and horizontal direction rules used by all three extracted class features. */
public final class AbilityMovement {
	private AbilityMovement() {}

	public static Vec3 flat(ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		return look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
	}

	public static Vec3 horizontalDir(Vec3 from, Vec3 to, ServerPlayer fallback) {
		Vec3 diff = to.subtract(from).multiply(1, 0, 1);
		return diff.lengthSqr() < 1e-4 ? flat(fallback) : diff.normalize();
	}

	public static void launch(LivingEntity entity, Vec3 velocity) {
		entity.setDeltaMovement(velocity);
		if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
		else entity.syncVelocity = true;
	}
}
