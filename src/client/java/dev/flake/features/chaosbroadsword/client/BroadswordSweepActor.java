package dev.flake.features.chaosbroadsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.chaosbroadsword.ChaosBroadswordAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Voxel sphere + swirling air/wind + varied white/orange/brown/dark-red pulse
 * for the Chaos Broadsword active sweep.
 */
public final class BroadswordSweepActor implements EffectActor {
	private static final float DURATION_TICKS = ChaosBroadswordAnimation.DURATION;
	private static final float GOLDEN_ANGLE = 2.3999632f;

	private final int sourceId;
	private final Vec3 origin;
	private long startNanos = -1L;
	private float ageTicks;

	public BroadswordSweepActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.origin();
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
		float opacity = ChaosBroadswordAnimation.opacity(ageTicks);
		if (opacity <= 0.01f) return;

		float baseRadius = ChaosBroadswordAnimation.radius(ageTicks);
		float cx = (float) (origin.x - cameraPos.x);
		float cy = (float) (origin.y + 0.95 - cameraPos.y);
		float cz = (float) (origin.z - cameraPos.z);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitSphereAndSwing(pose, vc, cx, cy, cz, baseRadius, progress, opacity));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitWindSwirls(pose, vc, cx, cy, cz, baseRadius, progress, opacity));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitPulseGlows(pose, vc, cx, cy, cz, baseRadius, progress, opacity));
	}

	private void emitSphereAndSwing(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float baseRadius, float progress, float opacity) {
		// 1. Expanding voxel sphere shell (Fibonacci lattice, restrained count)
		int sphereCount = 38;
		float spin = progress * 1.6f;
		for (int i = 0; i < sphereCount; i++) {
			float yNorm = 1.0f - 2.0f * (i + 0.5f) / sphereCount;
			float radial = (float) Math.sqrt(Math.max(0f, 1.0f - yNorm * yNorm));
			float theta = i * GOLDEN_ANGLE + spin * (0.6f + 0.4f * (i % 3));

			float pulse = 1.0f + 0.08f * (float) Math.sin(ageTicks * 1.35f + i * 0.9f);
			float r = baseRadius * pulse;

			float vx = cx + (float) Math.cos(theta) * radial * r;
			float vy = cy + yNorm * r * 0.78f;
			float vz = cz + (float) Math.sin(theta) * radial * r;

			float size = (0.082f - 0.032f * progress) * (0.85f + 0.25f * ((i % 3) * 0.5f));
			float[] col = paletteColor(i, progress);
			WeaponVoxelRenderer.emitCube(pose, vc, vx, vy, vz, size, size, size,
					col[0], col[1], col[2], opacity * 0.88f);
		}

		// 2. Fast sweeping blade arc during the sword swing
		float swingFade = Math.clamp(1.0f - progress * 1.25f, 0f, 1f);
		if (swingFade > 0.02f) {
			int arcCount = 16;
			float headAngle = progress * 5.2f;
			float arcRadius = Math.max(1.2f, baseRadius * 0.92f);
			for (int i = 0; i < arcCount; i++) {
				float t = i / (float) (arcCount - 1);
				float ang = headAngle - t * 2.1f;
				float vx = cx + (float) Math.cos(ang) * arcRadius;
				float vz = cz + (float) Math.sin(ang) * arcRadius;
				float vy = cy + (float) Math.sin(t * Math.PI) * 0.22f - 0.10f * t;

				float size = (0.095f - 0.045f * t) * swingFade;
				float[] col = (i < 4)
						? new float[] { 0.99f, 0.97f, 0.93f } // white-hot leading edge
						: paletteColor(i + 1, progress);
				WeaponVoxelRenderer.emitCube(pose, vc, vx, vy, vz, size * 1.2f, size * 0.65f, size * 1.2f,
						col[0], col[1], col[2], swingFade * 0.95f);
			}
		}
	}

	private void emitWindSwirls(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float baseRadius, float progress, float opacity) {
		// Swirling white / pale-ember air voxels expanding around the sphere
		int windCount = 24;
		for (int i = 0; i < windCount; i++) {
			float band = (i % 3) - 1;
			float ang = i * 0.78f + progress * (3.6f + (i % 2) * 1.1f);
			float r = baseRadius * (0.76f + 0.30f * ((i % 4) / 3.0f));
			float vx = cx + (float) Math.cos(ang) * r;
			float vz = cz + (float) Math.sin(ang) * r;
			float vy = cy + band * 0.52f * (0.6f + 0.5f * progress) + (float) Math.sin(ang * 2.0f) * 0.18f;

			float sx = 0.12f * (1.0f - 0.3f * progress);
			float sy = 0.040f;
			float sz = 0.12f * (1.0f - 0.3f * progress);
			boolean pureWhite = (i % 3 != 2);
			float cr = pureWhite ? 0.97f : 0.98f;
			float cg = pureWhite ? 0.96f : 0.80f;
			float cb = pureWhite ? 0.94f : 0.64f;
			WeaponVoxelRenderer.emitCube(pose, vc, vx, vy, vz, sx, sy, sz,
					cr, cg, cb, opacity * 0.44f);
		}
	}

	private void emitPulseGlows(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float baseRadius, float progress, float opacity) {
		int glowCount = 6;
		for (int i = 0; i < glowCount; i++) {
			float ang = i * (float) (Math.PI * 2.0 / glowCount) + progress * 2.1f;
			float pulse = 0.65f + 0.35f * (float) Math.sin(ageTicks * 1.6f + i * 1.3f);
			float r = baseRadius * (0.72f + 0.16f * (i % 2));
			float gx = cx + (float) Math.cos(ang) * r;
			float gz = cz + (float) Math.sin(ang) * r;
			float gy = cy + ((i % 2 == 0) ? 0.25f : -0.20f);

			float cr = (i % 2 == 0) ? 1.0f : 0.72f;
			float cg = (i % 2 == 0) ? 0.50f : 0.14f;
			float cb = (i % 2 == 0) ? 0.10f : 0.06f;
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, gx, gy, gz, 0.85f * pulse,
					cr, cg, cb, opacity * 0.30f * pulse);
		}
	}

	/** White - orange - brown - dark red palette. */
	private static float[] paletteColor(int idx, float progress) {
		int slot = (idx + (int) (progress * 3.0f)) & 3;
		return switch (slot) {
			case 0 -> new float[] { 0.98f, 0.96f, 0.92f }; // white
			case 1 -> new float[] { 1.00f, 0.46f, 0.08f }; // orange
			case 2 -> new float[] { 0.48f, 0.20f, 0.07f }; // brown
			default -> new float[] { 0.44f, 0.04f, 0.04f }; // dark red
		};
	}
}
