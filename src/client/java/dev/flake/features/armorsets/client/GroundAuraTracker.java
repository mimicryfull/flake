package dev.flake.features.armorsets.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared smooth ground-Y tracker for aura VFX actors so jumping/ledges stay free of flicker. */
final class GroundAuraTracker {
	private static final double[] SAMPLE_OFFSETS = {0.0, 0.0, 0.26, 0.26, -0.26, 0.26, 0.26, -0.26, -0.26, -0.26};

	private final int sourceId;
	private Vec3 lastOrigin;
	private float smoothedGroundY;

	GroundAuraTracker(int sourceId, Vec3 initialOrigin) {
		this.sourceId = sourceId;
		this.lastOrigin = initialOrigin;
		this.smoothedGroundY = (float) initialOrigin.y;
	}

	Vec3 resolve(Level level, float partial) {
		if (level != null) {
			Entity entity = level.getEntity(sourceId);
			if (entity != null) {
				lastOrigin = entity.getPosition(partial);
				float targetGroundY;
				if (entity.onGround()) {
					targetGroundY = (float) lastOrigin.y;
				} else {
					double bestY = Double.NEGATIVE_INFINITY;
					for (int i = 0; i < SAMPLE_OFFSETS.length; i += 2) {
						Vec3 probe = lastOrigin.add(SAMPLE_OFFSETS[i], 0.35, SAMPLE_OFFSETS[i + 1]);
						BlockHitResult hit = level.clip(new ClipContext(
								probe, probe.subtract(0.0, 6.0, 0.0),
								ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
						if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y <= lastOrigin.y + 0.15) {
							bestY = Math.max(bestY, hit.getLocation().y);
						}
					}
					targetGroundY = bestY > Double.NEGATIVE_INFINITY ? (float) bestY : (float) lastOrigin.y;
				}
				smoothedGroundY += (targetGroundY - smoothedGroundY) * 0.35f;
			}
		}
		return lastOrigin;
	}

	float groundY() { return smoothedGroundY; }
}
