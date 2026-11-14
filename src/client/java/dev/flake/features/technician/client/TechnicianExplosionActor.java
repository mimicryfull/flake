package dev.flake.features.technician.client;

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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Dramatic multi-stage explosion actor for Oreshnik missile impacts, UAV airbursts, and Kamikaze dive.
 * Pairs glowing fireball & billowing smoke mushroom voxels + ballistic shrapnel with shader mode 2 (`style = 25`).
 */
public final class TechnicianExplosionActor implements EffectActor {
	private static final float DURATION_SEC = 0.92f;
	private static final int SHRAPNEL_COUNT = 22;
	private static final int PLUME_COUNT = 34;

	private static final float[] S_DX = new float[SHRAPNEL_COUNT];
	private static final float[] S_DY = new float[SHRAPNEL_COUNT];
	private static final float[] S_DZ = new float[SHRAPNEL_COUNT];
	private static final float[] S_SPD = new float[SHRAPNEL_COUNT];

	private static final float[] P_ANG = new float[PLUME_COUNT];
	private static final float[] P_RAD = new float[PLUME_COUNT];
	private static final float[] P_UP = new float[PLUME_COUNT];
	private static final float[] P_SIZE = new float[PLUME_COUNT];

	static {
		for (int i = 0; i < SHRAPNEL_COUNT; i++) {
			float a = i * 2.3999632f + 0.25f;
			float up = 0.30f + 0.68f * hash(i * 3.31f);
			float h = (float) Math.sqrt(Math.max(0.05f, 1.0f - up * up * 0.42f));
			S_DX[i] = (float) Math.cos(a) * h;
			S_DY[i] = up;
			S_DZ[i] = (float) Math.sin(a) * h;
			S_SPD[i] = 5.4f + 6.4f * hash(i * 5.19f);
		}
		for (int i = 0; i < PLUME_COUNT; i++) {
			P_ANG[i] = i * 2.3999632f;
			P_RAD[i] = 0.35f + 1.85f * hash(i * 2.53f);
			P_UP[i] = 1.4f + 3.8f * hash(i * 4.71f);
			P_SIZE[i] = 0.065f + 0.075f * hash(i * 6.41f);
		}
	}

	private final int sourceId;
	private final Vec3 origin;
	private final float blastRadius;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;

	public TechnicianExplosionActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.aim().lengthSqr() > 1e-4 ? payload.aim() : payload.origin();
		this.blastRadius = Math.max(2.2f, payload.yaw());
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return false; // Allow multiple concurrent blasts cleanly
	}

	@Override public boolean expired() { return ageSec >= DURATION_SEC; }

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		float fade = 1.0f - Mth.clamp(ageSec / DURATION_SEC, 0.0f, 1.0f);
		float alpha = Math.min(1.0f, ageSec / 0.025f) * fade;

		float[] values = slot.values;
		values[slot.position] = (float) (origin.x - frame.camera.x);
		values[slot.position + 1] = (float) (origin.y - frame.camera.y);
		values[slot.position + 2] = (float) (origin.z - frame.camera.z);
		values[slot.position + 3] = blastRadius;

		values[slot.style] = alpha;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 25.0f;
		values[slot.style + 3] = 2.0f; // mode 2: Explosion
		return false;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		double dx = origin.x - cameraPos.x;
		double dy = origin.y - cameraPos.y;
		double dz = origin.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 76.0 * 76.0) return;

		float t = Mth.clamp(ageSec / DURATION_SEC, 0.0f, 1.0f);
		float scale = blastRadius / 3.2f;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);

		// Rising dark smoke & glowing fire plume voxels
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitSmokeAndFire(pose, vc, t, scale));

		// Bright white-gold core flash + radial ballistic shrapnel sparks
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitShrapnelAndCore(pose, vc, t, scale));

		poseStack.popPose();
	}

	private void emitSmokeAndFire(PoseStack.Pose pose, VertexConsumer vc, float t, float scale) {
		float smokeAlpha = (1.0f - t) * 0.78f;
		float expand = (float) Math.pow(t, 0.65f);
		for (int i = 0; i < PLUME_COUNT; i++) {
			float r = P_RAD[i] * expand * scale;
			float y = 0.25f + P_UP[i] * (0.20f + 0.80f * expand) * scale;
			float ang = P_ANG[i] + t * (1.2f - (i % 3) * 0.4f);
			float s = P_SIZE[i] * scale * (0.75f + 0.55f * (1.0f - t));
			boolean hot = (i % 2 == 0) && t < 0.62f;
			float rCol = hot ? 1.00f : 0.22f;
			float gCol = hot ? (0.36f + 0.42f * (1.0f - t)) : 0.18f;
			float bCol = hot ? 0.08f : 0.16f;
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) Math.cos(ang) * r, y, (float) Math.sin(ang) * r,
					s, s, s, rCol, gCol, bCol, smokeAlpha);
		}
	}

	private void emitShrapnelAndCore(PoseStack.Pose pose, VertexConsumer vc, float t, float scale) {
		float coreFade = (float) Math.exp(-ageSec * 8.5f);
		if (coreFade > 0.02f) {
			float coreR = (0.45f + 0.85f * t) * scale;
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, 0.45f, 0f, coreR * 1.4f, 1.00f, 0.84f, 0.36f, coreFade);
		}

		float shrapnelAlpha = (1.0f - t) * 0.95f;
		float g = 9.8f;
		for (int i = 0; i < SHRAPNEL_COUNT; i++) {
			float v = S_SPD[i] * scale;
			float sx = S_DX[i] * v * ageSec;
			float sy = 0.25f + S_DY[i] * v * ageSec - 0.5f * g * ageSec * ageSec;
			float sz = S_DZ[i] * v * ageSec;
			if (sy < -0.25f) continue;
			float s = 0.036f * (1.0f - t * 0.55f);
			WeaponVoxelRenderer.emitCube(pose, vc, sx, sy, sz, s, s, s,
					1.00f, 0.66f + 0.26f * (1.0f - t), 0.16f, shrapnelAlpha);
		}
	}

	private static float hash(float seed) {
		float s = (float) Math.sin(seed * 12.9898f) * 43758.5453f;
		return s - (float) Math.floor(s);
	}
}
