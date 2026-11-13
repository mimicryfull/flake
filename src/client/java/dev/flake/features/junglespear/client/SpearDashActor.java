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

/**
 * Matrix-like spatial rift of dense green voxels along the Jungle Spear dash.
 * Spawns many volumetric glitch-voxels across a wide corridor that linger as a trail and vanish asynchronously.
 */
public final class SpearDashActor implements EffectActor {
	private static final float DURATION_TICKS = 26.0f;
	private static final float WAVE_TICKS = 9.0f;

	private final int sourceId;
	private final Vec3 start;
	private final Vec3 end;
	private final Vec3 dir;
	private final Vec3 right;
	private long startNanos = -1L;
	private float ageTicks;

	public SpearDashActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.start = payload.origin();
		this.end = payload.aim();
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
		if (ageTicks >= DURATION_TICKS) return;

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitMatrixVoxels(pose, vc, cameraPos, false));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitMatrixVoxels(pose, vc, cameraPos, true));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitGlows(pose, vc, cameraPos));
	}

	private void emitMatrixVoxels(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, boolean softLayer) {
		int count = 144;
		for (int i = 0; i < count; i++) {
			// Partition between crisp core shards and softer outer rift fragments
			boolean isSoft = (i % 3 == 2);
			if (isSoft != softLayer) continue;

			float h1 = hash(i * 1.13f + 0.17f);
			float h2 = hash(i * 2.71f + 0.43f);
			float h3 = hash(i * 4.19f + 0.89f);
			float h4 = hash(i * 6.07f + 0.31f);

			float pathT = (i / (float) count) * 0.92f + h1 * 0.08f;
			float spawnTick = pathT * WAVE_TICKS + h2 * 1.2f;
			float localAge = ageTicks - spawnTick;
			if (localAge < 0f) continue;

			// Individual voxel lifetime so fragments vanish asynchronously
			float lifeTicks = 10.0f + h3 * 10.5f;
			if (localAge >= lifeTicks) continue;

			float lifeNorm = localAge / lifeTicks;
			float popIn = Math.min(1.0f, localAge / 1.1f);
			float vanish = lifeNorm > 0.62f ? Math.max(0.0f, 1.0f - (lifeNorm - 0.62f) / 0.38f) : 1.0f;
			float alpha = (softLayer ? 0.58f : 0.92f) * popIn * vanish;
			if (alpha <= 0.02f) continue;

			// Wide spatial tear around the dash corridor + slight outward drift
			float angle = h2 * (float) (Math.PI * 2.0);
			float radialDist = (0.28f + 1.05f * (float) Math.sqrt(h3)) * (1.0f + 0.22f * lifeNorm);
			float lateral = (float) Math.cos(angle) * radialDist;
			float vertical = (float) Math.sin(angle) * radialDist * 0.85f + (h4 - 0.5f) * 0.35f;
			float jitterBack = -lifeNorm * (0.18f + 0.25f * h1);

			Vec3 pos = start.lerp(end, pathT)
					.add(right.scale(lateral))
					.add(0, vertical, 0)
					.add(dir.scale(jitterBack));

			float shrink = 0.55f + 0.45f * vanish;
			float hx, hy, hz;
			if (i % 4 == 0) {
				// Vertical Matrix glitch pillar
				hx = 0.030f * shrink;
				hy = (0.10f + 0.14f * h4) * shrink;
				hz = 0.030f * shrink;
			} else if (i % 4 == 1) {
				// Speed-stretched horizontal shard
				hx = (0.045f + 0.045f * Math.abs((float) dir.x)) * shrink;
				hy = 0.032f * shrink;
				hz = (0.045f + 0.045f * Math.abs((float) dir.z)) * shrink;
			} else {
				// Crisp voxel cube
				float s = (0.042f + 0.048f * h4) * shrink;
				hx = s; hy = s; hz = s;
			}

			float r, g, b;
			if (h4 > 0.88f) {
				r = 0.78f; g = 1.00f; b = 0.82f; // bright white-green core spark
			} else if (h4 > 0.55f) {
				r = 0.12f; g = 1.00f; b = 0.36f; // neon Matrix green
			} else if (h4 > 0.25f) {
				r = 0.38f; g = 0.96f; b = 0.24f; // bright lime
			} else {
				r = 0.04f; g = 0.58f; b = 0.22f; // deep emerald
			}

			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z),
					hx, hy, hz, r, g, b, alpha);
		}
	}

	private void emitGlows(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam) {
		float globalFade = Math.clamp(1.0f - ageTicks / DURATION_TICKS, 0f, 1f);
		if (globalFade <= 0.01f) return;

		int nodes = 7;
		for (int i = 0; i < nodes; i++) {
			float t = i / (float) (nodes - 1);
			float spawn = t * WAVE_TICKS;
			if (ageTicks < spawn) continue;
			float local = Math.clamp(1.0f - (ageTicks - spawn) / 14.0f, 0f, 1f);
			if (local <= 0.01f) continue;

			Vec3 p = start.lerp(end, t);
			WeaponVoxelRenderer.emitGlowSprite(pose, vc,
					(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
					0.95f * (0.65f + 0.35f * local),
					0.15f, 1.00f, 0.38f, 0.28f * local * globalFade);
		}
	}

	private static float hash(float x) {
		float s = (float) Math.sin(x * 12.9898f + 78.233f) * 43758.5453f;
		return s - (float) Math.floor(s);
	}
}
