package dev.flake.features.kolobok;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-side VFX broadcasts for Kolobok Dual-AK artifact. */
public final class KolobokVisuals {
	private KolobokVisuals() {}

	public static void spawn(ServerPlayer owner, Vec3 pos, float yaw, int targetId) {
		CombatVisuals.broadcast(owner, new CombatVfxPayload(
				pos, owner.getId(), CombatVfxPayload.Phase.KOLOBOK_ON, yaw, targetId, pos),
				pos, KolobokRuntime.DURATION_TICKS + 10);
	}

	/** Broadcasts a burst shot. yaw sign encodes barrel side (>0 = left AK, <0 = right AK) encoded as yaw + hand flag via targetId sign or yaw. */
	public static void shoot(ServerPlayer owner, Vec3 pos, float yaw, boolean leftHand, int targetId, Vec3 impactPos) {
		float encodedYaw = leftHand ? yaw + 720.0f : yaw - 720.0f;
		CombatVisuals.broadcast(owner, new CombatVfxPayload(
				pos, owner.getId(), CombatVfxPayload.Phase.KOLOBOK_SHOT, encodedYaw, targetId, impactPos),
				pos, 24);
	}

	public static void despawn(ServerLevel level, ServerPlayer owner, int ownerId, Vec3 pos) {
		ServerPlayer anchor = owner != null ? owner : (level.players().isEmpty() ? null : level.players().getFirst());
		if (anchor == null) return;
		CombatVisuals.sendToLevel(anchor, new CombatVfxPayload(pos, ownerId, CombatVfxPayload.Phase.KOLOBOK_OFF));
	}
}
