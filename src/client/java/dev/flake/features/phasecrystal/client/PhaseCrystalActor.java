package dev.flake.features.phasecrystal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.phasecrystal.PhaseCrystalRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedBuffActor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Translucent aquamarine/violet 3D voxel shell orbiting the player during phase + teleport flash. */
public final class PhaseCrystalActor extends TimedBuffActor {
	private static final float BUFF_SECONDS = PhaseCrystalRuntime.BUFF_TICKS / 20.0f;
	private static final float FLASH_SECONDS = 0.36f;

	private float ageSec;
	private Vec3 flashOrigin;

	public PhaseCrystalActor(CombatVfxPayload payload) {
		super(payload, CombatVfxPayload.Phase.PHASE_CRYSTAL_ON, CombatVfxPayload.Phase.PHASE_CRYSTAL_OFF, BUFF_SECONDS);
		this.flashOrigin = payload.origin();
		if (payload.phase() == CombatVfxPayload.Phase.PHASE_CRYSTAL_OFF) {
			this.offAt = 0.0f;
		}
	}

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.PHASE_CRYSTAL_OFF) {
			flashOrigin = payload.origin();
		}
		return super.receive(payload);
	}

	@Override
	public boolean expired() {
		return ageSec >= off() + FLASH_SECONDS;
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

		float off = off();
		if (ageSec <= off) {
			Vec3 base = entity != null ? entity.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) : origin;
			double dx = base.x - cameraPos.x;
			double dy = base.y - cameraPos.y;
			double dz = base.z - cameraPos.z;
			if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

			float height = entity != null ? entity.getBbHeight() : 1.8f;
			float fadeIn = Math.min(1.0f, ageSec / 0.14f);
			boolean firstPerson = mc.player != null && mc.player.getId() == sourceId && mc.options.getCameraType().isFirstPerson();
			float shellRadius = firstPerson ? 1.05f : 0.86f;

			// Two counter-tilted translucent aquamarine & violet crystal shell rings
			emitCrystalRing(poseStack, collector, (float) dx, (float) dy + height * 0.52f, (float) dz,
					shellRadius, 0.55f, ageSec * 2.2f, fadeIn, true);
			emitCrystalRing(poseStack, collector, (float) dx, (float) dy + height * 0.52f, (float) dz,
					shellRadius * 0.94f, -0.55f, -ageSec * 2.6f, fadeIn, false);

			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
					(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc,
							(float) dx, (float) dy + height * 0.52f, (float) dz,
							0.85f, 0.34f, 0.92f, 0.95f, 0.36f * fadeIn));
			return;
		}

		// Teleport arrival flash burst at destination
		float flashT = Mth.clamp((ageSec - off) / FLASH_SECONDS, 0.0f, 1.0f);
		float flashFade = 1.0f - flashT * flashT;
		if (flashFade <= 0.01f) return;

		Vec3 base = flashOrigin != null ? flashOrigin : (entity != null ? entity.position() : origin);
		float fx = (float) (base.x - cameraPos.x);
		float fy = (float) (base.y - cameraPos.y + 0.95);
		float fz = (float) (base.z - cameraPos.z);
		if (fx * fx + fy * fy + fz * fz > 64.0 * 64.0) return;

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitTeleportShards(pose, vc, fx, fy, fz, flashT, flashFade));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc, fx, fy, fz,
						0.95f * (0.45f + 0.55f * flashT), 0.42f, 0.95f, 0.98f, 0.72f * flashFade));
	}

	private void emitCrystalRing(PoseStack poseStack, SubmitNodeCollector collector,
			float cx, float cy, float cz, float radius, float tiltX, float spin, float opacity, boolean aquaPrimary) {
		poseStack.pushPose();
		poseStack.translate(cx, cy, cz);
		poseStack.mulPose(new Matrix4f().rotate(new Quaternionf().rotateX(tiltX).rotateY(spin)));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE, (pose, vc) -> {
			int count = 12;
			for (int i = 0; i < count; i++) {
				float ang = (i / (float) count) * (float) (Math.PI * 2.0);
				float px = (float) Math.cos(ang) * radius;
				float pz = (float) Math.sin(ang) * radius;
				float py = (float) Math.sin(ang * 2.0f + ageSec * 4.0f) * 0.14f;
				boolean aqua = aquaPrimary ? (i % 3 != 0) : (i % 3 == 0);
				float r = aqua ? 0.28f : 0.68f;
				float g = aqua ? 0.94f : 0.34f;
				float b = aqua ? 0.88f : 0.98f;
				float s = (i % 2 == 0) ? 0.075f : 0.052f;
				WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, s, s * 1.45f, s, r, g, b, 0.54f * opacity);
			}
		});
		poseStack.popPose();
	}

	private void emitTeleportShards(PoseStack.Pose pose, VertexConsumer vc,
			float fx, float fy, float fz, float flashT, float flashFade) {
		int count = 16;
		float spread = 0.25f + flashT * 1.35f;
		for (int i = 0; i < count; i++) {
			float ang = i * 2.39996f + 0.6f;
			float yFrac = -0.6f + (i % 5) * 0.30f;
			float px = fx + (float) Math.cos(ang) * spread;
			float pz = fz + (float) Math.sin(ang) * spread;
			float py = fy + yFrac * (0.4f + 0.8f * flashT);
			boolean aqua = (i % 2 == 0);
			float s = 0.055f * (1.0f - 0.45f * flashT);
			WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, s, s * 1.3f, s,
					aqua ? 0.34f : 0.72f, aqua ? 0.96f : 0.38f, aqua ? 0.90f : 1.00f, 0.75f * flashFade);
		}
	}
}
