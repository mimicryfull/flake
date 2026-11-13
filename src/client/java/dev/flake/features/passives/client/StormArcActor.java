package dev.flake.features.passives.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedActor;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Client actor for Storm Charge: renders jagged 3D cyan-electric voxel arcs and sparks
 * between the primary target and chained opponents (or ground) for 0.45s.
 */
public final class StormArcActor extends TimedActor {
	public static final float DURATION = 0.45f;
	private static final int BOLT_COUNT = 2;
	private static final int SEGMENTS = 9;

	private final Vec3 start;
	private final Vec3 end;
	private final float seed;
	private final VfxTimeline geometryTime = new VfxTimeline();
	private float ageSec;
	private float geoAgeSec;

	public StormArcActor(CombatVfxPayload payload) {
		super(payload);
		this.start = payload.origin();
		Vec3 rawEnd = payload.aim();
		this.end = rawEnd.distanceToSqr(start) > 0.01 ? rawEnd : start.add(0.0, -0.9, 0.0);
		this.seed = (float) ((sourceId * 1.37 + payload.targetId() * 2.91 + start.x * 0.73 + end.z * 1.19) % 100.0);
	}

	@Override
	public boolean expired() {
		return Math.max(ageSec, geoAgeSec) >= DURATION;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = age(frame);
		return ageSec >= DURATION;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		geoAgeSec = geometryTime.sample(System.nanoTime(), mc.isPaused());
		if (geoAgeSec >= DURATION) return;

		float progress = Mth.clamp(geoAgeSec / DURATION, 0.0f, 1.0f);
		float pop = Math.min(1.0f, geoAgeSec / 0.025f);
		float fade = (float) Math.pow(1.0f - progress, 1.4) * pop;
		if (fade <= 0.01f) return;

		Vec3 delta = end.subtract(start);
		double len = delta.length();
		if (len < 0.05) return;
		Vec3 dir = delta.scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
		else side = side.normalize();
		Vec3 ortho = dir.cross(side).normalize();

		// Flicker step gives crisp electric re-strikes over the 0.45s burst
		int flickerStep = (int) (geoAgeSec * 28.0f);

		for (int bolt = 0; bolt < BOLT_COUNT; bolt++) {
			float boltScale = bolt == 0 ? 1.0f : 0.72f;
			float jitterAmp = (float) Math.min(0.42, 0.14 + len * 0.09) * (bolt == 0 ? 1.0f : 1.35f);
			Vec3 prev = start;
			for (int s = 1; s <= SEGMENTS; s++) {
				float t = s / (float) SEGMENTS;
				Vec3 point;
				if (s == SEGMENTS) {
					point = end;
				} else {
					float env = (float) Math.sin(t * Math.PI);
					float h1 = hash(seed + bolt * 17.3f + s * 5.71f + flickerStep * 3.13f) * 2.0f - 1.0f;
					float h2 = hash(seed + bolt * 31.1f + s * 9.17f + flickerStep * 7.41f) * 2.0f - 1.0f;
					point = start.add(delta.scale(t))
							.add(side.scale(h1 * jitterAmp * env))
							.add(ortho.scale(h2 * jitterAmp * env));
				}
				emitSegment(poseStack, collector, cameraPos, prev, point,
						0.024f * boltScale * (0.55f + 0.45f * fade),
						fade * boltScale, bolt == 0);
				prev = point;
			}
		}

		// Endpoint glow nodes & crackling voxel sparks
		float sx = (float) (start.x - cameraPos.x);
		float sy = (float) (start.y - cameraPos.y);
		float sz = (float) (start.z - cameraPos.z);
		float ex = (float) (end.x - cameraPos.x);
		float ey = (float) (end.y - cameraPos.y);
		float ez = (float) (end.z - cameraPos.z);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) -> {
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, sx, sy, sz, 0.34f * (0.6f + 0.4f * fade), 0.42f, 0.88f, 1.0f, 0.55f * fade);
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, ex, ey, ez, 0.42f * (0.6f + 0.4f * fade), 0.65f, 0.94f, 1.0f, 0.70f * fade);
			emitSparks(pose, vc, sx, sy, sz, ex, ey, ez, progress, fade);
		});
	}

	private static void emitSegment(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos,
			Vec3 a, Vec3 b, float halfThick, float fade, boolean primary) {
		Vec3 diff = b.subtract(a);
		double segLen = diff.length();
		if (segLen < 1e-4) return;
		Vec3 dir = diff.scale(1.0 / segLen);
		float yaw = (float) Math.atan2(dir.x, dir.z);
		float horiz = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		float pitch = (float) Math.atan2(-dir.y, horiz);

		double mx = (a.x + b.x) * 0.5 - cameraPos.x;
		double my = (a.y + b.y) * 0.5 - cameraPos.y;
		double mz = (a.z + b.z) * 0.5 - cameraPos.z;
		float halfLen = (float) (segLen * 0.5) + halfThick * 0.6f;

		poseStack.pushPose();
		poseStack.translate(mx, my, mz);
		poseStack.mulPose(new Matrix4f().rotate(new Quaternionf().rotateY(yaw).rotateX(pitch)));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			if (primary) {
				WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0f,
						halfThick * 0.65f, halfThick * 0.65f, halfLen,
						0.88f, 0.97f, 1.0f, 0.95f * fade);
			} else {
				WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0f,
						halfThick * 0.75f, halfThick * 0.75f, halfLen,
						0.42f, 0.88f, 1.0f, 0.82f * fade);
			}
		});

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) ->
				WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0f,
						halfThick * 1.75f, halfThick * 1.75f, halfLen,
						0.18f, 0.56f, 1.0f, (primary ? 0.42f : 0.28f) * fade));

		poseStack.popPose();
	}

	private void emitSparks(PoseStack.Pose pose, VertexConsumer vc,
			float sx, float sy, float sz, float ex, float ey, float ez, float progress, float fade) {
		int sparkCount = 10;
		for (int i = 0; i < sparkCount; i++) {
			float h1 = hash(seed + i * 2.13f);
			float h2 = hash(seed + i * 4.79f);
			float h3 = hash(seed + i * 7.31f);
			boolean atEnd = i >= 4;
			float bx = atEnd ? ex : sx;
			float by = atEnd ? ey : sy;
			float bz = atEnd ? ez : sz;
			float ang = h1 * (float) (Math.PI * 2.0);
			float reach = (0.15f + 0.55f * progress) * (0.5f + 0.8f * h2);
			float px = bx + (float) Math.cos(ang) * reach;
			float py = by + (h3 - 0.35f) * reach * 1.2f;
			float pz = bz + (float) Math.sin(ang) * reach;
			float szCube = (0.018f + 0.016f * h2) * (0.4f + 0.6f * fade);
			WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, szCube, szCube, szCube,
					0.42f, 0.90f, 1.0f, 0.75f * fade);
		}
	}

	private static float hash(float x) {
		float s = (float) Math.sin(x * 127.1f + 311.7f) * 43758.5453f;
		return s - (float) Math.floor(s);
	}
}
