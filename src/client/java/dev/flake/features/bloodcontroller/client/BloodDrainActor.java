package dev.flake.features.bloodcontroller.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
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
import net.minecraft.world.phys.Vec3;

/**
 * Dual helical dark-red & crimson blood siphon arc pulling blood droplets from victim to caster
 * for Blood Controller's Blood Drain skill.
 */
public final class BloodDrainActor implements EffectActor {
	private static final float DURATION_SEC = 0.78f;
	private static final int STREAM_SEGMENTS = 22;

	private final int sourceId;
	private final int targetId;
	private Vec3 fromPos;
	private Vec3 toPos;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;

	public BloodDrainActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.targetId = payload.targetId();
		this.fromPos = payload.origin();
		this.toPos = payload.aim();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.BLOOD_DRAIN;
	}

	@Override
	public boolean expired() {
		return ageSec >= DURATION_SEC;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		slot.values[slot.style] = 0.0f;
		return expired();
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		ClientLevel level = mc.level;
		if (level != null) {
			Entity victim = targetId >= 0 ? level.getEntity(targetId) : null;
			if (victim != null) fromPos = victim.position().add(0.0, victim.getBbHeight() * 0.58, 0.0);
			Entity caster = level.getEntity(sourceId);
			if (caster != null) toPos = caster.position().add(0.0, caster.getBbHeight() * 0.58, 0.0);
		}

		float progress = Mth.clamp(ageSec / DURATION_SEC, 0.0f, 1.0f);
		float alpha = (1.0f - Mth.clamp((progress - 0.62f) / 0.38f, 0.0f, 1.0f)) * Mth.clamp(progress / 0.08f, 0.0f, 1.0f);
		if (alpha <= 0.01f) return;

		float fx = (float) (fromPos.x - cameraPos.x);
		float fy = (float) (fromPos.y - cameraPos.y);
		float fz = (float) (fromPos.z - cameraPos.z);
		float tx = (float) (toPos.x - cameraPos.x);
		float ty = (float) (toPos.y - cameraPos.y);
		float tz = (float) (toPos.z - cameraPos.z);

		float dx = tx - fx;
		float dy = ty - fy;
		float dz = tz - fz;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		float rx = len > 1e-4f ? -dz / len : 1.0f;
		float rz = len > 1e-4f ? dx / len : 0.0f;

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitStreams(pose, vc, fx, fy, fz, tx, ty, tz, rx, rz, progress, alpha, false));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitStreams(pose, vc, fx, fy, fz, tx, ty, tz, rx, rz, progress, alpha * 0.52f, true));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> {
					float headT = Mth.clamp(progress * 1.45f, 0.0f, 1.0f);
					WeaponVoxelRenderer.emitGlowSprite(pose, vc,
							Mth.lerp(headT, fx, tx), Mth.lerp(headT, fy, ty), Mth.lerp(headT, fz, tz),
							0.42f, 0.98f, 0.08f, 0.14f, alpha * 0.72f);
					if (progress > 0.35f) {
						float absorbFade = 1.0f - Mth.clamp((progress - 0.35f) / 0.65f, 0.0f, 1.0f);
						WeaponVoxelRenderer.emitGlowSprite(pose, vc, tx, ty, tz,
								0.55f + 0.30f * progress, 0.92f, 0.06f, 0.12f, absorbFade * 0.65f);
					}
				});
	}

	private static void emitStreams(PoseStack.Pose pose, VertexConsumer vc,
			float fx, float fy, float fz, float tx, float ty, float tz,
			float rx, float rz, float progress, float alpha, boolean soft) {
		float front = Mth.clamp(progress * 1.55f, 0.0f, 1.0f);
		float tail = Mth.clamp((progress - 0.25f) * 1.35f, 0.0f, 1.0f);

		for (int strand = 0; strand < 2; strand++) {
			float phaseOff = strand * Mth.PI;
			for (int i = 0; i <= STREAM_SEGMENTS; i++) {
				float u = i / (float) STREAM_SEGMENTS;
				if (u > front || u < tail) continue;

				float envelope = Mth.sin(u * Mth.PI);
				float ang = u * 11.0f - progress * 14.0f + phaseOff;
				float radius = (0.28f - 0.10f * u) * envelope;
				float ox = rx * Mth.cos(ang) * radius;
				float oy = Mth.sin(ang) * radius + envelope * 0.28f;
				float oz = rz * Mth.cos(ang) * radius;

				float px = Mth.lerp(u, fx, tx) + ox;
				float py = Mth.lerp(u, fy, ty) + oy;
				float pz = Mth.lerp(u, fz, tz) + oz;

				float size = (soft ? 0.068f : 0.034f) * (0.65f + 0.55f * envelope);
				float r = strand == 0 ? 0.96f : 0.44f;
				float g = strand == 0 ? 0.07f : 0.015f;
				float b = strand == 0 ? 0.12f : 0.03f;
				WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, size, size, size, r, g, b, alpha);
			}
		}

		// Extraction droplets around victim + absorption halo around caster
		for (int i = 0; i < 10; i++) {
			float a = i * (Mth.TWO_PI / 10.0f) + progress * 5.0f;
			float burstR = 0.30f + progress * 0.65f;
			float vAlpha = alpha * (1.0f - progress);
			WeaponVoxelRenderer.emitCube(pose, vc,
					fx + Mth.cos(a) * burstR, fy + Mth.sin(a * 2.0f) * 0.25f, fz + Mth.sin(a) * burstR,
					0.03f, 0.04f, 0.03f, 0.85f, 0.04f, 0.08f, vAlpha);

			if (progress > 0.30f) {
				float cProg = (progress - 0.30f) / 0.70f;
				float cRad = 0.62f * (1.0f - cProg * 0.55f);
				WeaponVoxelRenderer.emitCube(pose, vc,
						tx + Mth.cos(-a) * cRad, ty + cProg * 0.45f - 0.15f, tz + Mth.sin(-a) * cRad,
						0.028f, 0.038f, 0.028f, 0.95f, 0.08f, 0.14f, alpha * (1.0f - cProg * 0.6f));
			}
		}
	}
}
