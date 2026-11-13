package dev.flake.features.junglespear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Restrained emerald/jade voxel dash trail for Jungle Spear.
 */
public final class SpearDashActor implements EffectActor {
	private static final float DURATION_TICKS = 10.0f;

	private final int sourceId;
	private final Vec3 start;
	private final Vec3 end;
	private final Vec3 dir;
	private final Vec3 right;
	private long startNanos = -1L;
	private float ageTicks;

	public SpearDashActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.start = payload.origin().add(0, 0.85, 0);
		this.end = payload.aim().add(0, 0.85, 0);
		Vec3 delta = end.subtract(start);
		double len = delta.length();
		this.dir = len > 1e-4 ? delta.scale(1.0 / len) : new Vec3(0, 0, 1);
		Vec3 r = dir.cross(new Vec3(0, 1, 0));
		this.right = r.lengthSqr() > 1e-4 ? r.normalize() : new Vec3(1, 0, 0);
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		if (startNanos < 0L) startNanos = frame.nanos;
		ageTicks = (frame.nanos - startNanos) * 20.0f / 1_000_000_000.0f;
		return ageTicks >= DURATION_TICKS;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		float progress = Math.clamp(ageTicks / DURATION_TICKS, 0f, 1f);
		float fade = 1.0f - progress * progress;
		if (fade <= 0.01f) return;

		double dashProgress = Math.min(1.0, progress * 2.2);
		int total = 12;

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			for (int i = 0; i < total; i++) {
				float t = i / (float) (total - 1);
				if (t > dashProgress) break;

				float localFade = Math.clamp(1.0f - (float) (dashProgress - t) * 1.15f, 0.15f, 1.0f) * fade;
				float side = ((i % 2 == 0) ? 1f : -1f) * (0.14f + 0.10f * t);
				float lift = (float) Math.sin(t * Math.PI) * 0.16f + ((i % 3) - 1) * 0.07f;

				Vec3 p = start.lerp(end, t).add(right.scale(side)).add(0, lift, 0);
				float size = (0.060f - 0.020f * t) * (0.7f + 0.3f * localFade);

				float r, g, b;
				if (i % 3 == 0) {
					r = 0.25f; g = 0.92f; b = 0.45f; // bright emerald
				} else if (i % 3 == 1) {
					r = 0.10f; g = 0.58f; b = 0.34f; // deep jade
				} else {
					r = 0.65f; g = 0.95f; b = 0.38f; // lime-gold accent
				}

				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (p.x - cameraPos.x), (float) (p.y - cameraPos.y), (float) (p.z - cameraPos.z),
						size, size, size, r, g, b, 0.86f * localFade);
			}
		});

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) -> {
			Vec3 head = start.lerp(end, dashProgress);
			WeaponVoxelRenderer.emitGlowSprite(pose, vc,
					(float) (head.x - cameraPos.x), (float) (head.y - cameraPos.y), (float) (head.z - cameraPos.z),
					0.55f, 0.30f, 0.95f, 0.48f, 0.35f * fade);
		});
	}
}
