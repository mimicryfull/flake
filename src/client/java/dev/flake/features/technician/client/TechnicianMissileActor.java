package dev.flake.features.technician.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.technician.TechnicianRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 3D voxel "Oreshnik" homing missile with spinning X-fins, crimson seeker nosecone,
 * glowing plasma contrail segments and target ground lock indicator (`style = 25, mode = 0`).
 */
public final class TechnicianMissileActor implements EffectActor {
	private static final float MAX_FLIGHT_SEC = TechnicianRuntime.MISSILE_FLIGHT_TICKS / 20.0f;
	private static final float SPEED_PER_SEC = (float) (TechnicianRuntime.MISSILE_SPEED * 20.0);
	private static final int TRAIL_LIMIT = 14;

	private final int sourceId;
	private final VfxTimeline timeline = new VfxTimeline();
	private final Deque<Vec3> trail = new ArrayDeque<>(TRAIL_LIMIT + 2);
	private float ageSec;
	private float lastSimSec;
	private float lastTrailSec;
	private boolean stopped;

	private Vec3 pos;
	private Vec3 dir;
	private int targetId;

	public TechnicianMissileActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.pos = payload.origin();
		Vec3 rawDir = payload.aim();
		this.dir = rawDir.lengthSqr() > 1e-4 ? rawDir.normalize() : new Vec3(0.0, 0.2, 1.0).normalize();
		this.targetId = payload.targetId();
		this.trail.addLast(this.pos);
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId) return false;
		if (payload.phase() == CombatVfxPayload.Phase.TECHNICIAN_MISSILE_SYNC) {
			if (pos.distanceToSqr(payload.origin()) > 1.8) pos = payload.origin();
			else pos = pos.lerp(payload.origin(), 0.65);
			if (payload.aim().lengthSqr() > 1e-4) dir = payload.aim().normalize();
			targetId = payload.targetId();
			return true;
		}
		if (payload.phase() == CombatVfxPayload.Phase.TECHNICIAN_MISSILE_OFF) {
			stopped = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && (payload.phase() == CombatVfxPayload.Phase.TECHNICIAN_MISSILE_ON
				|| payload.phase() == CombatVfxPayload.Phase.TECHNICIAN_MISSILE_BOOM);
	}

	@Override
	public boolean expired() {
		return stopped || ageSec >= MAX_FLIGHT_SEC + 0.15f;
	}

	private void stepSimulation(ClientLevel level, float nowSec) {
		float dt = Mth.clamp(nowSec - lastSimSec, 0.0f, 0.08f);
		lastSimSec = nowSec;
		if (stopped || dt <= 0.0f) return;

		if (level != null && targetId >= 0 && nowSec >= 0.18f) {
			Entity target = level.getEntity(targetId);
			if (target instanceof LivingEntity living && living.isAlive()) {
				Vec3 to = living.position().add(0.0, living.getBbHeight() * 0.55, 0.0).subtract(pos);
				if (to.lengthSqr() > 1e-4) {
					double blend = Math.min(1.0, dt * 5.2);
					dir = dir.lerp(to.normalize(), blend).normalize();
				}
			}
		}

		pos = pos.add(dir.scale(SPEED_PER_SEC * dt));
		if (nowSec - lastTrailSec >= 0.025f) {
			lastTrailSec = nowSec;
			trail.addLast(pos);
			while (trail.size() > TRAIL_LIMIT) trail.removeFirst();
		}
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		stepSimulation(frame.client.level, ageSec);
		if (expired()) return true;

		float[] values = slot.values;
		values[slot.position] = (float) (pos.x - frame.camera.x);
		values[slot.position + 1] = (float) (pos.y - frame.camera.y);
		values[slot.position + 2] = (float) (pos.z - frame.camera.z);
		values[slot.position + 3] = 14.0f;

		values[slot.style] = Math.min(1.0f, ageSec / 0.05f);
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 25.0f;
		values[slot.style + 3] = 0.0f; // mode 0: missile contrail

		Vec3[] pts = trail.toArray(new Vec3[0]);
		int segCount = Math.max(0, pts.length - 1);
		int segCursor = 0;
		for (int i = 0; i < segCount && segCursor < 24; i++) {
			Vec3 a = pts[i];
			Vec3 b = (i == segCount - 1) ? pos : pts[i + 1];
			float fade = (i + 1) / (float) Math.max(segCount, 1);
			int s = slot.segment + segCursor * 4;
			int e = slot.segmentEnd + segCursor * 4;
			values[s] = (float) (a.x - frame.camera.x);
			values[s + 1] = (float) (a.y - frame.camera.y);
			values[s + 2] = (float) (a.z - frame.camera.z);
			values[s + 3] = fade;
			values[e] = (float) (b.x - frame.camera.x);
			values[e + 1] = (float) (b.y - frame.camera.y);
			values[e + 2] = (float) (b.z - frame.camera.z);
			values[e + 3] = 1.0f;
			segCursor++;
		}
		values[slot.material] = segCursor;

		Entity target = (frame.client.level != null && targetId >= 0) ? frame.client.level.getEntity(targetId) : null;
		if (target instanceof LivingEntity living && living.isAlive()) {
			values[slot.material + 1] = 1.0f;
			int te = slot.segmentEnd + 31 * 4;
			Vec3 tp = living.position();
			values[te] = (float) (tp.x - frame.camera.x);
			values[te + 1] = (float) (tp.y - frame.camera.y);
			values[te + 2] = (float) (tp.z - frame.camera.z);
			values[te + 3] = 1.0f;
		} else {
			values[slot.material + 1] = 0.0f;
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
		if (dx * dx + dy * dy + dz * dz > 72.0 * 72.0) return;

		float yawRad = (float) Math.atan2(dir.x, dir.z);
		double horiz = Math.hypot(dir.x, dir.z);
		float pitchRad = (float) Math.atan2(-dir.y, horiz);
		float rollSpin = ageSec * 9.5f;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);

		// Billowing rocket plume voxels behind tail
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitExhaustPlume(pose, vc));

		poseStack.mulPose(new Matrix4f().rotationY(yawRad).rotateX(pitchRad).rotateZ(rollSpin));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitMissileBody(pose, vc));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitMissileEmissives(pose, vc));

		poseStack.popPose();
	}

	private void emitMissileBody(PoseStack.Pose pose, VertexConsumer vc) {
		// Sleek "Oreshnik" fuselage along +Z
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, -0.10f, 0.075f, 0.075f, 0.28f, 0.20f, 0.23f, 0.28f, 1.0f);
		// White upper collar & hazard amber stripe
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0.22f, 0.070f, 0.070f, 0.09f, 0.90f, 0.92f, 0.95f, 1.0f);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0.06f, 0.079f, 0.079f, 0.035f, 1.00f, 0.56f, 0.10f, 1.0f);
		// Pointed crimson warhead cone
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0.34f, 0.056f, 0.056f, 0.055f, 0.90f, 0.16f, 0.10f, 1.0f);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0.42f, 0.036f, 0.036f, 0.045f, 1.00f, 0.30f, 0.14f, 1.0f);
		// X stabilizing tail fins
		for (int i = 0; i < 4; i++) {
			float sx = (i == 0 || i == 2) ? 0.12f : 0.015f;
			float sy = (i == 1 || i == 3) ? 0.12f : 0.015f;
			float ox = i == 0 ? 0.10f : (i == 2 ? -0.10f : 0f);
			float oy = i == 1 ? 0.10f : (i == 3 ? -0.10f : 0f);
			WeaponVoxelRenderer.emitCube(pose, vc, ox, oy, -0.30f, sx, sy, 0.09f, 0.96f, 0.42f, 0.12f, 0.98f);
		}
	}

	private void emitMissileEmissives(PoseStack.Pose pose, VertexConsumer vc) {
		float flicker = 0.85f + 0.15f * Mth.sin(ageSec * 36.0f);
		// Glowing nose seeker eye
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0.46f, 0.024f, 0.024f, 0.020f, 1.00f, 0.88f, 0.38f, 1.0f);
		// Hot rocket thruster exhaust flame cone at -Z
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, -0.44f, 0.055f, 0.055f, 0.11f, 1.00f, 0.84f, 0.30f, 0.98f * flicker);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, -0.58f, 0.036f, 0.036f, 0.12f, 1.00f, 0.44f, 0.10f, 0.85f * flicker);
		WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, 0f, -0.46f, 0.52f * flicker, 1.00f, 0.62f, 0.16f, 0.85f);
	}

	private void emitExhaustPlume(PoseStack.Pose pose, VertexConsumer vc) {
		Vec3[] pts = trail.toArray(new Vec3[0]);
		for (int i = 0; i < pts.length; i++) {
			Vec3 rel = pts[i].subtract(pos);
			float fade = (i + 1) / (float) Math.max(pts.length, 1);
			float s = 0.045f + (1.0f - fade) * 0.055f;
			WeaponVoxelRenderer.emitCube(pose, vc, (float) rel.x, (float) rel.y, (float) rel.z,
					s, s, s, 1.00f, 0.38f + 0.48f * fade, 0.10f + 0.25f * fade, fade * 0.68f);
		}
	}
}
