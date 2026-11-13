package dev.flake.features.mirrortrap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.mirrortrap.MirrorTrapRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedBuffActor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 3D silver-cyan floating mirror prism panels orbiting the wearer via WeaponVoxelRenderer. */
public final class MirrorTrapActor extends TimedBuffActor {
	private static final float BUFF_SECONDS = MirrorTrapRuntime.BUFF_TICKS / 20.0f;
	private static final float DISSOLVE_SECONDS = 0.14f;
	private static final float REFLECT_FLASH_SECONDS = 0.32f;
	private static final int PANEL_COUNT = 5;

	private float ageSec;
	private float reflectAt = Float.NaN;
	private Vec3 reflectPos = Vec3.ZERO;

	public MirrorTrapActor(CombatVfxPayload payload) {
		super(payload, CombatVfxPayload.Phase.MIRROR_TRAP_ON, CombatVfxPayload.Phase.MIRROR_TRAP_OFF, BUFF_SECONDS);
		if (payload.phase() == CombatVfxPayload.Phase.MIRROR_TRAP_REFLECT) {
			this.offAt = 0.0f;
			this.reflectAt = 0.0f;
			this.reflectPos = payload.origin();
		}
	}

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.MIRROR_TRAP_REFLECT) {
			reflectAt = ageSec;
			reflectPos = payload.origin();
			return true;
		}
		return super.receive(payload);
	}

	@Override
	public boolean expired() {
		float end = off() + DISSOLVE_SECONDS;
		if (!Float.isNaN(reflectAt)) end = Math.max(end, reflectAt + REFLECT_FLASH_SECONDS);
		return ageSec >= end;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = age(frame);
		return expired();
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = time.sample(System.nanoTime(), mc.isPaused());
		if (expired() || mc.level == null) return;

		var entity = mc.level.getEntity(sourceId);
		if ((entity == null || !entity.isAlive()) && Float.isNaN(offAt)) {
			offAt = Math.min(ageSec, duration);
		}
		Vec3 base = entity != null ? entity.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) : origin;
		double dx = base.x - cameraPos.x;
		double dy = base.y - cameraPos.y;
		double dz = base.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float off = off();
		float panelOpacity = ageSec <= off
				? Math.min(1.0f, ageSec / 0.14f)
				: Math.clamp(1.0f - (ageSec - off) / DISSOLVE_SECONDS, 0.0f, 1.0f);

		if (panelOpacity > 0.01f) {
			float bodyCenterY = (float) dy + (entity != null ? entity.getBbHeight() * 0.54f : 0.96f);
			boolean firstPersonOwner = mc.player != null && mc.player.getId() == sourceId && mc.options.getCameraType().isFirstPerson();
			float orbitRadius = firstPersonOwner ? 1.05f : 0.92f;

			for (int i = 0; i < PANEL_COUNT; i++) {
				float angle = ageSec * 1.95f + i * (float) (Math.PI * 2.0 / PANEL_COUNT);
				float bob = (float) Math.sin(ageSec * 3.4f + i * 1.3f) * 0.11f;
				float px = (float) dx + (float) Math.cos(angle) * orbitRadius;
				float py = bodyCenterY + bob;
				float pz = (float) dz + (float) Math.sin(angle) * orbitRadius;

				poseStack.pushPose();
				poseStack.translate(px, py, pz);
				poseStack.mulPose(new Matrix4f().rotationY(-angle + (float) (Math.PI * 0.5)).rotateX(0.16f * (float) Math.sin(ageSec * 2.2f + i)));

				float s = 0.85f + 0.15f * panelOpacity;
				collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
						(pose, vc) -> emitMirrorFrame(pose, vc, s, panelOpacity));
				collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
						(pose, vc) -> emitMirrorPane(pose, vc, s, panelOpacity));
				poseStack.popPose();
			}
		}

		if (!Float.isNaN(reflectAt)) {
			float rt = (ageSec - reflectAt) / REFLECT_FLASH_SECONDS;
			if (rt >= 0.0f && rt < 1.0f) {
				float fx = (float) (reflectPos.x - cameraPos.x);
				float fy = (float) (reflectPos.y - cameraPos.y);
				float fz = (float) (reflectPos.z - cameraPos.z);
				float flashAlpha = 1.0f - rt;
				collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
						(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc, fx, fy, fz,
								0.55f + 0.75f * rt, 0.72f, 0.96f, 1.0f, 0.85f * flashAlpha));
			}
		}
	}

	private void emitMirrorFrame(PoseStack.Pose pose, VertexConsumer vc, float scale, float opacity) {
		float w = 0.18f * scale;
		float h = 0.30f * scale;
		float t = 0.024f * scale;
		// Silver-cyan prism frame border bars
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, h, 0f, w, 0.025f * scale, t, 0.84f, 0.95f, 1.0f, 0.90f * opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, -h, 0f, w, 0.025f * scale, t, 0.84f, 0.95f, 1.0f, 0.90f * opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, -w, 0f, 0f, 0.025f * scale, h, t, 0.68f, 0.90f, 0.98f, 0.90f * opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, w, 0f, 0f, 0.025f * scale, h, t, 0.68f, 0.90f, 0.98f, 0.90f * opacity);
	}

	private void emitMirrorPane(PoseStack.Pose pose, VertexConsumer vc, float scale, float opacity) {
		float shimmer = 0.82f + 0.18f * Mth.sin(ageSec * 7.5f);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0f, 0.155f * scale, 0.275f * scale, 0.014f * scale,
				0.52f * shimmer, 0.90f * shimmer, 1.0f, 0.44f * opacity);
	}
}
