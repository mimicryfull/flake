package dev.flake.features.kolobok.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.SmoothMeshRenderer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.kolobok.KolobokRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Large smooth matte-yellow Kolobok wielding a 50-AK-47 war halo around his body
 * while keeping his oval eyes and wide thin smile crystal clear, unleashing dense full-auto bullet storms.
 */
public final class KolobokActor implements EffectActor {
	public static final int AK_COUNT = 50;
	private static final float DURATION_SEC = KolobokRuntime.DURATION_TICKS / 20.0f;
	private static final float FADE_OUT_SEC = 0.25f;
	private static final float SPEED_PER_SEC = (float) (KolobokRuntime.MOVE_SPEED * 20.0);
	private static final float BODY_RADIUS = 0.56f;
	private static final float BODY_CENTER_Y = 0.56f;

	private static final float[] AK_X = new float[AK_COUNT];
	private static final float[] AK_Y = new float[AK_COUNT];
	private static final float[] AK_Z = new float[AK_COUNT];
	private static final float[] AK_FAN_YAW = new float[AK_COUNT];
	private static final float[] AK_FAN_PITCH = new float[AK_COUNT];

	static {
		// 3 concentric halo arcs (14 + 18 + 18 = 50 AK-47s) framing Kolobok without blocking his front face
		int[] ringCounts = {14, 18, 18};
		float[] ringRadii = {0.72f, 0.96f, 1.20f};
		float[] ringZ = {0.06f, -0.06f, -0.18f};
		int idx = 0;
		for (int ring = 0; ring < ringCounts.length; ring++) {
			int count = ringCounts[ring];
			float rad = ringRadii[ring];
			float baseZ = ringZ[ring];
			for (int k = 0; k < count && idx < AK_COUNT; k++) {
				// Arc from -118 deg (lower-left) over top (+90 deg) down to -62 deg? Wait:
				// Angle measured from top (+Y = 0): sweep [-132 deg .. +132 deg] cleanly around sides & crown!
				float t = count == 1 ? 0.5f : k / (float) (count - 1);
				float angDeg = -126.0f + t * 252.0f + (ring == 1 ? 5.0f : (ring == 2 ? -4.0f : 0.0f));
				float ang = (float) Math.toRadians(angDeg);
				float x = Mth.sin(ang) * rad;
				float y = BODY_CENTER_Y + Mth.cos(ang) * (rad * 0.88f);
				// Keep clear of terrain under y=0.18
				if (y < 0.22f) y = 0.22f;
				AK_X[idx] = x;
				AK_Y[idx] = y;
				AK_Z[idx] = baseZ;
				// Slight inward convergence toward target + slight outward radial fan
				AK_FAN_YAW[idx] = -x * 0.08f;
				AK_FAN_PITCH[idx] = (y - BODY_CENTER_Y) * 0.04f;
				idx++;
			}
		}
	}

	private static final class BulletTracer {
		final Vec3 start;
		final Vec3 end;
		final float bornAtSec;
		final float flightSec;

		BulletTracer(Vec3 start, Vec3 end, float bornAtSec, float flightSec) {
			this.start = start;
			this.end = end;
			this.bornAtSec = bornAtSec;
			this.flightSec = flightSec;
		}
	}

	private static final class BrassCasing {
		final Vec3 origin;
		final Vec3 vel;
		final float bornAtSec;

		BrassCasing(Vec3 origin, Vec3 vel, float bornAtSec) {
			this.origin = origin;
			this.vel = vel;
			this.bornAtSec = bornAtSec;
		}
	}

	private final int sourceId;
	private final VfxTimeline timeline = new VfxTimeline();
	private final float[] lastFiredAt = new float[AK_COUNT];
	private final List<BulletTracer> bullets = new ArrayList<>();
	private final List<BrassCasing> casings = new ArrayList<>();
	private float ageSec;
	private float lastSimSec;
	private float despawnAtSec = DURATION_SEC;

	private Vec3 pos;
	private Vec3 serverPos;
	private Vec3 impactPos;
	private float yaw;
	private int targetId;

	private float lastShotAt = -10.0f;
	private int muzzleBarrelIdx;
	private int burstCounter;

	public KolobokActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.pos = payload.origin();
		this.serverPos = payload.origin();
		this.impactPos = payload.aim();
		this.targetId = payload.targetId();
		java.util.Arrays.fill(lastFiredAt, -10.0f);
		if (payload.phase() == CombatVfxPayload.Phase.KOLOBOK_SHOT) {
			decodeShotYaw(payload.yaw());
			triggerFiftyAkVolley();
		} else {
			this.yaw = payload.yaw();
		}
	}

	private void decodeShotYaw(float encodedYaw) {
		this.yaw = encodedYaw >= 360.0f ? (encodedYaw - 720.0f) : (encodedYaw + 720.0f);
		this.lastShotAt = this.ageSec;
	}

	private void triggerFiftyAkVolley() {
		int step = ++burstCounter;
		// Fire 6 different AKs per 3-tick packet with staggered timing for continuous bullet wall
		for (int n = 0; n < 6; n++) {
			int ak = (step * 7 + n * 9) % AK_COUNT;
			float fireTime = ageSec + n * 0.024f;
			lastFiredAt[ak] = fireTime;
			if (n == 0) muzzleBarrelIdx = ak;

			Vec3 muzzle = akMuzzleWorldPos(ak);
			float spreadX = ((float) Math.sin(step * 5.3 + n * 2.9)) * 0.24f;
			float spreadY = ((float) Math.cos(step * 3.7 + n * 4.1)) * 0.18f;
			float spreadZ = ((float) Math.sin(step * 8.1 + n * 1.7)) * 0.24f;
			Vec3 hitPt = impactPos.add(spreadX, spreadY, spreadZ);
			double dist = Math.max(1.5, muzzle.distanceTo(hitPt));
			float flightSec = (float) Math.clamp(dist / 34.0, 0.09, 0.30);
			bullets.add(new BulletTracer(muzzle, hitPt, fireTime, flightSec));

			if (n % 2 == 0) {
				float sideSign = AK_X[ak] >= 0.0f ? 1.0f : -1.0f;
				float yawRad = (float) Math.toRadians(-yaw);
				Vector3f breech = new Vector3f(AK_X[ak], AK_Y[ak] + 0.03f, AK_Z[ak] + 0.10f).rotateY(yawRad);
				Vector3f vel = new Vector3f(sideSign * (1.8f + (n % 3) * 0.4f), 2.0f + (step % 2) * 0.3f, -0.35f).rotateY(yawRad);
				casings.add(new BrassCasing(pos.add(breech.x, breech.y, breech.z),
						new Vec3(vel.x, vel.y, vel.z), fireTime));
			}
		}
		while (bullets.size() > 60) bullets.removeFirst();
		while (casings.size() > 28) casings.removeFirst();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId) return false;
		if (payload.phase() == CombatVfxPayload.Phase.KOLOBOK_SHOT) {
			this.serverPos = payload.origin();
			if (this.pos.distanceToSqr(payload.origin()) > 2.25) {
				this.pos = payload.origin();
			} else {
				this.pos = this.pos.lerp(payload.origin(), 0.60);
			}
			decodeShotYaw(payload.yaw());
			this.targetId = payload.targetId();
			this.impactPos = payload.aim();
			triggerFiftyAkVolley();
			return true;
		}
		if (payload.phase() == CombatVfxPayload.Phase.KOLOBOK_OFF) {
			this.despawnAtSec = Math.min(this.despawnAtSec, this.ageSec);
			return true;
		}
		return false;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.KOLOBOK_ON;
	}

	@Override
	public boolean expired() {
		return ageSec >= despawnAtSec + FADE_OUT_SEC;
	}

	private void stepSimulation(ClientLevel level, float nowSec) {
		float dt = Mth.clamp(nowSec - lastSimSec, 0.0f, 0.1f);
		lastSimSec = nowSec;
		if (level != null && dt > 0.0f && nowSec < despawnAtSec) {
			Entity target = targetId >= 0 ? level.getEntity(targetId) : null;
			if (target instanceof LivingEntity living && living.isAlive()) {
				Vec3 tPos = living.position();
				double dx = tPos.x - pos.x;
				double dz = tPos.z - pos.z;
				double horiz = Math.hypot(dx, dz);
				if (horiz > KolobokRuntime.PREFERRED_RANGE) {
					double step = Math.min(SPEED_PER_SEC * dt, horiz - KolobokRuntime.PREFERRED_RANGE * 0.85);
					if (step > 0.0 && horiz > 1.0e-5) {
						Vec3 next = new Vec3(pos.x + (dx / horiz) * step, pos.y, pos.z + (dz / horiz) * step);
						pos = snapToGround(level, next, Math.max(pos.y, tPos.y));
					}
				}
				if (horiz > 1.0e-4) {
					yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				}
			} else {
				pos = pos.lerp(serverPos, Math.min(1.0, dt * 6.0));
			}
		}

		for (Iterator<BulletTracer> it = bullets.iterator(); it.hasNext();) {
			BulletTracer b = it.next();
			if (nowSec > b.bornAtSec + b.flightSec + 0.04f) it.remove();
		}
		for (Iterator<BrassCasing> it = casings.iterator(); it.hasNext();) {
			if (nowSec > it.next().bornAtSec + 0.50f) it.remove();
		}
	}

	private static Vec3 snapToGround(ClientLevel level, Vec3 pos, double referenceY) {
		Vec3 top = new Vec3(pos.x, referenceY + 1.5, pos.z);
		Vec3 bottom = new Vec3(pos.x, referenceY - 3.5, pos.z);
		var hit = level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : new Vec3(pos.x, referenceY, pos.z);
	}

	private float opacity() {
		float in = Math.min(1.0f, ageSec / 0.14f);
		float out = ageSec <= despawnAtSec ? 1.0f : 1.0f - Mth.clamp((ageSec - despawnAtSec) / FADE_OUT_SEC, 0.0f, 1.0f);
		return in * out;
	}

	private Vec3 akMuzzleWorldPos(int ak) {
		float yawRad = (float) Math.toRadians(-yaw);
		Vector3f local = new Vector3f(AK_X[ak], AK_Y[ak] + 0.02f, AK_Z[ak] + 0.62f).rotateY(yawRad);
		return pos.add(local.x, local.y, local.z);
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		stepSimulation(frame.client.level, ageSec);
		if (expired()) return true;

		float alpha = opacity();
		float shotAge = lastShotAt >= -1.0f ? Math.max(0.0f, ageSec - lastShotAt) : 10.0f;

		float[] values = slot.values;
		values[slot.position] = (float) (pos.x - frame.camera.x);
		values[slot.position + 1] = (float) (pos.y - frame.camera.y);
		values[slot.position + 2] = (float) (pos.z - frame.camera.z);
		values[slot.position + 3] = 15.0f;

		values[slot.style] = alpha;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 26.0f;
		values[slot.style + 3] = shotAge;

		if (shotAge < 0.16f) {
			Vec3 muzzle = akMuzzleWorldPos(muzzleBarrelIdx);
			values[slot.segment] = (float) (muzzle.x - frame.camera.x);
			values[slot.segment + 1] = (float) (muzzle.y - frame.camera.y);
			values[slot.segment + 2] = (float) (muzzle.z - frame.camera.z);
			values[slot.segment + 3] = 1.0f;

			values[slot.segmentEnd] = (float) (impactPos.x - frame.camera.x);
			values[slot.segmentEnd + 1] = (float) (impactPos.y - frame.camera.y);
			values[slot.segmentEnd + 2] = (float) (impactPos.z - frame.camera.z);
			values[slot.segmentEnd + 3] = 1.0f;
		} else {
			values[slot.segment] = 0.0f;
		}
		return false;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		stepSimulation(mc.level, ageSec);
		if (expired()) return;

		double dx = pos.x - cameraPos.x;
		double dy = pos.y - cameraPos.y;
		double dz = pos.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float alpha = opacity();
		if (alpha <= 0.01f) return;

		float spawnScale = ageSec < 0.22f ? (0.80f + 0.20f * Mth.sin((ageSec / 0.22f) * Mth.HALF_PI)) : 1.0f;
		float despawnScale = ageSec > despawnAtSec
				? Math.max(0.05f, 1.0f - Mth.clamp((ageSec - despawnAtSec) / FADE_OUT_SEC, 0.0f, 1.0f))
				: 1.0f;
		float scale = spawnScale * despawnScale;

		float bob = Math.abs(Mth.sin(ageSec * 8.0f)) * 0.032f;
		float waddleRoll = Mth.sin(ageSec * 8.0f) * 0.042f;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy + bob, (float) dz);

		if (!casings.isEmpty()) {
			collector.submitCustomGeometry(poseStack, SmoothMeshRenderer.CORE_RENDER_TYPE,
					(pose, vc) -> emitCasings(pose, vc, alpha, bob));
		}
		if (!bullets.isEmpty()) {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
					(pose, vc) -> emitBulletStream(pose, vc, alpha, bob));
		}

		poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-yaw)).rotateZ(waddleRoll));
		poseStack.scale(scale, scale, scale);

		collector.submitCustomGeometry(poseStack, SmoothMeshRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitKolobokAndFiftyAks(pose, vc, alpha));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitMuzzleFlashes(pose, vc, alpha));

		poseStack.popPose();
	}

	private void emitKolobokAndFiftyAks(PoseStack.Pose pose, VertexConsumer vc, float alpha) {
		// Smooth golden-yellow Kolobok sphere unobstructed in front
		SmoothMeshRenderer.emitSphere(pose, vc, 0.0f, BODY_CENTER_Y, 0.0f, BODY_RADIUS, 30, 44,
				0.96f, 0.78f, 0.06f, alpha, true);

		// Two small golden hands gripping the lowest side rifles
		SmoothMeshRenderer.emitSphere(pose, vc, AK_X[0] - 0.04f, AK_Y[0] - 0.02f, AK_Z[0] + 0.06f,
				0.088f, 8, 12, 0.96f, 0.76f, 0.06f, alpha, false);
		SmoothMeshRenderer.emitSphere(pose, vc, AK_X[13] + 0.04f, AK_Y[13] - 0.02f, AK_Z[13] + 0.06f,
				0.088f, 8, 12, 0.96f, 0.76f, 0.06f, alpha, false);

		// 50 sleek polygonal AK-47s arrayed around sides and halo
		for (int i = 0; i < AK_COUNT; i++) {
			float sAge = ageSec - lastFiredAt[i];
			float recoil = (sAge >= 0.0f && sAge < 0.11f) ? Mth.sin((sAge / 0.11f) * Mth.PI) * 0.09f : 0.0f;
			emitCompactSmoothAk47(pose, vc, AK_X[i], AK_Y[i], AK_Z[i] - recoil,
					AK_FAN_YAW[i], AK_FAN_PITCH[i], alpha);
		}
	}

	private void emitCompactSmoothAk47(PoseStack.Pose pose, VertexConsumer vc,
			float ox, float oy, float oz, float fanYaw, float fanPitch, float alpha) {
		float sr = 0.17f, sg = 0.19f, sb = 0.23f;
		float dr = 0.30f, dg = 0.34f, db = 0.40f;
		float wr = 0.56f, wg = 0.30f, wb = 0.13f;
		float mr = 0.13f, mg = 0.13f, mb = 0.16f;

		float dX = fanYaw * 0.45f;
		float dY = fanPitch * 0.35f;

		// Tapered wooden buttstock (-Z)
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox - dX * 0.3f, oy - 0.022f - dY * 0.2f, oz - 0.24f, 0.020f, 0.046f,
				ox, oy + 0.005f, oz - 0.04f, 0.022f, 0.028f, wr, wg, wb, alpha);

		// Slanted pistol grip
		SmoothMeshRenderer.emitLoftedY(pose, vc,
				ox, oy - 0.015f, oz + 0.004f, 0.016f, 0.020f,
				ox, oy - 0.102f, oz - 0.032f, 0.014f, 0.016f, wr * 0.85f, wg * 0.85f, wb * 0.85f, alpha);

		// Steel receiver + beveled dust cover
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.005f, oz - 0.04f, 0.022f, 0.028f,
				ox + dX * 0.2f, oy + 0.006f + dY * 0.2f, oz + 0.20f, 0.021f, 0.026f, sr, sg, sb, alpha);
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.034f, oz - 0.03f, 0.017f, 0.011f,
				ox + dX * 0.2f, oy + 0.035f + dY * 0.2f, oz + 0.19f, 0.016f, 0.011f, dr, dg, db, alpha);

		// Smooth wooden handguard
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox + dX * 0.2f, oy + 0.005f + dY * 0.2f, oz + 0.20f, 0.023f, 0.025f,
				ox + dX * 0.4f, oy + 0.005f + dY * 0.4f, oz + 0.36f, 0.018f, 0.022f, wr, wg, wb, alpha);

		// 6-sided sleek cylindrical barrel + upper gas tube
		SmoothMeshRenderer.emitCylinderZ(pose, vc, ox + dX * 0.55f, oy + 0.008f + dY * 0.55f,
				oz + 0.20f, oz + 0.58f, 0.013f, 0.011f, 6, dr, dg, db, alpha);
		SmoothMeshRenderer.emitCylinderZ(pose, vc, ox + dX * 0.4f, oy + 0.032f + dY * 0.4f,
				oz + 0.20f, oz + 0.40f, 0.011f, 0.010f, 5, sr, sg, sb, alpha);

		// Curved banana magazine ("ріжок", 3 curved segments)
		float mz = oz + 0.105f;
		float my = oy - 0.020f;
		for (int seg = 0; seg < 3; seg++) {
			float t0 = seg / 3.0f;
			float t1 = (seg + 1) / 3.0f;
			float y0 = my - t0 * 0.165f;
			float y1 = my - t1 * 0.165f;
			float z0 = mz + t0 * t0 * 0.115f;
			float z1 = mz + t1 * t1 * 0.115f;
			SmoothMeshRenderer.emitLoftedY(pose, vc,
					ox, y0, z0, 0.014f, 0.022f,
					ox, y1, z1, 0.013f, 0.020f, mr, mg, mb, alpha);
		}
	}

	private void emitBulletStream(PoseStack.Pose pose, VertexConsumer vc, float alpha, float bob) {
		for (BulletTracer b : bullets) {
			float age = ageSec - b.bornAtSec;
			if (age < 0.0f || age > b.flightSec) continue;
			float tHead = Mth.clamp(age / b.flightSec, 0.0f, 1.0f);
			float tTail = Mth.clamp((age - 0.055f) / b.flightSec, 0.0f, 1.0f);
			Vec3 head = b.start.lerp(b.end, tHead).subtract(pos.x, pos.y + bob, pos.z);
			Vec3 tail = b.start.lerp(b.end, tTail).subtract(pos.x, pos.y + bob, pos.z);
			for (int k = 0; k <= 2; k++) {
				float u = k / 2.0f;
				Vec3 p = tail.lerp(head, u);
				float r = k == 2 ? 0.026f : 0.016f;
				WeaponVoxelRenderer.emitCube(pose, vc, (float) p.x, (float) p.y, (float) p.z,
						r, r, r, 1.00f, k == 2 ? 0.96f : 0.68f, k == 2 ? 0.55f : 0.14f, alpha * (0.45f + 0.55f * u));
			}
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, (float) head.x, (float) head.y, (float) head.z,
					0.14f, 1.00f, 0.85f, 0.25f, alpha * 0.85f);
		}
	}

	private void emitCasings(PoseStack.Pose pose, VertexConsumer vc, float alpha, float bob) {
		for (BrassCasing c : casings) {
			float age = ageSec - c.bornAtSec;
			if (age < 0.0f || age > 0.50f) continue;
			float x = (float) (c.origin.x + c.vel.x * age - pos.x);
			float y = (float) (c.origin.y + c.vel.y * age - 4.9f * age * age - (pos.y + bob));
			float z = (float) (c.origin.z + c.vel.z * age - pos.z);
			if (y < -0.1f) continue;
			float fade = 1.0f - age / 0.50f;
			SmoothMeshRenderer.emitCylinderZ(pose, vc, x, y, z - 0.016f, z + 0.016f,
					0.010f, 0.010f, 5, 0.94f, 0.74f, 0.22f, alpha * fade);
		}
	}

	private void emitMuzzleFlashes(PoseStack.Pose pose, VertexConsumer vc, float alpha) {
		for (int i = 0; i < AK_COUNT; i++) {
			float sAge = ageSec - lastFiredAt[i];
			if (sAge >= 0.0f && sAge < 0.095f) {
				float f = (1.0f - sAge / 0.095f) * alpha;
				WeaponVoxelRenderer.emitGlowSprite(pose, vc,
						AK_X[i], AK_Y[i] + 0.01f, AK_Z[i] + 0.62f,
						0.32f * f, 1.00f, 0.78f, 0.22f, f);
			}
		}
	}
}
