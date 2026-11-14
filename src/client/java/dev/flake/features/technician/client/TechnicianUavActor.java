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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Fixed-wing delta Tactical Strike UAV circling overhead with spinning pusher prop,
 * cyan-amber laser target designator beam, drop ordnance & terminal kamikaze dive (`style = 25, mode = 1`).
 */
public final class TechnicianUavActor implements EffectActor {
	private static final float MAX_FLIGHT_SEC = TechnicianRuntime.UAV_FLIGHT_TICKS / 20.0f;
	private static final float DIVE_START_SEC = TechnicianRuntime.UAV_DIVE_AGE / 20.0f;

	private final int sourceId;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float lastSimSec;
	private boolean stopped;

	private Vec3 pos;
	private Vec3 serverPos;
	private Vec3 lockPos;
	private float yawDeg;
	private float targetYawDeg;
	private int targetId;

	public TechnicianUavActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.pos = payload.origin();
		this.serverPos = payload.origin();
		this.lockPos = payload.aim();
		this.yawDeg = payload.yaw();
		this.targetYawDeg = payload.yaw();
		this.targetId = payload.targetId();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId) return false;
		if (payload.phase() == CombatVfxPayload.Phase.TECHNICIAN_UAV_SYNC) {
			this.serverPos = payload.origin();
			this.lockPos = payload.aim();
			this.targetYawDeg = payload.yaw();
			this.targetId = payload.targetId();
			return true;
		}
		if (payload.phase() == CombatVfxPayload.Phase.TECHNICIAN_UAV_OFF) {
			this.stopped = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && (payload.phase() == CombatVfxPayload.Phase.TECHNICIAN_UAV_ON
				|| payload.phase() == CombatVfxPayload.Phase.TECHNICIAN_UAV_BOOM);
	}

	@Override
	public boolean expired() {
		return stopped || ageSec >= MAX_FLIGHT_SEC + 0.15f;
	}

	private void stepSimulation(ClientLevel level, float nowSec) {
		float dt = Mth.clamp(nowSec - lastSimSec, 0.0f, 0.08f);
		lastSimSec = nowSec;
		if (stopped || dt <= 0.0f) return;

		pos = pos.lerp(serverPos, Math.min(1.0, dt * 10.0));
		float deltaYaw = Mth.wrapDegrees(targetYawDeg - yawDeg);
		yawDeg += deltaYaw * Math.min(1.0f, dt * 12.0f);

		if (level != null && targetId >= 0) {
			Entity target = level.getEntity(targetId);
			if (target instanceof LivingEntity living && living.isAlive()) {
				lockPos = lockPos.lerp(living.position(), Math.min(1.0, dt * 14.0));
			}
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
		values[slot.position + 3] = 16.0f;

		values[slot.style] = Math.min(1.0f, ageSec / 0.12f);
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 25.0f;
		values[slot.style + 3] = 1.0f; // mode 1: Strike UAV

		boolean diving = ageSec >= DIVE_START_SEC;
		float diveAlert = diving ? 1.0f : (float) Math.max(0.0, Math.sin(ageSec * 10.0)) * 0.35f;
		values[slot.material] = 1.0f;
		values[slot.material + 1] = diveAlert;

		values[slot.segment] = (float) (pos.x - frame.camera.x);
		values[slot.segment + 1] = (float) (pos.y - 0.14 - frame.camera.y);
		values[slot.segment + 2] = (float) (pos.z - frame.camera.z);
		values[slot.segment + 3] = 1.0f;

		values[slot.segmentEnd] = (float) (lockPos.x - frame.camera.x);
		values[slot.segmentEnd + 1] = (float) (lockPos.y - frame.camera.y);
		values[slot.segmentEnd + 2] = (float) (lockPos.z - frame.camera.z);
		values[slot.segmentEnd + 3] = 1.0f;
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
		if (dx * dx + dy * dy + dz * dz > 76.0 * 76.0) return;

		boolean diving = ageSec >= DIVE_START_SEC;
		float pitchRad = diving ? 0.65f : (float) Math.sin(ageSec * 4.0f) * 0.05f;
		float bankRoll = diving ? 0.0f : -0.34f;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);
		poseStack.mulPose(new Matrix4f().rotationY((float) Math.toRadians(-yawDeg)).rotateX(pitchRad).rotateZ(bankRoll));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitUavMesh(pose, vc));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitUavEmissives(pose, vc, diving));

		poseStack.popPose();
	}

	private void emitUavMesh(PoseStack.Pose pose, VertexConsumer vc) {
		float hullR = 0.24f, hullG = 0.28f, hullB = 0.34f;
		float trimR = 0.96f, trimG = 0.44f, trimB = 0.10f;
		float wingR = 0.36f, wingG = 0.41f, wingB = 0.48f;

		// Fuselage spine (+Z forward)
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.02f, 0.02f, 0.075f, 0.065f, 0.42f, hullR, hullG, hullB, 1.0f);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.02f, 0.48f, 0.052f, 0.045f, 0.12f, trimR, trimG, trimB, 1.0f);

		// Swept delta wings (3 stepped segments per wing for aerodynamic profile)
		for (int side = -1; side <= 1; side += 2) {
			WeaponVoxelRenderer.emitCube(pose, vc, side * 0.20f, 0.01f, 0.02f, 0.14f, 0.022f, 0.26f, wingR, wingG, wingB, 1.0f);
			WeaponVoxelRenderer.emitCube(pose, vc, side * 0.44f, 0.01f, -0.09f, 0.13f, 0.020f, 0.18f, wingR, wingG, wingB, 1.0f);
			WeaponVoxelRenderer.emitCube(pose, vc, side * 0.64f, 0.02f, -0.18f, 0.09f, 0.018f, 0.12f, trimR, trimG, trimB, 1.0f);
			// Twin vertical tail stabilizers
			WeaponVoxelRenderer.emitCube(pose, vc, side * 0.14f, 0.10f, -0.34f, 0.016f, 0.085f, 0.09f, hullR, hullG, hullB, 1.0f);
		}

		// Belly optical gimbal turret
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, -0.075f, 0.18f, 0.055f, 0.050f, 0.055f, 0.14f, 0.16f, 0.20f, 1.0f);

		// Visible micro-bombs mounted under wings before drops
		if (ageSec < TechnicianRuntime.UAV_STRIKE_1_AGE / 20.0f) {
			WeaponVoxelRenderer.emitCube(pose, vc, 0.24f, -0.05f, 0.04f, 0.032f, 0.032f, 0.12f, trimR, trimG, trimB, 1.0f);
		}
		if (ageSec < TechnicianRuntime.UAV_STRIKE_2_AGE / 20.0f) {
			WeaponVoxelRenderer.emitCube(pose, vc, -0.24f, -0.05f, 0.04f, 0.032f, 0.032f, 0.12f, trimR, trimG, trimB, 1.0f);
		}
	}

	private void emitUavEmissives(PoseStack.Pose pose, VertexConsumer vc, boolean diving) {
		// Spinning rear pusher propeller blades at tail (-Z)
		float propAngle = ageSec * 38.0f;
		float px = Mth.cos(propAngle) * 0.16f;
		float py = Mth.sin(propAngle) * 0.16f;
		WeaponVoxelRenderer.emitCube(pose, vc, px, py, -0.44f, 0.035f, 0.035f, 0.016f, 0.90f, 0.96f, 1.00f, 0.90f);
		WeaponVoxelRenderer.emitCube(pose, vc, -px, -py, -0.44f, 0.035f, 0.035f, 0.016f, 0.90f, 0.96f, 1.00f, 0.90f);

		// Rear engine afterburner glow (turns hot fiery crimson-orange during kamikaze dive)
		float er = diving ? 1.00f : 0.20f;
		float eg = diving ? 0.45f : 0.92f;
		float eb = diving ? 0.10f : 0.96f;
		WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, 0.02f, -0.46f, diving ? 0.65f : 0.34f, er, eg, eb, 0.85f);

		// Belly laser targeting lens glow
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, -0.095f, 0.21f, 0.025f, 0.025f, 0.025f, 0.24f, 0.98f, 0.94f, 1.0f);

		// Wingtip nav strobes (red left + cyan right blinking)
		float strobe = ((int) (ageSec * 8.0f) % 2 == 0) ? 1.0f : 0.25f;
		WeaponVoxelRenderer.emitCube(pose, vc, 0.72f, 0.03f, -0.20f, 0.025f, 0.025f, 0.025f, 1.00f, 0.18f, 0.12f, strobe);
		WeaponVoxelRenderer.emitCube(pose, vc, -0.72f, 0.03f, -0.20f, 0.025f, 0.025f, 0.025f, 0.16f, 1.00f, 0.88f, strobe);
	}
}
