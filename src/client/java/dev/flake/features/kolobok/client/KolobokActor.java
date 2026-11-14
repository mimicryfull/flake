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
 * Smooth non-voxel polygonal Kolobok armed with two AK-47 assault rifles.
 * Renders a glossy curved yellow sphere with SDF oval black eyes & ultra-wide narrow smile,
 * paired with polygonal curved magazines, cylindrical barrels, muzzle flashes & tracers (`style = 26`).
 */
public final class KolobokActor implements EffectActor {
	private static final float DURATION_SEC = KolobokRuntime.DURATION_TICKS / 20.0f;
	private static final float FADE_OUT_SEC = 0.25f;
	private static final float SPEED_PER_SEC = (float) (KolobokRuntime.MOVE_SPEED * 20.0);

	private final int sourceId;
	private final VfxTimeline timeline = new VfxTimeline();
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

	public KolobokActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.pos = payload.origin();
		this.serverPos = payload.origin();
		this.impactPos = payload.aim();
		this.targetId = payload.targetId();
		if (payload.phase() == CombatVfxPayload.Phase.KOLOBOK_SHOT) {
			decodeShotYawAndSide(payload.yaw());
			this.lastLeftShotAt = 0.0f;
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
		if (level == null || dt <= 0.0f || nowSec >= despawnAtSec) return;

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
		float sideX = leftHand ? 0.44f : -0.44f;
		float yawRad = (float) Math.toRadians(-yaw);
		Vector3f local = new Vector3f(sideX, 0.31f, 0.64f);
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

		if (shotAge < 0.25f) {
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

		float bob = Math.abs(Mth.sin(ageSec * 9.5f)) * 0.055f;
		float waddleRoll = Mth.sin(ageSec * 9.5f) * 0.09f;

		float leftShotAge = lastLeftShotAt >= -1.0f ? Math.max(0.0f, ageSec - lastLeftShotAt) : 10.0f;
		float rightShotAge = lastRightShotAt >= -1.0f ? Math.max(0.0f, ageSec - lastRightShotAt) : 10.0f;
		float leftRecoil = leftShotAge < 0.18f ? Mth.sin((leftShotAge / 0.18f) * Mth.PI) * 0.11f : 0.0f;
		float rightRecoil = rightShotAge < 0.18f ? Mth.sin((rightShotAge / 0.18f) * Mth.PI) * 0.11f : 0.0f;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy + bob, (float) dz);
		poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-yaw)).rotateZ(waddleRoll));
		poseStack.scale(scale, scale, scale);

		collector.submitCustomGeometry(poseStack, SmoothMeshRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitKolobokAndRifles(pose, vc, alpha, leftRecoil, rightRecoil));

		if (leftShotAge < 0.14f || rightShotAge < 0.14f) {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
					(pose, vc) -> emitMuzzleFlashes(pose, vc, alpha, leftShotAge, rightShotAge, leftRecoil, rightRecoil));
		}

		poseStack.popPose();
	}

	private void emitKolobokAndRifles(PoseStack.Pose pose, VertexConsumer vc,
			float alpha, float leftRecoil, float rightRecoil) {
		// Smooth golden-yellow Kolobok body sphere (26x40 segments, analytical normal + oval eyes & wide narrow smile)
		SmoothMeshRenderer.emitSphere(pose, vc, 0.0f, 0.36f, 0.0f, 0.34f, 26, 40,
				1.00f, 0.85f, 0.14f, alpha, true);

		// Two small smooth yellow hands holding the rifles symmetrically
		SmoothMeshRenderer.emitSphere(pose, vc, 0.42f, 0.27f, 0.10f - leftRecoil * 0.6f, 0.078f, 10, 14,
				1.00f, 0.82f, 0.12f, alpha, false);
		SmoothMeshRenderer.emitSphere(pose, vc, -0.42f, 0.27f, 0.10f - rightRecoil * 0.6f, 0.078f, 10, 14,
				1.00f, 0.82f, 0.12f, alpha, false);

		// Two detailed smooth non-voxel AK-47 assault rifles (left hand & right hand)
		emitSmoothAk47(pose, vc, 0.44f, 0.29f, 0.04f - leftRecoil, leftRecoil * 1.4f, alpha);
		emitSmoothAk47(pose, vc, -0.44f, 0.29f, 0.04f - rightRecoil, rightRecoil * 1.4f, alpha);
	}

	private void emitSmoothAk47(PoseStack.Pose pose, VertexConsumer vc,
			float ox, float oy, float oz, float recoilPitch, float alpha) {
		// Palette: dark stamped gunmetal steel, warm walnut AK wood, dark curved magazine
		float sr = 0.18f, sg = 0.20f, sb = 0.24f;
		float dr = 0.29f, dg = 0.33f, db = 0.39f;
		float wr = 0.56f, wg = 0.30f, wb = 0.13f;
		float mr = 0.14f, mg = 0.14f, mb = 0.17f;

		float lift = recoilPitch * 0.30f;

		// Tapered wooden AK-47 buttstock (-Z)
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy - 0.025f, oz - 0.26f, 0.022f, 0.052f,
				ox, oy + 0.005f, oz - 0.04f, 0.024f, 0.030f, wr, wg, wb, alpha);

		// Slanted wooden pistol grip
		SmoothMeshRenderer.emitLoftedY(pose, vc,
				ox, oy - 0.015f, oz + 0.005f, 0.018f, 0.022f,
				ox, oy - 0.110f, oz - 0.035f, 0.016f, 0.018f, wr * 0.85f, wg * 0.85f, wb * 0.85f, alpha);

		// Receiver body + sloped upper dust cover
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.004f, oz - 0.04f, 0.024f, 0.030f,
				ox, oy + 0.006f + lift * 0.3f, oz + 0.22f, 0.023f, 0.028f, sr, sg, sb, alpha);
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.036f, oz - 0.03f, 0.019f, 0.012f,
				ox, oy + 0.038f + lift * 0.3f, oz + 0.21f, 0.018f, 0.012f, dr, dg, db, alpha);

		// Smooth wooden forearm / handguard (+Z)
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.005f + lift * 0.3f, oz + 0.22f, 0.025f, 0.028f,
				ox, oy + 0.005f + lift * 0.55f, oz + 0.39f, 0.020f, 0.024f, wr, wg, wb, alpha);

		// Smooth 10-sided cylindrical rifle barrel + upper gas tube + slanted muzzle brake
		SmoothMeshRenderer.emitCylinderZ(pose, vc, ox, oy + 0.008f + lift * 0.65f,
				oz + 0.22f, oz + 0.59f, 0.014f, 0.012f, 10, dr, dg, db, alpha);
		SmoothMeshRenderer.emitCylinderZ(pose, vc, ox, oy + 0.034f + lift * 0.5f,
				oz + 0.22f, oz + 0.43f, 0.012f, 0.011f, 8, sr, sg, sb, alpha);
		// Front sight post + muzzle compensator tip
		SmoothMeshRenderer.emitTaperedBoxZ(pose, vc,
				ox, oy + 0.028f + lift * 0.7f, oz + 0.51f, 0.008f, 0.022f,
				ox, oy + 0.028f + lift * 0.7f, oz + 0.54f, 0.007f, 0.022f, sr, sg, sb, alpha);
		SmoothMeshRenderer.emitCylinderZ(pose, vc, ox, oy + 0.008f + lift * 0.8f,
				oz + 0.59f, oz + 0.64f, 0.016f, 0.015f, 8, sr, sg, sb, alpha);

		// Signature curved AK-47 banana magazine ("ріжок") made of 6 smooth curved segments
		float mz = oz + 0.12f;
		float my = oy - 0.020f;
		for (int seg = 0; seg < 6; seg++) {
			float t0 = seg / 6.0f;
			float t1 = (seg + 1) / 6.0f;
			float y0 = my - t0 * 0.175f;
			float y1 = my - t1 * 0.175f;
			float z0 = mz + t0 * t0 * 0.125f;
			float z1 = mz + t1 * t1 * 0.125f;
			SmoothMeshRenderer.emitLoftedY(pose, vc,
					ox, y0, z0, 0.016f, 0.024f,
					ox, y1, z1, 0.015f, 0.022f, mr, mg, mb, alpha);
		}
	}

	private void emitMuzzleFlashes(PoseStack.Pose pose, VertexConsumer vc,
			float alpha, float leftAge, float rightAge, float leftRecoil, float rightRecoil) {
		if (leftAge < 0.14f) {
			float f = (1.0f - leftAge / 0.14f) * alpha;
			float z = 0.67f - leftRecoil;
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0.44f, 0.31f, z, 0.36f * f, 1.00f, 0.78f, 0.22f, f);
		}
		if (rightAge < 0.14f) {
			float f = (1.0f - rightAge / 0.14f) * alpha;
			float z = 0.67f - rightRecoil;
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, -0.44f, 0.31f, z, 0.36f * f, 1.00f, 0.78f, 0.22f, f);
		}
	}
}
