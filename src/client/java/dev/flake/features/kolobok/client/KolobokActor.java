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
 * Large smooth matte-yellow Kolobok matching the screenshot silhouette (oval black eyes,
 * wide thin U-smile with cheek creases) wielding dual full-auto AK-47 rifles with flying
 * bullet streams, muzzle flashes and brass shell casings (`style = 26`).
 */
public final class KolobokActor implements EffectActor {
	private static final float DURATION_SEC = KolobokRuntime.DURATION_TICKS / 20.0f;
	private static final float FADE_OUT_SEC = 0.25f;
	private static final float SPEED_PER_SEC = (float) (KolobokRuntime.MOVE_SPEED * 20.0);
	private static final float BODY_RADIUS = 0.56f;
	private static final float BODY_CENTER_Y = 0.56f;
	private static final float HAND_X = 0.68f;
	private static final float HAND_Y = 0.44f;

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

	private float lastLeftShotAt = -10.0f;
	private float lastRightShotAt = -10.0f;
	private boolean lastShotLeft = true;
	private int tracerSeed;

	public KolobokActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.pos = payload.origin();
		this.serverPos = payload.origin();
		this.impactPos = payload.aim();
		this.targetId = payload.targetId();
		if (payload.phase() == CombatVfxPayload.Phase.KOLOBOK_SHOT) {
			decodeShotYawAndSide(payload.yaw());
			spawnTracerAndCasing();
		} else {
			this.yaw = payload.yaw();
		}
	}

	private void decodeShotYawAndSide(float encodedYaw) {
		if (encodedYaw >= 360.0f) {
			this.yaw = encodedYaw - 720.0f;
			this.lastShotLeft = true;
			this.lastLeftShotAt = this.ageSec;
		} else {
			this.yaw = encodedYaw + 720.0f;
			this.lastShotLeft = false;
			this.lastRightShotAt = this.ageSec;
		}
	}

	private void spawnTracerAndCasing() {
		Vec3 muzzle = muzzleWorldPos(lastShotLeft);
		float s = ++tracerSeed;
		// Slight full-auto spread + 2 rapid visual rounds per burst for dense bullet stream
		for (int round = 0; round < 2; round++) {
			float sx = ((float) Math.sin(s * 7.3 + round * 3.1)) * 0.14f;
			float sy = ((float) Math.cos(s * 5.1 + round * 2.7)) * 0.10f;
			float sz = ((float) Math.sin(s * 11.7 + round * 1.9)) * 0.14f;
			Vec3 spreadHit = impactPos.add(sx, sy, sz);
			double dist = Math.max(1.5, muzzle.distanceTo(spreadHit));
			float flightSec = (float) Math.clamp(dist / 36.0, 0.08, 0.30);
			bullets.add(new BulletTracer(muzzle, spreadHit, ageSec + round * 0.045f, flightSec));
		}
		while (bullets.size() > 20) bullets.removeFirst();

		float sideSign = lastShotLeft ? 1.0f : -1.0f;
		float yawRad = (float) Math.toRadians(-yaw);
		Vector3f breechLocal = new Vector3f(sideSign * HAND_X, HAND_Y + 0.04f, 0.12f).rotateY(yawRad);
		Vector3f ejectVel = new Vector3f(sideSign * (1.8f + (s % 3) * 0.35f), 2.1f + (s % 2) * 0.3f, -0.4f).rotateY(yawRad);
		casings.add(new BrassCasing(pos.add(breechLocal.x, breechLocal.y, breechLocal.z),
				new Vec3(ejectVel.x, ejectVel.y, ejectVel.z), ageSec));
		while (casings.size() > 14) casings.removeFirst();
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
			decodeShotYawAndSide(payload.yaw());
			this.targetId = payload.targetId();
			this.impactPos = payload.aim();
			spawnTracerAndCasing();
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
			if (nowSec > b.bornAtSec + b.flightSec + 0.05f) it.remove();
		}
		for (Iterator<BrassCasing> it = casings.iterator(); it.hasNext();) {
			if (nowSec > it.next().bornAtSec + 0.55f) it.remove();
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

	private Vec3 muzzleWorldPos(boolean leftHand) {
		float sideX = leftHand ? HAND_X : -HAND_X;
		float yawRad = (float) Math.toRadians(-yaw);
		Vector3f local = new Vector3f(sideX, HAND_Y + 0.03f, 0.76f);
		local.rotateY(yawRad);
		return pos.add(local.x, local.y, local.z);
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		stepSimulation(frame.client.level, ageSec);
		if (expired()) return true;

		float alpha = opacity();
		float recentShotAt = Math.max(lastLeftShotAt, lastRightShotAt);
		float shotAge = recentShotAt >= -1.0f ? Math.max(0.0f, ageSec - recentShotAt) : 10.0f;

		float[] values = slot.values;
		values[slot.position] = (float) (pos.x - frame.camera.x);
		values[slot.position + 1] = (float) (pos.y - frame.camera.y);
		values[slot.position + 2] = (float) (pos.z - frame.camera.z);
		values[slot.position + 3] = 14.0f;

		values[slot.style] = alpha;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 26.0f;
		values[slot.style + 3] = shotAge;

		if (shotAge < 0.16f) {
			Vec3 muzzle = muzzleWorldPos(lastShotLeft);
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
		float waddleRoll = Mth.sin(ageSec * 8.0f) * 0.045f;

		float leftShotAge = lastLeftShotAt >= -1.0f ? Math.max(0.0f, ageSec - lastLeftShotAt) : 10.0f;
		float rightShotAge = lastRightShotAt >= -1.0f ? Math.max(0.0f, ageSec - lastRightShotAt) : 10.0f;
		float leftRecoil = leftShotAge < 0.13f ? Mth.sin((leftShotAge / 0.13f) * Mth.PI) * 0.10f : 0.0f;
		float rightRecoil = rightShotAge < 0.13f ? Mth.sin((rightShotAge / 0.13f) * Mth.PI) * 0.10f : 0.0f;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy + bob, (float) dz);

		// World-space flying brass casings & glowing full-auto bullet stream
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
				(pose, vc) -> emitKolobokAndRifles(pose, vc, alpha, leftRecoil, rightRecoil));

		if (leftShotAge < 0.12f || rightShotAge < 0.12f) {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
					(pose, vc) -> emitMuzzleFlashes(pose, vc, alpha, leftShotAge, rightShotAge, leftRecoil, rightRecoil));
		}

		poseStack.popPose();
	}

	private void emitKolobokAndRifles(PoseStack.Pose pose, VertexConsumer vc,
			float alpha, float leftRecoil, float rightRecoil) {
		// Large smooth golden-yellow Kolobok sphere matching screenshot (32x48 smooth mesh + SDF face)
		SmoothMeshRenderer.emitSphere(pose, vc, 0.0f, BODY_CENTER_Y, 0.0f, BODY_RADIUS, 32, 48,
				0.96f, 0.78f, 0.06f, alpha, true);

		// Smooth little yellow hands gripping the AK-47s on left and right sides
		SmoothMeshRenderer.emitSphere(pose, vc, HAND_X - 0.02f, HAND_Y - 0.02f, 0.10f - leftRecoil * 0.6f,
				0.095f, 10, 14, 0.96f, 0.76f, 0.06f, alpha, false);
		SmoothMeshRenderer.emitSphere(pose, vc, -HAND_X + 0.02f, HAND_Y - 0.02f, 0.10f - rightRecoil * 0.6f,
				0.095f, 10, 14, 0.96f, 0.76f, 0.06f, alpha, false);

		// Two large detailed smooth polygonal AK-47s
		emitSmoothAk47(pose, vc, HAND_X, HAND_Y, 0.04f - leftRecoil, leftRecoil * 1.4f, alpha);
		emitSmoothAk47(pose, vc, -HAND_X, HAND_Y, 0.04f - rightRecoil, rightRecoil * 1.4f, alpha);
	}

	private void emitSmoothAk47(PoseStack.Pose pose, VertexConsumer vc,
			float ox, float oy, float oz, float recoilPitch, float alpha) {
		float sr = 0.17f, sg = 0.19f, sb = 0.23f;
		float dr = 0.30f, dg = 0.34f, db = 0.40f;
		float wr = 0.56f, wg = 0.30f, wb = 0.13f;
		float mr = 0.13f, mg = 0.13f, mb = 0.16f;

		float lift = recoilPitch * 0.32f;

		// Tapered wooden AK-47 buttstock (-Z)
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy - 0.028f, oz - 0.30f, 0.025f, 0.060f,
				ox, oy + 0.006f, oz - 0.05f, 0.028f, 0.035f, wr, wg, wb, alpha);

		// Slanted wooden pistol grip
		SmoothMeshRenderer.emitLoftedY(pose, vc,
				ox, oy - 0.018f, oz + 0.005f, 0.020f, 0.026f,
				ox, oy - 0.128f, oz - 0.042f, 0.018f, 0.020f, wr * 0.85f, wg * 0.85f, wb * 0.85f, alpha);

		// Receiver body + beveled upper dust cover
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.005f, oz - 0.05f, 0.028f, 0.035f,
				ox, oy + 0.008f + lift * 0.3f, oz + 0.25f, 0.026f, 0.032f, sr, sg, sb, alpha);
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.042f, oz - 0.04f, 0.022f, 0.014f,
				ox, oy + 0.045f + lift * 0.3f, oz + 0.24f, 0.021f, 0.014f, dr, dg, db, alpha);

		// Smooth wooden forearm / handguard (+Z)
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.006f + lift * 0.3f, oz + 0.25f, 0.029f, 0.032f,
				ox, oy + 0.006f + lift * 0.55f, oz + 0.45f, 0.023f, 0.028f, wr, wg, wb, alpha);

		// 10-sided cylindrical barrel + gas tube + front sight + compensator
		SmoothMeshRenderer.emitCylinderZ(pose, vc, ox, oy + 0.010f + lift * 0.65f,
				oz + 0.25f, oz + 0.68f, 0.016f, 0.014f, 10, dr, dg, db, alpha);
		SmoothMeshRenderer.emitCylinderZ(pose, vc, ox, oy + 0.040f + lift * 0.5f,
				oz + 0.25f, oz + 0.49f, 0.014f, 0.013f, 8, sr, sg, sb, alpha);
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.034f + lift * 0.7f, oz + 0.58f, 0.009f, 0.026f,
				ox, oy + 0.034f + lift * 0.7f, oz + 0.61f, 0.008f, 0.026f, sr, sg, sb, alpha);
		SmoothMeshRenderer.emitCylinderZ(pose, vc, ox, oy + 0.010f + lift * 0.8f,
				oz + 0.68f, oz + 0.74f, 0.019f, 0.018f, 8, sr, sg, sb, alpha);

		// Signature curved AK-47 banana magazine ("ріжок")
		float mz = oz + 0.135f;
		float my = oy - 0.024f;
		for (int seg = 0; seg < 6; seg++) {
			float t0 = seg / 6.0f;
			float t1 = (seg + 1) / 6.0f;
			float y0 = my - t0 * 0.205f;
			float y1 = my - t1 * 0.205f;
			float z0 = mz + t0 * t0 * 0.145f;
			float z1 = mz + t1 * t1 * 0.145f;
			SmoothMeshRenderer.emitLoftedY(pose, vc,
					ox, y0, z0, 0.018f, 0.027f,
					ox, y1, z1, 0.017f, 0.025f, mr, mg, mb, alpha);
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
			Vec3 dir = head.subtract(tail);
			double len = dir.length();
			if (len < 1e-4) continue;

			// Emit elongated bright golden bullet slug + glowing tracer tail cubes along flight ray
			for (int k = 0; k <= 3; k++) {
				float u = k / 3.0f;
				Vec3 p = tail.lerp(head, u);
				float r = k == 3 ? 0.030f : 0.018f;
				float gCol = k == 3 ? 0.96f : 0.68f;
				float bCol = k == 3 ? 0.55f : 0.14f;
				WeaponVoxelRenderer.emitCube(pose, vc, (float) p.x, (float) p.y, (float) p.z,
						r, r, r, 1.00f, gCol, bCol, alpha * (0.45f + 0.55f * u));
			}
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, (float) head.x, (float) head.y, (float) head.z,
					0.16f, 1.00f, 0.85f, 0.25f, alpha * 0.90f);
		}
	}

	private void emitCasings(PoseStack.Pose pose, VertexConsumer vc, float alpha, float bob) {
		for (BrassCasing c : casings) {
			float age = ageSec - c.bornAtSec;
			if (age < 0.0f || age > 0.55f) continue;
			float x = (float) (c.origin.x + c.vel.x * age - pos.x);
			float y = (float) (c.origin.y + c.vel.y * age - 4.9f * age * age - (pos.y + bob));
			float z = (float) (c.origin.z + c.vel.z * age - pos.z);
			if (y < -0.1f) continue;
			float fade = 1.0f - age / 0.55f;
			SmoothMeshRenderer.emitCylinderZ(pose, vc, x, y, z - 0.018f, z + 0.018f,
					0.011f, 0.011f, 6, 0.94f, 0.74f, 0.22f, alpha * fade);
		}
	}

	private void emitMuzzleFlashes(PoseStack.Pose pose, VertexConsumer vc,
			float alpha, float leftAge, float rightAge, float leftRecoil, float rightRecoil) {
		if (leftAge < 0.12f) {
			float f = (1.0f - leftAge / 0.12f) * alpha;
			float z = 0.79f - leftRecoil;
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, HAND_X, HAND_Y + 0.02f, z, 0.42f * f, 1.00f, 0.80f, 0.22f, f);
		}
		if (rightAge < 0.12f) {
			float f = (1.0f - rightAge / 0.12f) * alpha;
			float z = 0.79f - rightRecoil;
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, -HAND_X, HAND_Y + 0.02f, z, 0.42f * f, 1.00f, 0.80f, 0.22f, f);
		}
	}
}
