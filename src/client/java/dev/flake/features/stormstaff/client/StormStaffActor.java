package dev.flake.features.stormstaff.client;

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
 * Jagged 3D cyan-electric voxel lightning bolt from the Storm Staff muzzle to its impact point.
 */
public final class StormStaffActor implements EffectActor {
	private static final float DURATION_SEC = 0.50f;
	private static final int SEGMENTS = 12;

	private final int sourceId;
	private final Vec3 muzzle;
	private final Vec3 end;
	private final Vec3 dir;
	private final Vec3 right;
	private final Vec3 up;
	private final double length;
	private final float seed;
	private long startNanos = -1L;
	private float ageSec;

	public StormStaffActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.muzzle = payload.origin();
		this.end = payload.aim();
		Vec3 delta = end.subtract(muzzle);
		double len = delta.length();
		this.length = Math.max(0.25, len);
		this.dir = len > 1e-4 ? delta.scale(1.0 / len) : new Vec3(0, 0, 1);
		Vec3 rawRight = dir.cross(new Vec3(0, 1, 0));
		this.right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : rawRight.normalize();
		this.up = right.cross(dir).normalize();
		this.seed = (float) (sourceId * 0.37 + muzzle.x * 1.13 + muzzle.z * 2.41);
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
		float flash = (float) Math.pow(Math.clamp(1.0f - progress, 0f, 1f), 1.35);
		if (flash <= 0.015f) return;

		// Subtle electric flicker between two frames so the bolt crackles without noisy CPU work
		int flickerPhase = (int) (ageSec * 28.0f);
		Vec3[] nodes = buildJaggedPolyline(flickerPhase);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitBoltVoxels(pose, vc, cameraPos, nodes, flickerPhase, flash));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitImpactSparks(pose, vc, cameraPos, progress, flash));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitBoltGlows(pose, vc, cameraPos, nodes, flash));
	}

	private Vec3[] buildJaggedPolyline(int flickerPhase) {
		Vec3[] nodes = new Vec3[SEGMENTS + 1];
		nodes[0] = muzzle;
		nodes[SEGMENTS] = end;
		double jitterAmp = Math.min(0.32, 0.14 + length * 0.022);
		for (int i = 1; i < SEGMENTS; i++) {
			float t = i / (float) SEGMENTS;
			float env = (float) Math.sin(t * Math.PI);
			float rx = (hash(seed + i * 3.17f + flickerPhase * 1.31f) - 0.5f) * 2.0f;
			float ry = (hash(seed + i * 5.73f + flickerPhase * 2.19f) - 0.5f) * 2.0f;
			nodes[i] = muzzle.add(dir.scale(t * length))
					.add(right.scale(rx * jitterAmp * env))
					.add(up.scale(ry * jitterAmp * env));
		}
		return nodes;
	}

	private void emitBoltVoxels(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3[] nodes, int flickerPhase, float flash) {
		for (int seg = 0; seg < SEGMENTS; seg++) {
			Vec3 a = nodes[seg];
			Vec3 b = nodes[seg + 1];
			double segLen = a.distanceTo(b);
			int steps = Math.max(3, Math.min(7, (int) Math.ceil(segLen / 0.15)));
			for (int s = 0; s < steps; s++) {
				float u = s / (float) steps;
				Vec3 p = a.lerp(b, u);
				boolean hotCore = ((seg + s) % 2 == 0);
				float size = (hotCore ? 0.046f : 0.058f) * (0.55f + 0.45f * flash);
				float r = hotCore ? 0.82f : 0.12f;
				float g = hotCore ? 0.98f : 0.86f;
				float bCol = 1.00f;
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
						size, size, size, r, g, bCol, flash * (hotCore ? 0.96f : 0.82f));
			}

			// 3 short electric fork twigs along the middle of the bolt
			if (seg == 3 || seg == 6 || seg == 9) {
				Vec3 forkStart = nodes[seg];
				float sideSign = (seg == 6 ? -1.0f : 1.0f);
				float forkSignY = (hash(seed + seg + flickerPhase * 0.7f) > 0.5f ? 1.0f : -1.0f);
				Vec3 forkDir = dir.scale(0.65).add(right.scale(sideSign * 0.58)).add(up.scale(forkSignY * 0.35)).normalize();
				for (int k = 1; k <= 3; k++) {
					Vec3 fp = forkStart.add(forkDir.scale(k * 0.18));
					float fs = (0.036f - k * 0.006f) * (0.5f + 0.5f * flash);
					WeaponVoxelRenderer.emitCube(pose, vc,
							(float) (fp.x - cam.x), (float) (fp.y - cam.y), (float) (fp.z - cam.z),
							fs, fs, fs, 0.24f, 0.92f, 1.00f, flash * 0.78f);
				}
			}
		}
	}

	private void emitImpactSparks(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, float progress, float flash) {
		int sparks = 10;
		for (int i = 0; i < sparks; i++) {
			float h1 = hash(seed + i * 2.13f);
			float h2 = hash(seed + i * 4.91f);
			float ang = (i / (float) sparks) * (float) (Math.PI * 2.0) + h1 * 0.6f;
			float dist = (0.12f + 0.55f * h2) * Math.min(1.0f, progress * 2.2f);
			Vec3 p = end.add(right.scale(Math.cos(ang) * dist))
					.add(up.scale(Math.sin(ang) * dist))
					.add(dir.scale(-0.18f * progress));
			float s = 0.032f * (0.4f + 0.6f * flash);
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
					s, s, s,
					i % 3 == 0 ? 0.88f : 0.16f,
					i % 3 == 0 ? 1.00f : 0.90f,
					1.00f, flash * 0.78f);
		}
	}

	private void emitBoltGlows(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, Vec3[] nodes, float flash) {
		WeaponVoxelRenderer.emitGlowSprite(pose, vc,
				(float) (muzzle.x - cam.x), (float) (muzzle.y - cam.y), (float) (muzzle.z - cam.z),
				0.55f, 0.22f, 0.92f, 1.00f, 0.44f * flash);
		Vec3 mid = nodes[SEGMENTS / 2];
		WeaponVoxelRenderer.emitGlowSprite(pose, vc,
				(float) (mid.x - cam.x), (float) (mid.y - cam.y), (float) (mid.z - cam.z),
				0.70f, 0.18f, 0.86f, 1.00f, 0.28f * flash);
		WeaponVoxelRenderer.emitGlowSprite(pose, vc,
				(float) (end.x - cam.x), (float) (end.y - cam.y), (float) (end.z - cam.z),
				0.82f, 0.55f, 0.96f, 1.00f, 0.52f * flash);
	}

	private static float hash(float x) {
		float s = (float) Math.sin(x * 12.9898f + 78.233f) * 43758.5453f;
		return s - (float) Math.floor(s);
	}
}
