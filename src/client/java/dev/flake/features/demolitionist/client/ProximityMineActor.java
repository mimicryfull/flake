package dev.flake.features.demolitionist.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.demolitionist.DemolitionistRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** World-space 3D voxel proximity mine with locking arming clamps, blinking beacon, and trigger perimeter ticks. */
public final class ProximityMineActor implements EffectActor {
	private static final float ARM_SEC = DemolitionistRuntime.MINE_ARM_TICKS / 20.0f;
	private static final float MAX_LIFE_SEC = DemolitionistRuntime.MINE_LIFE_TICKS / 20.0f;
	private static final float TRIGGER_RADIUS = (float) DemolitionistRuntime.MINE_TRIGGER_RADIUS;
	private static final float DISARM_FADE_SEC = 0.25f;
	private static final int RING_SEGMENTS = 16;

	private final int sourceId;
	private final Vec3 origin;
	private final float yaw;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float disarmAt = Float.NaN;

	public ProximityMineActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.origin();
		this.yaw = payload.yaw();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId || payload.phase() != CombatVfxPayload.Phase.MINE_DISARM) return false;
		if (Float.isNaN(disarmAt)) disarmAt = ageSec;
		return true;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && (payload.phase() == CombatVfxPayload.Phase.MINE_PLACE
				|| payload.phase() == CombatVfxPayload.Phase.MINE_DETONATE);
	}

	@Override
	public boolean expired() {
		return ageSec >= MAX_LIFE_SEC || (!Float.isNaN(disarmAt) && ageSec >= disarmAt + DISARM_FADE_SEC);
	}

	@Override public boolean write(RenderFrame frame, SlotWriter slot) { return expired(); }

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		double dx = origin.x - cameraPos.x;
		double dy = origin.y - cameraPos.y;
		double dz = origin.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float spawnScale = Math.clamp(ageSec / 0.14f, 0f, 1f);
		spawnScale = 1.0f - (1.0f - spawnScale) * (1.0f - spawnScale);
		float disarmFade = Float.isNaN(disarmAt) ? 1.0f : Math.clamp(1.0f - (ageSec - disarmAt) / DISARM_FADE_SEC, 0f, 1f);
		float tailFade = Math.clamp((MAX_LIFE_SEC - ageSec) / 0.4f, 0f, 1f);
		float opacity = spawnScale * disarmFade * tailFade;
		if (opacity <= 0.01f) return;

		float armT = Math.clamp(ageSec / ARM_SEC, 0f, 1f);
		boolean armed = ageSec >= ARM_SEC;
		float pulse = armed ? doubleBlink(ageSec - ARM_SEC) : (0.35f + 0.65f * armT * armT);

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);
		poseStack.mulPose(new Matrix4f().rotationY(-yaw));

		float scale = (0.75f + 0.25f * spawnScale) * (0.3f + 0.7f * disarmFade);
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitMineBody(pose, vc, scale, armT, armed, pulse, opacity));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitPerimeterAndSparks(pose, vc, armT, armed, pulse, opacity));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitBeaconGlow(pose, vc, armed, pulse, opacity));

		poseStack.popPose();
	}

	private void emitMineBody(PoseStack.Pose pose, VertexConsumer vc,
			float scale, float armT, boolean armed, float pulse, float opacity) {
		// Octagonal dark-iron base plate (2 crossed flat slabs)
		float bw = 0.27f * scale, bt = 0.11f * scale, bh = 0.032f * scale;
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, bh, 0f, bw, bh, bt, 0.14f, 0.15f, 0.18f, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, bh, 0f, bt, bh, bw, 0.14f, 0.15f, 0.18f, opacity);

		// Steel pressure collar + brass rim
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.072f * scale, 0f, 0.18f * scale, 0.022f * scale, 0.18f * scale,
				0.28f, 0.29f, 0.32f, opacity);

		// Four arming clamp jaws sliding inward until locked at armT == 1
		float clampOffset = 0.22f - 0.065f * smoothstep(armT);
		float clampLift = 0.07f + 0.045f * (1.0f - armT);
		for (int i = 0; i < 4; i++) {
			float sx = (i == 0 || i == 2) ? 1f : -1f;
			float sz = (i < 2) ? 1f : -1f;
			float jx = sx * clampOffset * scale;
			float jz = sz * clampOffset * scale;
			WeaponVoxelRenderer.emitCube(pose, vc, jx, clampLift * scale, jz,
					0.042f * scale, 0.030f * scale, 0.042f * scale,
					armed ? 0.72f : 0.90f, armed ? 0.36f : 0.62f, 0.14f, opacity);
		}

		// Central blinking crimson-amber detonator core
		float coreR = armed ? (0.45f + 0.55f * pulse) : (0.55f + 0.40f * armT);
		float coreG = armed ? (0.08f + 0.32f * pulse) : (0.28f + 0.35f * armT);
		float coreB = armed ? (0.04f + 0.10f * pulse) : 0.06f;
		float coreSize = (0.068f + 0.018f * pulse) * scale;
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.115f * scale, 0f, coreSize, coreSize * 0.65f, coreSize,
				coreR, coreG, coreB, opacity);
	}

	private void emitPerimeterAndSparks(PoseStack.Pose pose, VertexConsumer vc,
			float armT, boolean armed, float pulse, float opacity) {
		// Low tactical trigger perimeter markers at TRIGGER_RADIUS = 1.7 blocks
		float radius = armed ? TRIGGER_RADIUS : TRIGGER_RADIUS + (1.0f - armT) * 0.45f;
		float ringAlpha = opacity * (armed ? (0.24f + 0.24f * pulse) : 0.38f * armT);
		float segAngleStep = (float) (Math.PI * 2.0 / RING_SEGMENTS);
		float rot = armed ? 0.0f : (1.0f - armT) * 1.1f;

		for (int i = 0; i < RING_SEGMENTS; i++) {
			float ang = i * segAngleStep + rot;
			float x = (float) Math.cos(ang) * radius;
			float z = (float) Math.sin(ang) * radius;
			boolean major = (i % 4 == 0);
			float s = major ? 0.048f : 0.032f;
			float r = armed ? (major ? 1.0f : 0.92f) : 1.0f;
			float g = armed ? (major ? 0.34f + 0.25f * pulse : 0.22f) : 0.68f;
			float b = armed ? 0.08f : 0.18f;
			WeaponVoxelRenderer.emitCube(pose, vc, x, 0.024f, z, s, 0.014f, s, r, g, b, ringAlpha);
		}

		// Subtle rising heat micro-sparks on armed pulse
		if (armed && pulse > 0.15f) {
			for (int k = 0; k < 4; k++) {
				float phase = ((ageSec * 1.6f + k * 0.25f) % 1.0f);
				float ang = k * 1.57f + ageSec * 1.4f;
				float rx = (float) Math.cos(ang) * (0.08f + phase * 0.10f);
				float rz = (float) Math.sin(ang) * (0.08f + phase * 0.10f);
				float ry = 0.16f + phase * 0.34f;
				float s = 0.020f * (1.0f - 0.6f * phase);
				WeaponVoxelRenderer.emitCube(pose, vc, rx, ry, rz, s, s, s,
						1.0f, 0.42f + 0.35f * (1f - phase), 0.10f, opacity * (1.0f - phase) * 0.72f);
			}
		}
	}

	private void emitBeaconGlow(PoseStack.Pose pose, VertexConsumer vc, boolean armed, float pulse, float opacity) {
		float glowRadius = armed ? (0.34f + 0.24f * pulse) : 0.28f;
		float alpha = opacity * (armed ? (0.28f + 0.46f * pulse) : 0.32f);
		WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, 0.14f, 0f, glowRadius,
				1.0f, armed ? (0.24f + 0.32f * pulse) : 0.56f, 0.08f, alpha);
	}

	/** Sharp industrial double-blink hazard beacon (~1.3s cycle). */
	private static float doubleBlink(float t) {
		float cycle = t % 1.30f;
		if (cycle < 0.14f) return 1.0f - cycle / 0.14f * 0.35f;
		if (cycle > 0.22f && cycle < 0.38f) return 1.0f - (cycle - 0.22f) / 0.16f;
		return 0.08f;
	}

	private static float smoothstep(float x) { return x * x * (3.0f - 2.0f * x); }
}
