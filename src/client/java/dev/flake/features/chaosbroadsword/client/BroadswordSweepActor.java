package dev.flake.features.chaosbroadsword.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Low ground-hugging circular voxel shockwave with tight rings, tidy rising voxel particles,
 * warm glow, and a slower readable sword slash shader arc.
 */
public final class BroadswordSweepActor implements EffectActor {
	private static final float DURATION_SEC = 0.85f;
	private static final float MAX_WAVE_RADIUS = 3.65f;

	private final int sourceId;
	private final Vec3 origin;
	private final float startYaw;
	private long startNanos = -1L;
	private float ageSec;

	public BroadswordSweepActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.origin();
		this.startYaw = payload.yaw();
	}

	@Override public int sourceId() { return sourceId; }

	@Override public boolean expired() { return startNanos >= 0L && ageSec >= DURATION_SEC; }

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		if (startNanos < 0L) startNanos = frame.nanos;
		ageSec = (frame.nanos - startNanos) / 1_000_000_000.0f;
		return ageSec >= DURATION_SEC;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		if (startNanos < 0L) startNanos = System.nanoTime();
		ageSec = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
		if (ageSec >= DURATION_SEC) return;

		float progress = Math.clamp(ageSec / DURATION_SEC, 0f, 1f);
		float fadeStart = 0.48f;
		float fadeT = Math.clamp((ageSec - fadeStart) / (DURATION_SEC - fadeStart), 0f, 1f);
		float opacity = Math.min(1f, ageSec / 0.045f) * (1f - fadeT * fadeT * (3f - 2f * fadeT));
		if (opacity <= 0.01f) return;

		float waveRadius = 0.30f + (MAX_WAVE_RADIUS - 0.30f) * (1.0f - (float) Math.exp(-ageSec * 5.8));
		float cx = (float) (origin.x - cameraPos.x);
		float cy = (float) (origin.y - cameraPos.y);
		float cz = (float) (origin.z - cameraPos.z);

		// 1. Tight, low ground-hugging circular voxel wave
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitDenseVoxelWave(pose, vc, cx, cy, cz, waveRadius, progress, opacity));

		// 2. Tidy rising voxel particles + soft ground mist
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitTidyParticles(pose, vc, cx, cy, cz, waveRadius, progress, opacity));

		// 3. Additive glow along the wave ring
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitWaveGlow(pose, vc, cx, cy, cz, waveRadius, progress, opacity));

		// 4. Slower, clearly readable sword slash shader arc
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SLASH_RENDER_TYPE,
				(pose, vc) -> emitSwordSlashArc(pose, vc, cx, cy, cz, waveRadius, progress));
	}

	private void emitDenseVoxelWave(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress, float opacity) {
		// Tightly packed concentric rings emerging from the ground
		int[] counts = { 64, 72, 56 };
		float[] rOffsets = { 0.10f, 0.0f, -0.11f };
		float[] yHeights = { 0.18f, 0.26f, 0.14f };

		for (int ring = 0; ring < 3; ring++) {
			int n = counts[ring];
			float rBase = Math.max(0.35f, waveRadius + rOffsets[ring]);
			float ringFade = (ring == 2 ? 0.78f : 0.94f) * opacity;

			for (int i = 0; i < n; i++) {
				float ang = (i / (float) n) * (float) (Math.PI * 2.0) + ring * 0.09f;
				float waveMod = (float) Math.sin(ang * 8.0f - ageSec * 12.0f + ring * 1.2f);
				float r = rBase + waveMod * 0.045f;

				float vx = cx + (float) Math.cos(ang) * r;
				float vz = cz + (float) Math.sin(ang) * r;

				float emerge = Math.min(1.0f, ageSec / 0.09f);
				float crestHeight = (0.08f + yHeights[ring] * (0.65f + 0.35f * waveMod)) * emerge * (1.0f - 0.35f * progress);
				float vy = cy + 0.03f + crestHeight * 0.45f;

				float hx = (ring == 1 ? 0.082f : 0.064f) * (1.0f - 0.22f * progress);
				float hy = Math.max(0.032f, crestHeight * 0.52f);
				float hz = hx;

				float[] col = waveColor(ring, i, waveMod, progress);
				WeaponVoxelRenderer.emitCube(pose, vc, vx, vy, vz, hx, hy, hz,
						col[0], col[1], col[2], ringFade);
			}
		}
	}

	private void emitTidyParticles(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress, float opacity) {
		// Small, tidy voxel sparks/dust lifting gently from the ground wave
		int count = 28;
		for (int i = 0; i < count; i++) {
			float h1 = hash(i * 1.37f + 0.23f);
			float h2 = hash(i * 3.11f + 0.61f);
			float ang = (i / (float) count) * (float) (Math.PI * 2.0) + h1 * 0.22f + progress * 0.6f;
			float r = waveRadius * (0.86f + 0.16f * h2);
			float vx = cx + (float) Math.cos(ang) * r;
			float vz = cz + (float) Math.sin(ang) * r;
			float lift = (0.08f + 0.34f * h1) * (0.35f + 0.65f * progress);
			float vy = cy + 0.10f + lift;

			float s = (0.026f + 0.018f * h2) * (1.0f - 0.45f * progress);
			boolean bright = (i % 3 == 0);
			float cr = bright ? 0.99f : 1.00f;
			float cg = bright ? 0.94f : 0.55f;
			float cb = bright ? 0.85f : 0.14f;
			WeaponVoxelRenderer.emitCube(pose, vc, vx, vy, vz, s, s, s,
					cr, cg, cb, opacity * 0.72f);
		}
	}

	private void emitWaveGlow(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress, float opacity) {
		int glowCount = 22;
		for (int i = 0; i < glowCount; i++) {
			float ang = (i / (float) glowCount) * (float) (Math.PI * 2.0) + progress * 0.6f;
			float r = waveRadius + 0.02f;
			float gx = cx + (float) Math.cos(ang) * r;
			float gz = cz + (float) Math.sin(ang) * r;
			float gy = cy + 0.18f;

			boolean hot = (i % 3 == 0);
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, gx, gy, gz,
					0.85f * (1.0f - 0.22f * progress),
					hot ? 1.0f : 0.94f,
					hot ? 0.62f : 0.34f,
					hot ? 0.18f : 0.07f,
					opacity * 0.34f);
		}
	}

	private void emitSwordSlashArc(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress) {
		// Slower, clearly visible sword slash arc
		float slashFade = Math.clamp(1.0f - progress / 0.86f, 0f, 1f);
		if (slashFade <= 0.01f) return;

		int segments = 30;
		float sweepProgress = Math.min(1.0f, progress / 0.55f);
		float arcSpan = (float) (Math.PI * 1.75) * sweepProgress;
		float baseAngle = startYaw + (float) (Math.PI * 0.10) + progress * 1.65f;

		float outerR = Math.max(1.6f, waveRadius * 0.96f + 0.45f);
		float innerR = Math.max(0.55f, outerR - 1.15f);
		float slashY = cy + 0.80f;

		for (int i = 0; i < segments; i++) {
			float u0 = i / (float) segments;
			float u1 = (i + 1) / (float) segments;
			float a0 = baseAngle + u0 * arcSpan;
			float a1 = baseAngle + u1 * arcSpan;

			float tilt0 = (float) Math.sin(a0 - baseAngle) * 0.22f;
			float tilt1 = (float) Math.sin(a1 - baseAngle) * 0.22f;

			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

			vc.addVertex(pose, cx + c0 * innerR, slashY + tilt0, cz + s0 * innerR).setUv(u0, 0f).setColor(1f, 1f, 1f, slashFade);
			vc.addVertex(pose, cx + c0 * outerR, slashY + tilt0 * 1.15f, cz + s0 * outerR).setUv(u0, 1f).setColor(1f, 1f, 1f, slashFade);
			vc.addVertex(pose, cx + c1 * outerR, slashY + tilt1 * 1.15f, cz + s1 * outerR).setUv(u1, 1f).setColor(1f, 1f, 1f, slashFade);
			vc.addVertex(pose, cx + c1 * innerR, slashY + tilt1, cz + s1 * innerR).setUv(u1, 0f).setColor(1f, 1f, 1f, slashFade);
		}
	}

	private static float hash(float x) {
		float s = (float) Math.sin(x * 12.9898f + 78.233f) * 43758.5453f;
		return s - (float) Math.floor(s);
	}

	/** White - orange - brown - dark red palette across the wave rings. */
	private static float[] waveColor(int ring, int idx, float waveMod, float progress) {
		if (ring == 0) {
			return (idx % 3 == 0 || waveMod > 0.45f)
					? new float[] { 0.99f, 0.96f, 0.90f }
					: new float[] { 1.00f, 0.52f, 0.10f };
		}
		if (ring == 1) {
			return switch ((idx + (int) (progress * 4)) % 3) {
				case 0 -> new float[] { 1.00f, 0.45f, 0.08f };
				case 1 -> new float[] { 0.56f, 0.24f, 0.07f };
				default -> new float[] { 0.92f, 0.62f, 0.18f };
			};
		}
		return (idx % 2 == 0)
				? new float[] { 0.46f, 0.18f, 0.06f }
				: new float[] { 0.48f, 0.04f, 0.04f };
	}
}
