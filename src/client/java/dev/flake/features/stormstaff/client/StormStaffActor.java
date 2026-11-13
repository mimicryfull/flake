package dev.flake.features.stormstaff.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Concussed-style volumetric plasma lightning (`style = 20`, mode 0) with branching electric forks
 * and impact spark embers for Storm Staff.
 */
public final class StormStaffActor implements EffectActor {
	private static final float DURATION_SEC = 0.48f;
	private static final int MAIN_SEGMENTS = 14;
	private static final int BRANCH_COUNT = 5;
	private static final int BRANCH_STEPS = 3;

	private final int sourceId;
	private final Vec3 muzzle;
	private final Vec3 end;
	private final Vec3 dir;
	private final Vec3 right;
	private final Vec3 up;
	private final double length;
	private final float seed;
	private final VfxTimeline timeline = new VfxTimeline();
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

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.STORM_STAFF_RAY;
	}

	@Override public boolean expired() { return ageSec >= DURATION_SEC; }

	private Vec3[] buildJaggedPolyline(int flickerPhase) {
		Vec3[] nodes = new Vec3[MAIN_SEGMENTS + 1];
		nodes[0] = muzzle;
		nodes[MAIN_SEGMENTS] = end;
		double jitterAmp = Math.min(0.42, 0.16 + length * 0.032);
		for (int i = 1; i < MAIN_SEGMENTS; i++) {
			float t = i / (float) MAIN_SEGMENTS;
			float env = (float) Math.sin(t * Math.PI);
			float rx = (hash(seed + i * 3.17f + flickerPhase * 1.31f) - 0.5f) * 2.0f;
			float ry = (hash(seed + i * 5.73f + flickerPhase * 2.19f) - 0.5f) * 2.0f;
			nodes[i] = muzzle.add(dir.scale(t * length))
					.add(right.scale(rx * jitterAmp * env))
					.add(up.scale(ry * jitterAmp * env));
		}
		return nodes;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		float progress = Math.clamp(ageSec / DURATION_SEC, 0f, 1f);
		float alpha = (float) Math.pow(1.0f - progress, 1.15f);
		int flickerPhase = (int) (ageSec * 24.0f);
		Vec3[] nodes = buildJaggedPolyline(flickerPhase);

		Vec3 mid = muzzle.add(end).scale(0.5);
		float boundRadius = (float) (length * 0.55 + 2.0);
		float[] values = slot.values;
		values[slot.position] = (float) (mid.x - frame.camera.x);
		values[slot.position + 1] = (float) (mid.y - frame.camera.y);
		values[slot.position + 2] = (float) (mid.z - frame.camera.z);
		values[slot.position + 3] = boundRadius;

		values[slot.style] = alpha;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 20.0f;
		values[slot.style + 3] = 0.0f; // mode 0: jagged volumetric shader lightning

		int segCursor = 0;
		for (int s = 0; s < MAIN_SEGMENTS; s++) {
			writeSegment(values, slot, segCursor++, frame.camera, nodes[s], nodes[s + 1], 0.036f, 1.0f);
		}
		// Secondary thin parallel crackling arc
		for (int s = 0; s < MAIN_SEGMENTS; s++) {
			Vec3 a = nodes[s];
			Vec3 b = nodes[s + 1];
			float jx = (hash(seed + s * 7.1f + flickerPhase * 3.1f) - 0.5f) * 0.24f;
			float jy = (hash(seed + s * 4.3f + flickerPhase * 1.9f) - 0.5f) * 0.24f;
			Vec3 pa = s == 0 ? a : a.add(right.scale(jx)).add(up.scale(jy));
			float jx2 = (hash(seed + (s + 1) * 7.1f + flickerPhase * 3.1f) - 0.5f) * 0.24f;
			float jy2 = (hash(seed + (s + 1) * 4.3f + flickerPhase * 1.9f) - 0.5f) * 0.24f;
			Vec3 pb = (s + 1 == MAIN_SEGMENTS) ? b : b.add(right.scale(jx2)).add(up.scale(jy2));
			writeSegment(values, slot, segCursor++, frame.camera, pa, pb, 0.017f, 0.65f);
		}
		// Electric forks fanning outward from the main trunk (Concussed Skill 2 style)
		for (int b = 0; b < BRANCH_COUNT && segCursor + BRANCH_STEPS <= 64; b++) {
			int anchorIdx = Math.min(MAIN_SEGMENTS - 1, 2 + b * 2);
			Vec3 cur = nodes[anchorIdx];
			float sideSign = (b % 2 == 0) ? 1.0f : -1.0f;
			float upSign = (hash(seed + b * 2.7f + flickerPhase) > 0.5f) ? 1.0f : -1.0f;
			Vec3 fDir = dir.scale(0.62).add(right.scale(sideSign * 0.56)).add(up.scale(upSign * 0.42)).normalize();
			for (int k = 0; k < BRANCH_STEPS; k++) {
				float jx = (hash(seed + b * 11.3f + k * 3.1f + flickerPhase) - 0.5f) * 0.22f;
				float jy = (hash(seed + b * 5.7f + k * 4.7f + flickerPhase) - 0.5f) * 0.22f;
				Vec3 next = cur.add(fDir.scale(0.32)).add(right.scale(jx)).add(up.scale(jy));
				writeSegment(values, slot, segCursor++, frame.camera, cur, next, 0.022f - k * 0.004f, 0.85f - k * 0.18f);
				cur = next;
			}
		}
		values[slot.material] = segCursor;
		return false;
	}

	private static void writeSegment(float[] values, SlotWriter slot, int idx, Vec3 cam,
			Vec3 a, Vec3 b, float width, float intensity) {
		int sb = slot.segment + idx * 4;
		int eb = slot.segmentEnd + idx * 4;
		values[sb] = (float) (a.x - cam.x);
		values[sb + 1] = (float) (a.y - cam.y);
		values[sb + 2] = (float) (a.z - cam.z);
		values[sb + 3] = width;
		values[eb] = (float) (b.x - cam.x);
		values[eb + 1] = (float) (b.y - cam.y);
		values[eb + 2] = (float) (b.z - cam.z);
		values[eb + 3] = intensity;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		float progress = Math.clamp(ageSec / DURATION_SEC, 0f, 1f);
		float flash = (float) Math.pow(Math.clamp(1.0f - progress, 0f, 1f), 1.25);
		if (flash <= 0.015f) return;

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitImpactSparks(pose, vc, cameraPos, progress, flash));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc,
						(float) (end.x - cameraPos.x), (float) (end.y - cameraPos.y), (float) (end.z - cameraPos.z),
						0.85f, 0.65f, 0.96f, 1.00f, 0.50f * flash));
	}

	private void emitImpactSparks(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, float progress, float flash) {
		int sparks = 10;
		for (int i = 0; i < sparks; i++) {
			float h1 = hash(seed + i * 2.13f);
			float h2 = hash(seed + i * 4.91f);
			float ang = (i / (float) sparks) * (float) (Math.PI * 2.0) + h1 * 0.6f;
			float dist = (0.14f + 0.62f * h2) * Math.min(1.0f, progress * 2.4f);
			Vec3 p = end.add(right.scale(Math.cos(ang) * dist))
					.add(up.scale(Math.sin(ang) * dist))
					.add(dir.scale(-0.20f * progress));
			float s = 0.026f * (0.4f + 0.6f * flash);
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
					s, s, s,
					i % 3 == 0 ? 0.90f : 0.24f,
					i % 3 == 0 ? 1.00f : 0.92f,
					1.00f, flash * 0.80f);
		}
	}

	private static float hash(float x) {
		float s = (float) Math.sin(x * 12.9898f + 78.233f) * 43758.5453f;
		return s - (float) Math.floor(s);
	}
}
