package dev.flake.features.combatdrone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.combatdrone.CombatDroneRuntime;
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

/**
 * Small tracked voxel combat drone modeled after the artifact icon, paired with
 * orange-yellow deployment shockwave, antenna beacon halo and melee strike bursts (`style = 24`).
 */
public final class CombatDroneActor implements EffectActor {
	private static final float DURATION_SEC = CombatDroneRuntime.DURATION_TICKS / 20.0f;
	private static final float FADE_OUT_SEC = 0.25f;
	private static final float SPEED_PER_SEC = (float) (CombatDroneRuntime.MOVE_SPEED * 20.0);

	private final int sourceId;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float lastSimSec;
	private float despawnAtSec = DURATION_SEC;
	private float lastHitAtSec = -10.0f;

	private Vec3 pos;
	private Vec3 serverTargetPos;
	private Vec3 strikePos;
	private float yaw;
	private int targetId;

	public CombatDroneActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.pos = payload.origin();
		this.serverTargetPos = payload.origin();
		this.strikePos = payload.aim();
		this.yaw = payload.yaw();
		this.targetId = payload.targetId();
		if (payload.phase() == CombatVfxPayload.Phase.COMBAT_DRONE_HIT) {
			this.lastHitAtSec = 0.0f;
		}
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId) return false;
		if (payload.phase() == CombatVfxPayload.Phase.COMBAT_DRONE_HIT) {
			this.serverTargetPos = payload.origin();
			if (this.pos.distanceToSqr(payload.origin()) > 2.25) {
				this.pos = payload.origin();
			} else {
				this.pos = this.pos.lerp(payload.origin(), 0.60);
			}
			this.yaw = payload.yaw();
			this.targetId = payload.targetId();
			this.strikePos = payload.aim();
			this.lastHitAtSec = this.ageSec;
			return true;
		}
		if (payload.phase() == CombatVfxPayload.Phase.COMBAT_DRONE_OFF) {
			this.despawnAtSec = Math.min(this.despawnAtSec, this.ageSec);
			return true;
		}
		return false;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.COMBAT_DRONE_ON;
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
			if (horiz > CombatDroneRuntime.STANDOFF_RANGE) {
				double step = Math.min(SPEED_PER_SEC * dt, horiz - CombatDroneRuntime.STANDOFF_RANGE * 0.85);
				if (step > 0.0 && horiz > 1.0e-5) {
					Vec3 next = new Vec3(pos.x + (dx / horiz) * step, pos.y, pos.z + (dz / horiz) * step);
					pos = snapToGround(level, next, Math.max(pos.y, tPos.y));
				}
			}
			if (horiz > 1.0e-4) {
				yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			}
		} else {
			pos = pos.lerp(serverTargetPos, Math.min(1.0, dt * 6.0));
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

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		stepSimulation(frame.client.level, ageSec);
		if (expired()) return true;

		float alpha = opacity();
		float hitAge = lastHitAtSec >= -1.0f ? Math.max(0.0f, ageSec - lastHitAtSec) : 10.0f;

		float[] values = slot.values;
		values[slot.position] = (float) (pos.x - frame.camera.x);
		values[slot.position + 1] = (float) (pos.y - frame.camera.y);
		values[slot.position + 2] = (float) (pos.z - frame.camera.z);
		values[slot.position + 3] = 3.2f;

		values[slot.style] = alpha;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 24.0f;
		values[slot.style + 3] = hitAge;

		if (hitAge < 0.45f) {
			values[slot.segment] = 1.0f;
			values[slot.segmentEnd] = (float) (strikePos.x - frame.camera.x);
			values[slot.segmentEnd + 1] = (float) (strikePos.y - frame.camera.y);
			values[slot.segmentEnd + 2] = (float) (strikePos.z - frame.camera.z);
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

		float spawnBounce = ageSec < 0.25f ? (0.85f + 0.15f * Mth.sin((ageSec / 0.25f) * (float) Math.PI * 0.5f)) : 1.0f;
		float disappearScale = ageSec > despawnAtSec
				? Math.max(0.05f, 1.0f - Mth.clamp((ageSec - despawnAtSec) / FADE_OUT_SEC, 0.0f, 1.0f))
				: 1.0f;
		float scale = spawnBounce * disappearScale;

		float hitAge = lastHitAtSec >= -1.0f ? Math.max(0.0f, ageSec - lastHitAtSec) : 10.0f;
		float lungeZ = hitAge < 0.28f ? (float) Math.sin((hitAge / 0.28f) * Math.PI) * 0.40f : 0.0f;
		float lungePitch = hitAge < 0.28f ? -(float) Math.sin((hitAge / 0.28f) * Math.PI) * 0.24f : 0.0f;
		float treadJitter = (float) Math.sin(ageSec * 24.0f) * 0.008f;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy + treadJitter, (float) dz);

		// Drone local space: +Z faces target
		poseStack.pushPose();
		poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-yaw)));
		poseStack.translate(0.0f, 0.0f, lungeZ);
		poseStack.mulPose(new Matrix4f().rotationX(lungePitch));
		poseStack.scale(scale, scale, scale);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitDroneBody(pose, vc, alpha));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitDroneEmissives(pose, vc, alpha, hitAge));
		poseStack.popPose();

		// World-oriented orange-yellow spawn & strike spark voxels
		if (ageSec < 0.55f || hitAge < 0.36f) {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
					(pose, vc) -> emitSparkBurst(pose, vc, alpha, hitAge));
		}

		poseStack.popPose();
	}

	private void emitDroneBody(PoseStack.Pose pose, VertexConsumer vc, float alpha) {
		// Dark bronze/brown tank tracks matching robot_32.png
		float tr = 0.32f, tg = 0.17f, tb = 0.10f;
		float darkTrackR = 0.20f, darkTrackG = 0.10f, darkTrackB = 0.06f;
		float tanR = 0.85f, tanG = 0.77f, tanB = 0.56f;
		float trimR = 0.42f, trimG = 0.25f, trimB = 0.14f;
		float screenR = 0.16f, screenG = 0.09f, screenB = 0.05f;

		for (int side = -1; side <= 1; side += 2) {
			float tx = side * 0.165f;
			// Main tread block
			WeaponVoxelRenderer.emitCube(pose, vc, tx, 0.09f, 0.0f, 0.045f, 0.085f, 0.23f, tr, tg, tb, alpha);
			// Rounded front & back tread caps
			WeaponVoxelRenderer.emitCube(pose, vc, tx, 0.09f, 0.235f, 0.042f, 0.065f, 0.025f, darkTrackR, darkTrackG, darkTrackB, alpha);
			WeaponVoxelRenderer.emitCube(pose, vc, tx, 0.09f, -0.235f, 0.042f, 0.065f, 0.025f, darkTrackR, darkTrackG, darkTrackB, alpha);
			// Animated rotating orange-amber cleats on tracks
			float phase = (ageSec * 2.8f) % 1.0f;
			for (int c = 0; c < 4; c++) {
				float cz = -0.17f + ((c / 4.0f + phase) % 1.0f) * 0.34f;
				WeaponVoxelRenderer.emitCube(pose, vc, tx + side * 0.048f, 0.09f, cz,
						0.008f, 0.055f, 0.018f, 0.96f, 0.56f, 0.14f, alpha * 0.85f);
			}
		}

		// Undercarriage axle & rear plate
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.09f, 0.0f, 0.135f, 0.045f, 0.12f, trimR, trimG, trimB, alpha);

		// Square tan/beige main hull + brown armored trim edges
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.265f, 0.0f, 0.145f, 0.13f, 0.155f, tanR, tanG, tanB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.398f, 0.0f, 0.152f, 0.014f, 0.162f, trimR, trimG, trimB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.138f, 0.0f, 0.152f, 0.014f, 0.162f, trimR, trimG, trimB, alpha);

		// Side bolts / plates
		for (int side = -1; side <= 1; side += 2) {
			WeaponVoxelRenderer.emitCube(pose, vc, side * 0.148f, 0.265f, 0.0f,
					0.010f, 0.075f, 0.095f, trimR, trimG, trimB, alpha);
		}

		// Recessed dark visor panel on front (+Z)
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.275f, 0.156f, 0.122f, 0.068f, 0.010f, screenR, screenG, screenB, alpha);

		// Curved copper antenna stem rising from top-back-right to top-center bulb
		WeaponVoxelRenderer.emitCube(pose, vc, -0.02f, 0.445f, -0.03f, 0.018f, 0.048f, 0.018f, tr, tg, tb, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, -0.005f, 0.515f, 0.00f, 0.016f, 0.045f, 0.016f, trimR, trimG, trimB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.015f, 0.565f, 0.025f, 0.014f, 0.022f, 0.024f, tr, tg, tb, alpha);
	}

	private void emitDroneEmissives(PoseStack.Pose pose, VertexConsumer vc, float alpha, float hitAge) {
		float pulse = 0.80f + 0.20f * Mth.sin(ageSec * 12.0f);
		float attackBoost = hitAge < 0.28f ? 1.0f : 0.0f;

		// Three glowing orange-yellow front sensor eyes like robot_32.png
		for (int eye = -1; eye <= 1; eye++) {
			float ex = eye * 0.068f;
			WeaponVoxelRenderer.emitCube(pose, vc, ex, 0.275f, 0.172f,
					0.022f, 0.022f, 0.010f, 1.00f, 0.62f + 0.28f * attackBoost, 0.14f, alpha * pulse);
		}

		// Glowing orange-yellow antenna tip bulb + soft halo sprite
		WeaponVoxelRenderer.emitCube(pose, vc, 0.015f, 0.605f, 0.030f,
				0.036f, 0.036f, 0.036f, 1.00f, 0.72f, 0.18f, alpha * pulse);
		WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0.015f, 0.605f, 0.030f,
				0.32f + 0.14f * attackBoost, 1.00f, 0.64f, 0.14f, alpha * (0.55f + 0.30f * pulse));

		// Side orange thruster vents when moving / lunging
		for (int side = -1; side <= 1; side += 2) {
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, side * 0.17f, 0.18f, -0.15f,
					0.20f + 0.12f * attackBoost, 1.00f, 0.48f, 0.10f, alpha * 0.48f);
		}
	}

	private void emitSparkBurst(PoseStack.Pose pose, VertexConsumer vc, float alpha, float hitAge) {
		if (ageSec < 0.55f) {
			float t = Mth.clamp(ageSec / 0.55f, 0.0f, 1.0f);
			float a = alpha * (1.0f - t) * 0.90f;
			for (int i = 0; i < 12; i++) {
				float ang = i * 0.5236f + ageSec * 3.5f;
				float r = 0.25f + t * 1.45f;
				float y = 0.16f + (float) Math.sin(t * Math.PI) * (0.28f + (i % 3) * 0.14f);
				float s = 0.034f * (1.0f - t * 0.5f);
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) Math.cos(ang) * r, y, (float) Math.sin(ang) * r,
						s, s, s, 1.00f, 0.66f + (i % 2) * 0.26f, 0.14f, a);
			}
		}

		if (hitAge < 0.36f) {
			float t = Mth.clamp(hitAge / 0.36f, 0.0f, 1.0f);
			float a = alpha * (1.0f - t) * 0.95f;
			Vec3 rel = strikePos.subtract(pos);
			float hx = (float) rel.x;
			float hy = (float) rel.y;
			float hz = (float) rel.z;
			for (int i = 0; i < 10; i++) {
				float ang = i * 0.6283f + hitAge * 8.0f;
				float spread = 0.12f + t * 0.85f;
				float dy = ((i % 3) - 1) * 0.28f * t;
				float s = 0.032f * (1.0f - t * 0.55f);
				WeaponVoxelRenderer.emitCube(pose, vc,
						hx + (float) Math.cos(ang) * spread, hy + dy, hz + (float) Math.sin(ang) * spread,
						s, s, s, 1.00f, 0.78f, 0.20f, a);
			}
		}
	}
}
