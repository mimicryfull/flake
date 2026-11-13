package dev.flake.features.huntermark.client;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 3D crimson rotating target reticle above the marked target for the 0.8s burst + highlight pulses. */
public final class HunterMarkActor implements EffectActor {
	private static final float BURST_DURATION_SEC = 0.80f;
	private static final float HIGHLIGHT_DURATION_SEC = 0.50f;

	private final int sourceId;
	private final int targetId;
	private final Vec3 fallbackOrigin;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float durationSec;

	public HunterMarkActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.targetId = payload.targetId();
		this.fallbackOrigin = payload.origin();
		this.durationSec = payload.phase() == CombatVfxPayload.Phase.HUNTER_HIGHLIGHT
				? HIGHLIGHT_DURATION_SEC : BURST_DURATION_SEC;
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.HUNTER_HIGHLIGHT) {
			timeline.reset();
			ageSec = 0.0f;
			durationSec = Math.max(durationSec, HIGHLIGHT_DURATION_SEC);
			return true;
		}
		return false;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.HUNTER_MARK;
	}

	@Override
	public boolean expired() {
		return ageSec >= durationSec;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		return expired();
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired() || mc.level == null) return;

		Entity target = targetId >= 0 ? mc.level.getEntity(targetId) : null;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 anchor = target != null
				? target.getPosition(partial).add(0.0, target.getBbHeight() + 0.42, 0.0)
				: fallbackOrigin;

		double dx = anchor.x - cameraPos.x;
		double dy = anchor.y - cameraPos.y;
		double dz = anchor.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float progress = Mth.clamp(ageSec / durationSec, 0.0f, 1.0f);
		float pop = Math.min(1.0f, ageSec / 0.10f);
		float fade = 1.0f - smoothstep(0.55f, 1.0f, progress);
		float opacity = pop * fade;
		if (opacity <= 0.01f) return;

		float bob = (float) Math.sin(ageSec * 6.5f) * 0.06f;
		float spin = ageSec * 3.4f;
		float scale = 1.18f - 0.18f * pop;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy + bob, (float) dz);
		poseStack.mulPose(new Matrix4f().rotationY(spin));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitReticle(pose, vc, scale, opacity));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, 0.08f, 0f, 0.65f * scale,
						1.00f, 0.16f, 0.20f, 0.62f * opacity));

		poseStack.popPose();
	}

	private void emitReticle(PoseStack.Pose pose, VertexConsumer vc, float scale, float opacity) {
		float r = 0.34f * scale;
		float bar = 0.12f * scale;
		float thick = 0.028f * scale;

		// Four crimson corner brackets forming a rotating lock-on reticle
		for (int i = 0; i < 4; i++) {
			float sx = (i == 0 || i == 2) ? 1.0f : -1.0f;
			float sz = (i < 2) ? 1.0f : -1.0f;
			WeaponVoxelRenderer.emitCube(pose, vc, sx * r, 0f, sz * (r - bar * 0.5f),
					thick, thick, bar * 0.5f, 0.96f, 0.14f, 0.18f, opacity);
			WeaponVoxelRenderer.emitCube(pose, vc, sx * (r - bar * 0.5f), 0f, sz * r,
					bar * 0.5f, thick, thick, 0.96f, 0.14f, 0.18f, opacity);
		}

		// Central floating crimson pointer diamond
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.10f * scale, 0f,
				0.075f * scale, 0.14f * scale, 0.075f * scale, 1.00f, 0.36f, 0.24f, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, -0.06f * scale, 0f,
				0.045f * scale, 0.08f * scale, 0.045f * scale, 1.00f, 0.64f, 0.52f, opacity);
	}

	private static float smoothstep(float low, float high, float x) {
		float t = Mth.clamp((x - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
