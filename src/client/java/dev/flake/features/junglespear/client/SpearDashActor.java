package dev.flake.features.junglespear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Matrix-style green voxel dash trail aligned strictly along the dash axis.
 * Elongated voxel streaks surge forward with the dash, glow brightly on arrival, and smoothly fade out.
 */
public final class SpearDashActor implements EffectActor {
	private static final float DURATION_TICKS = 26.0f;
	private static final float WAVE_TICKS = 9.0f;

	private final int sourceId;
	private final Vec3 start;
	private final Vec3 dir;
	private final float dashLength;
	private final float yaw;
	private final float pitch;
	private long startNanos = -1L;
	private float ageTicks;

	public SpearDashActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.start = payload.origin();
		Vec3 delta = payload.aim().subtract(start);
		double len = delta.length();
		this.dashLength = (float) Math.max(1.0, len);
		this.dir = len > 1e-4 ? delta.scale(1.0 / len) : new Vec3(0, 0, 1);
		this.yaw = (float) Math.atan2(dir.x, dir.z);
		float horiz = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		this.pitch = (float) Math.atan2(-dir.y, horiz);
	}

	@Override public int sourceId() { return sourceId; }

	@Override public boolean expired() { return startNanos >= 0L && ageTicks >= DURATION_TICKS; }

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		if (startNanos < 0L) startNanos = frame.nanos;
		ageTicks = (frame.nanos - startNanos) * 20.0f / 1_000_000_000.0f;
		return ageTicks >= DURATION_TICKS;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		if (startNanos < 0L) startNanos = System.nanoTime();
		ageTicks = (System.nanoTime() - startNanos) * 20.0f / 1_000_000_000.0f;
		if (ageTicks >= DURATION_TICKS) return;

		poseStack.pushPose();
		poseStack.translate(start.x - cameraPos.x, start.y - cameraPos.y, start.z - cameraPos.z);
		poseStack.mulPose(new Matrix4f().rotate(new Quaternionf().rotateY(yaw).rotateX(pitch)));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitAlignedStreaks(pose, vc, false));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitAlignedStreaks(pose, vc, true));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				this::emitAlignedGlows);

		poseStack.popPose();
	}

	private void emitAlignedStreaks(PoseStack.Pose pose, VertexConsumer vc, boolean softLayer) {
		int count = 132;
		for (int i = 0; i < count; i++) {
			boolean isSoft = (i % 3 == 2);
			if (isSoft != softLayer) continue;

			float h1 = hash(i * 1.13f + 0.17f);
			float h2 = hash(i * 2.71f + 0.43f);
			float h3 = hash(i * 4.19f + 0.89f);
			float h4 = hash(i * 6.07f + 0.31f);

			float pathT = (i / (float) count) * 0.94f + h1 * 0.06f;
			float spawnTick = pathT * WAVE_TICKS + h2 * 1.0f;
			float localAge = ageTicks - spawnTick;
			if (localAge < 0f) continue;

			float lifeTicks = 12.5f + h3 * 8.5f;
			if (localAge >= lifeTicks) continue;

			float lifeNorm = localAge / lifeTicks;
			float popIn = Math.min(1.0f, localAge / 1.4f);
			float fade = (float) Math.pow(Math.max(0.0f, 1.0f - lifeNorm), 1.45);
			float alpha = (softLayer ? 0.56f : 0.90f) * popIn * fade;
			if (alpha <= 0.015f) continue;

			// Cylindrical distribution around the dash line, strictly aligned along +Z (dash axis)
			float angle = h2 * (float) (Math.PI * 2.0);
			float radius = 0.24f + 0.88f * (float) Math.sqrt(h3);
			float lx = (float) Math.cos(angle) * radius;
			float ly = (float) Math.sin(angle) * radius * 0.85f + (h4 - 0.5f) * 0.22f;

			// Smoothly glide forward along the dash axis as it trails behind
			float zProgress = pathT * dashLength + (lifeNorm * 0.45f * (0.4f + h1));

			// Elongated along Z (dash axis), slim cross-section that gently tapers out
			float stretch = Math.min(1.0f, localAge / 2.2f) * (0.65f + 0.35f * fade);
			float thickness = (0.022f + 0.016f * h4) * (0.45f + 0.55f * fade);
			float hz = (0.18f + 0.26f * h1) * stretch;

			// Brighter flash right on spawn, then smooth Matrix green palette
			float spawnGlow = Math.max(0f, 1.0f - localAge / 3.5f) * 0.35f;
			float r, g, b;
			if (h4 > 0.88f) {
				r = 0.78f; g = 1.00f; b = 0.84f;
			} else if (h4 > 0.52f) {
				r = 0.14f + spawnGlow; g = 1.00f; b = 0.38f + spawnGlow;
			} else if (h4 > 0.22f) {
				r = 0.36f + spawnGlow * 0.8f; g = 0.96f; b = 0.26f;
			} else {
				r = 0.05f + spawnGlow * 0.5f; g = 0.65f; b = 0.25f;
			}

			WeaponVoxelRenderer.emitCube(pose, vc,
					lx, ly, zProgress,
					thickness, thickness, hz,
					Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), alpha);
		}
	}

	private void emitAlignedGlows(PoseStack.Pose pose, VertexConsumer vc) {
		float globalFade = Math.clamp(1.0f - ageTicks / DURATION_TICKS, 0f, 1f);
		if (globalFade <= 0.01f) return;

		int nodes = 10;
		for (int i = 0; i < nodes; i++) {
			float t = i / (float) (nodes - 1);
			float spawn = t * WAVE_TICKS;
			float localAge = ageTicks - spawn;
			if (localAge < 0f) continue;

			float pop = Math.min(1.0f, localAge / 1.8f);
			float localFade = (float) Math.pow(Math.clamp(1.0f - localAge / 17.0f, 0f, 1f), 1.35);
			float a = 0.34f * pop * localFade * globalFade;
			if (a <= 0.008f) continue;

			float z = t * dashLength;
			WeaponVoxelRenderer.emitGlowSprite(pose, vc,
					0f, 0f, z,
					1.05f * (0.65f + 0.35f * pop),
					0.18f, 1.00f, 0.42f, a);
		}
	}

	private static float hash(float x) {
		float s = (float) Math.sin(x * 12.9898f + 78.233f) * 43758.5453f;
		return s - (float) Math.floor(s);
	}
}
