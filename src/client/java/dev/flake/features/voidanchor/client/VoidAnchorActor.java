package dev.flake.features.voidanchor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.voidanchor.VoidAnchorRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 3D dark-violet rotating void anchor paired with gravitational singularity & suction distortion (`style = 22`). */
public final class VoidAnchorActor implements EffectActor {
	private static final float DELAY_SEC = VoidAnchorRuntime.DELAY_TICKS / 20.0f;
	private static final float PULL_DURATION_SEC = 0.65f;
	private static final float PULL_RADIUS = (float) VoidAnchorRuntime.PULL_RADIUS;

	private final int sourceId;
	private final Vec3 origin;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float pullAt = Float.NaN;

	public VoidAnchorActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.origin();
		if (payload.phase() == CombatVfxPayload.Phase.VOID_ANCHOR_PULL) {
			this.pullAt = 0.0f;
		}
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.VOID_ANCHOR_PULL) {
			if (Float.isNaN(pullAt)) pullAt = ageSec;
			return true;
		}
		return false;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.VOID_ANCHOR_CAST;
	}

	@Override
	public boolean expired() {
		float pullStart = Float.isNaN(pullAt) ? DELAY_SEC : pullAt;
		return ageSec >= pullStart + PULL_DURATION_SEC;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		float pullStart = Float.isNaN(pullAt) ? DELAY_SEC : pullAt;
		boolean pulling = ageSec >= pullStart;
		float pullProgress = pulling ? Mth.clamp((ageSec - pullStart) / PULL_DURATION_SEC, 0.0f, 1.0f) : 0.0f;
		float opacity = pulling ? (1.0f - smoothstep(0.45f, 1.0f, pullProgress)) : Math.min(1.0f, ageSec / 0.12f);
		float pullBurst = pulling ? (1.0f - pullProgress) : 0.0f;

		float[] values = slot.values;
		values[slot.position] = (float) (origin.x - frame.camera.x);
		values[slot.position + 1] = (float) (origin.y - frame.camera.y);
		values[slot.position + 2] = (float) (origin.z - frame.camera.z);
		values[slot.position + 3] = PULL_RADIUS;

		values[slot.style] = opacity;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 22.0f;
		values[slot.style + 3] = pullBurst;
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
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float pullStart = Float.isNaN(pullAt) ? DELAY_SEC : pullAt;
		boolean pulling = ageSec >= pullStart;
		float pullProgress = pulling ? Mth.clamp((ageSec - pullStart) / PULL_DURATION_SEC, 0.0f, 1.0f) : 0.0f;
		float chargeT = pulling ? 1.0f : Mth.clamp(ageSec / DELAY_SEC, 0.0f, 1.0f);
		float opacity = pulling ? (1.0f - smoothstep(0.45f, 1.0f, pullProgress)) : Math.min(1.0f, ageSec / 0.12f);
		if (opacity <= 0.01f) return;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);

		// Rotating 3D dark-violet anchor
		float spin = ageSec * (1.8f + 4.2f * chargeT * chargeT);
		float hover = pulling ? (0.14f * (1.0f - pullProgress)) : (0.58f - 0.36f * smoothstep(0.55f, 1.0f, chargeT));
		poseStack.pushPose();
		poseStack.translate(0.0f, hover, 0.0f);
		poseStack.mulPose(new Matrix4f().rotationY(spin));
		float anchorScale = pulling ? (1.15f - 0.55f * pullProgress) : (0.85f + 0.25f * chargeT);
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitAnchorMesh(pose, vc, anchorScale, chargeT, opacity));
		poseStack.popPose();

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitRingsAndMotes(pose, vc, chargeT, pulling, pullProgress, opacity));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitGlows(pose, vc, hover, chargeT, pulling, pullProgress, opacity));

		poseStack.popPose();
	}

	private void emitAnchorMesh(PoseStack.Pose pose, VertexConsumer vc, float scale, float chargeT, float opacity) {
		float dr = 0.26f + 0.20f * chargeT, dg = 0.06f, db = 0.44f + 0.30f * chargeT;
		float br = 0.68f, bg = 0.26f, bb = 0.98f;

		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.38f * scale, 0f,
				0.065f * scale, 0.46f * scale, 0.065f * scale, dr, dg, db, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.90f * scale, 0f,
				0.13f * scale, 0.045f * scale, 0.055f * scale, br, bg, bb, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 1.02f * scale, 0f,
				0.10f * scale, 0.045f * scale, 0.055f * scale, br, bg, bb, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.64f * scale, 0f,
				0.34f * scale, 0.055f * scale, 0.055f * scale, dr * 1.15f, dg, db * 1.1f, opacity);
		for (int side = -1; side <= 1; side += 2) {
			WeaponVoxelRenderer.emitCube(pose, vc, side * 0.22f * scale, 0.04f * scale, 0f,
					0.18f * scale, 0.06f * scale, 0.06f * scale, dr, dg, db, opacity);
			WeaponVoxelRenderer.emitCube(pose, vc, side * 0.40f * scale, 0.16f * scale, 0f,
					0.08f * scale, 0.12f * scale, 0.065f * scale, br, bg, bb, opacity);
			WeaponVoxelRenderer.emitCube(pose, vc, side * 0.46f * scale, 0.30f * scale, 0f,
					0.055f * scale, 0.08f * scale, 0.055f * scale, 0.90f, 0.56f, 1.0f, opacity);
		}
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, -0.10f * scale, 0f,
				0.085f * scale, 0.085f * scale, 0.085f * scale, br, bg, bb, opacity);
	}

	private void emitRingsAndMotes(PoseStack.Pose pose, VertexConsumer vc,
			float chargeT, boolean pulling, float pullProgress, float opacity) {
		if (!pulling) {
			for (int i = 0; i < 16; i++) {
				float phase = ((ageSec * 0.95f + i * 0.17f) % 1.0f);
				float r = PULL_RADIUS * (1.0f - phase * 0.90f);
				float ang = i * 2.39996f + ageSec * 1.8f;
				float s = 0.036f * (1.0f - phase * 0.45f);
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) Math.cos(ang) * r, 0.12f + phase * 0.42f, (float) Math.sin(ang) * r,
						s, s, s, 0.72f, 0.28f, 1.00f, opacity * (1.0f - phase) * 0.76f);
			}
			return;
		}

		// Two crisp staggered inward pull rings
		for (int ring = 0; ring < 2; ring++) {
			float localT = Mth.clamp((pullProgress - ring * 0.16f) / 0.76f, 0.0f, 1.0f);
			if (localT <= 0.0f || localT >= 1.0f) continue;
			float radius = Mth.lerp(localT * localT, PULL_RADIUS, 0.22f);
			float ringAlpha = opacity * (1.0f - localT * 0.35f) * 0.85f;
			float y = 0.12f + ring * 0.32f * (1.0f - localT);
			for (int i = 0; i < 18; i++) {
				float ang = (i / 18.0f) * (float) (Math.PI * 2.0) + ageSec * (3.6f - ring * 0.9f);
				float s = 0.044f * (1.0f - 0.35f * localT);
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) Math.cos(ang) * radius, y, (float) Math.sin(ang) * radius,
						s, s * 0.6f, s, 0.78f, 0.32f, 1.00f, ringAlpha);
			}
		}
	}

	private void emitGlows(PoseStack.Pose pose, VertexConsumer vc,
			float hover, float chargeT, boolean pulling, float pullProgress, float opacity) {
		float coreRadius = pulling ? (1.15f * (1.0f - 0.55f * pullProgress)) : (0.56f + 0.38f * chargeT);
		float coreAlpha = pulling ? (0.85f * (1.0f - pullProgress)) : (0.48f + 0.30f * chargeT);
		WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, hover + 0.25f, 0f, coreRadius,
				0.68f, 0.26f, 1.00f, coreAlpha * opacity);
	}

	private static float smoothstep(float low, float high, float x) {
		float t = Mth.clamp((x - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
