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
 * Dense circular voxel shockwave with glowing crest + procedural sword slash arc shader
 * for the Chaos Broadsword active sweep.
 */
public final class BroadswordSweepActor implements EffectActor {
	private static final float DURATION_TICKS = ChaosBroadswordAnimation.DURATION;

	private final int sourceId;
	private final Vec3 origin;
	private final float startYaw;
	private long startNanos = -1L;
	private float ageTicks;

	public BroadswordSweepActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.origin();
		this.startYaw = payload.yaw();
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

		float waveRadius = ChaosBroadswordAnimation.radius(ageTicks);
		float cx = (float) (origin.x - cameraPos.x);
		float cy = (float) (origin.y - cameraPos.y);
		float cz = (float) (origin.z - cameraPos.z);

		// 1. Dense circular voxel wave (3 concentric rings)
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitDenseVoxelWave(pose, vc, cx, cy, cz, waveRadius, progress, opacity));

		// 2. Soft air/wind wisps riding the wave crest
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitWaveAir(pose, vc, cx, cy, cz, waveRadius, progress, opacity));

		// 3. Additive glow along the wave ring
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitWaveGlow(pose, vc, cx, cy, cz, waveRadius, progress, opacity));

		// 4. Sword slash texture shader ribbon during the swing
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SLASH_RENDER_TYPE,
				(pose, vc) -> emitSwordSlashArc(pose, vc, cx, cy, cz, waveRadius, progress));
	}

	private void emitDenseVoxelWave(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress, float opacity) {
		// Ring 0: leading bright crest; Ring 1: main dense wall; Ring 2: trailing ember/dark-red base
		int[] counts = { 56, 64, 44 };
		float[] rOffsets = { 0.16f, 0.0f, -0.26f };
		float[] yHeights = { 0.30f, 0.44f, 0.22f };

		for (int ring = 0; ring < 3; ring++) {
			int n = counts[ring];
			float rBase = Math.max(0.45f, waveRadius + rOffsets[ring]);
			float ringFade = (ring == 2 ? 0.72f : 0.94f) * opacity;

			for (int i = 0; i < n; i++) {
				float ang = (i / (float) n) * (float) (Math.PI * 2.0) + ring * 0.11f;
				float waveMod = (float) Math.sin(ang * 7.0f - ageTicks * 0.85f + ring * 1.4f);
				float r = rBase + waveMod * 0.07f;

				float vx = cx + (float) Math.cos(ang) * r;
				float vz = cz + (float) Math.sin(ang) * r;

				float crestHeight = (0.14f + yHeights[ring] * (0.65f + 0.35f * waveMod)) * (1.0f - 0.38f * progress);
				float vy = cy + 0.12f + crestHeight * 0.5f;

				float hx = (ring == 1 ? 0.095f : 0.072f) * (1.0f - 0.25f * progress);
				float hy = Math.max(0.04f, crestHeight * 0.55f);
				float hz = hx;

				float[] col = waveColor(ring, i, waveMod, progress);
				WeaponVoxelRenderer.emitCube(pose, vc, vx, vy, vz, hx, hy, hz,
						col[0], col[1], col[2], ringFade);
			}
		}
	}

	private void emitWaveAir(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress, float opacity) {
		int airCount = 32;
		for (int i = 0; i < airCount; i++) {
			float ang = (i / (float) airCount) * (float) (Math.PI * 2.0) + progress * 2.2f;
			float r = waveRadius * (0.90f + 0.14f * ((i % 3) - 1));
			float vx = cx + (float) Math.cos(ang) * r;
			float vz = cz + (float) Math.sin(ang) * r;
			float vy = cy + 0.55f + 0.28f * (float) Math.sin(ang * 5.0f + ageTicks * 0.7f);

			float s = 0.095f * (1.0f - 0.3f * progress);
			WeaponVoxelRenderer.emitCube(pose, vc, vx, vy, vz, s, 0.032f, s,
					0.98f, 0.94f, 0.88f, opacity * 0.42f);
		}
	}

	private void emitWaveGlow(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress, float opacity) {
		int glowCount = 20;
		for (int i = 0; i < glowCount; i++) {
			float ang = (i / (float) glowCount) * (float) (Math.PI * 2.0) + progress * 0.8f;
			float r = waveRadius + 0.04f;
			float gx = cx + (float) Math.cos(ang) * r;
			float gz = cz + (float) Math.sin(ang) * r;
			float gy = cy + 0.36f;

			boolean hot = (i % 3 == 0);
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, gx, gy, gz,
					1.05f * (1.0f - 0.25f * progress),
					hot ? 1.0f : 0.92f,
					hot ? 0.62f : 0.32f,
					hot ? 0.18f : 0.06f,
					opacity * 0.36f);
		}
	}

	private void emitSwordSlashArc(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress) {
		float slashFade = Math.clamp(1.0f - progress / 0.62f, 0f, 1f);
		if (slashFade <= 0.01f) return;

		int segments = 28;
		float sweepProgress = Math.min(1.0f, progress / 0.38f);
		float arcSpan = (float) (Math.PI * 1.65) * sweepProgress;
		float baseAngle = startYaw + (float) (Math.PI * 0.15) + progress * 2.4f;

		float outerR = Math.max(1.8f, waveRadius * 0.98f + 0.65f);
		float innerR = Math.max(0.6f, outerR - 1.35f);
		float slashY = cy + 1.05f;

		for (int i = 0; i < segments; i++) {
			float u0 = i / (float) segments;
			float u1 = (i + 1) / (float) segments;
			float a0 = baseAngle + u0 * arcSpan;
			float a1 = baseAngle + u1 * arcSpan;

			// Slight diagonal tilt matching a broadsword horizontal-diagonal swing
			float tilt0 = (float) Math.sin(a0 - baseAngle) * 0.24f;
			float tilt1 = (float) Math.sin(a1 - baseAngle) * 0.24f;

			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

			vc.addVertex(pose, cx + c0 * innerR, slashY + tilt0, cz + s0 * innerR).setUv(u0, 0f).setColor(1f, 1f, 1f, slashFade);
			vc.addVertex(pose, cx + c0 * outerR, slashY + tilt0 * 1.15f, cz + s0 * outerR).setUv(u0, 1f).setColor(1f, 1f, 1f, slashFade);
			vc.addVertex(pose, cx + c1 * outerR, slashY + tilt1 * 1.15f, cz + s1 * outerR).setUv(u1, 1f).setColor(1f, 1f, 1f, slashFade);
			vc.addVertex(pose, cx + c1 * innerR, slashY + tilt1, cz + s1 * innerR).setUv(u1, 0f).setColor(1f, 1f, 1f, slashFade);
		}
	}

	/** White - orange - brown - dark red palette across the wave rings. */
	private static float[] waveColor(int ring, int idx, float waveMod, float progress) {
		if (ring == 0) {
			// Leading edge: bright white & hot orange
			return (idx % 3 == 0 || waveMod > 0.45f)
					? new float[] { 0.99f, 0.96f, 0.90f }
					: new float[] { 1.00f, 0.52f, 0.10f };
		}
		if (ring == 1) {
			// Main wave crest: orange & brown-amber
			return switch ((idx + (int) (progress * 4)) % 3) {
				case 0 -> new float[] { 1.00f, 0.45f, 0.08f };
				case 1 -> new float[] { 0.56f, 0.24f, 0.07f };
				default -> new float[] { 0.92f, 0.62f, 0.18f };
			};
		}
		// Inner trailing base: brown & dark red
		return (idx % 2 == 0)
				? new float[] { 0.46f, 0.18f, 0.06f }
				: new float[] { 0.48f, 0.04f, 0.04f };
	}
}
