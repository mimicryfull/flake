package dev.flake.features.concussed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.concussed.Shockwave;
import dev.flake.features.concussed.ShockwaveAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RadialActor;
import dev.flake.render.vfx.RenderFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Experimental voxel + gravitational dissonance charge actor for Concussed Skill 1:
 * pulls swirling voxel shards inward into a spinning, compressing orbital cage before detonation.
 */
public final class ChargeActor extends RadialActor {
	private static final float TOTAL_DURATION = Shockwave.CHARGE_TICKS / 20.0f + 1.0f;

	private long startNanos = -1L;
	private float ageSec;

	public ChargeActor(CombatVfxPayload payload) {
		super(payload);
	}

	@Override public boolean charging() { return true; }
	@Override protected float duration() { return TOTAL_DURATION; }
	@Override protected float radius(float age) { return ShockwaveAnimation.chargeRadius(age); }
	@Override protected float opacity(float age) { return 0.25f + ShockwaveAnimation.chargeProgress(age) * 0.55f; }
	@Override protected int style() { return 1; }
	@Override protected float styleExtra(float age) { return ShockwaveAnimation.chargeProgress(age); }

	@Override
	protected Vec3 position(RenderFrame frame) {
		var source = frame.client.level.getEntity(sourceId);
		return source != null ? source.getPosition(frame.partial).add(0, 1, 0) : origin;
	}

	@Override
	public boolean expired() {
		return startNanos >= 0L && ageSec >= TOTAL_DURATION;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		if (startNanos < 0L) startNanos = System.nanoTime();
		if (!mc.isPaused()) {
			ageSec = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
		}
		if (ageSec >= TOTAL_DURATION) return;

		Vec3 center = origin;
		if (mc.level != null) {
			var entity = mc.level.getEntity(sourceId);
			if (entity != null) {
				center = entity.position().add(0, 1, 0);
			}
		}

		float progress = ShockwaveAnimation.chargeProgress(ageSec);
		float r = ShockwaveAnimation.chargeRadius(ageSec);
		float alpha = Math.min(1.0f, ageSec / 0.08f) * (0.65f + 0.35f * progress);
		if (alpha <= 0.01f) return;

		float cx = (float) (center.x - cameraPos.x);
		float cy = (float) (center.y - cameraPos.y);
		float cz = (float) (center.z - cameraPos.z);

		// Two dense accelerating gyroscopic micro-voxel rings compressing toward the core
		float spin = ageSec * (2.6f + 9.0f * progress * progress);
		emitCageRing(poseStack, collector, cx, cy, cz, Math.max(0.28f, r * 0.95f), 32, 0.58f, 0.2f, spin, progress, alpha);
		emitCageRing(poseStack, collector, cx, cy, cz, Math.max(0.24f, r * 0.88f), 28, -0.64f, 1.7f, -spin * 1.15f, progress, alpha);

		// Compressing rotating voxel core + dense inward-pulled gravity micro-shards
		poseStack.pushPose();
		poseStack.translate(cx, cy, cz);
		poseStack.mulPose(new Matrix4f().rotate(new Quaternionf().rotateY(spin * 1.4f).rotateX(spin * 0.9f)));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			float coreSize = 0.065f + 0.045f * (float) Math.sin(progress * Math.PI);
			WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0f, coreSize, coreSize, coreSize, 0.88f, 0.98f, 1.0f, 0.90f * alpha);
		});
		poseStack.popPose();

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitInwardShards(pose, vc, cx, cy, cz, progress, alpha));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) ->
				WeaponVoxelRenderer.emitGlowSprite(pose, vc, cx, cy, cz, 0.42f + 0.38f * progress, 0.48f, 0.90f, 1.0f, 0.52f * alpha));
	}

	private void emitCageRing(PoseStack poseStack, SubmitNodeCollector collector,
			float cx, float cy, float cz, float ringRadius, int count,
			float tiltX, float tiltY, float spin, float progress, float alpha) {
		poseStack.pushPose();
		poseStack.translate(cx, cy, cz);
		poseStack.mulPose(new Matrix4f().rotate(new Quaternionf().rotateY(tiltY).rotateX(tiltX).rotateY(spin)));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			for (int i = 0; i < count; i++) {
				float ang = (i / (float) count) * (float) (Math.PI * 2.0);
				float px = (float) Math.cos(ang) * ringRadius;
				float pz = (float) Math.sin(ang) * ringRadius;
				float size = (i % 2 == 0 ? 0.030f : 0.020f) * (0.75f + 0.35f * (1.0f - progress * 0.5f));
				float rCol = i % 3 == 0 ? 0.88f : 0.34f;
				float gCol = i % 3 == 0 ? 0.97f : 0.82f;
				WeaponVoxelRenderer.emitCube(pose, vc, px, 0f, pz, size, size, size, rCol, gCol, 1.0f, 0.88f * alpha);
			}
		});
		poseStack.popPose();
	}

	private void emitInwardShards(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float progress, float alpha) {
		for (int i = 0; i < 72; i++) {
			float phase = (ageSec * 1.6f + i / 72.0f) % 1.0f;
			float inward = 1.0f - phase * phase;
			float dist = (0.30f + 3.6f * inward) * (1.0f - 0.35f * progress);
			float ang = i * 2.39996f + phase * 2.9f;
			float yOff = (-0.95f + (i % 9) * 0.24f) * inward;

			float px = cx + (float) Math.cos(ang) * dist;
			float pz = cz + (float) Math.sin(ang) * dist;
			float py = cy + yOff;
			float s = (0.017f + (i % 3) * 0.007f) * (0.45f + 0.55f * (1.0f - inward));
			float fade = Mth.clamp((float) Math.sin(phase * Math.PI) * 1.3f, 0f, 1f);
			WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, s, s, s, 0.50f, 0.91f, 1.0f, 0.78f * fade * alpha);
		}
	}
}
