package dev.flake.features.stormharpoon.client;

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

/** Bright cyan-white 3D voxel harpoon chain + crackling thunder rings. */
public final class StormHarpoonActor implements EffectActor {
	private static final float DURATION_SEC = 0.55f;

	private final int sourceId;
	private final int targetId;
	private final Vec3 origin;
	private final Vec3 aim;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;

	public StormHarpoonActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.targetId = payload.targetId();
		this.origin = payload.origin();
		this.aim = payload.aim();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.STORM_HARPOON;
	}

	@Override
	public boolean expired() {
		return ageSec >= DURATION_SEC;
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

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Entity caster = mc.level.getEntity(sourceId);
		Vec3 start = caster != null
				? caster.getEyePosition(partial).add(caster.getViewVector(partial).scale(0.45)).add(0.0, -0.15, 0.0)
				: origin;
		Entity target = targetId >= 0 ? mc.level.getEntity(targetId) : null;
		Vec3 end = target != null
				? target.getPosition(partial).add(0.0, target.getBbHeight() * 0.55, 0.0)
				: aim;

		Vec3 delta = end.subtract(start);
		double dist = delta.length();
		if (dist < 0.15) return;
		Vec3 dir = delta.scale(1.0 / dist);

		double midDx = (start.x + end.x) * 0.5 - cameraPos.x;
		double midDy = (start.y + end.y) * 0.5 - cameraPos.y;
		double midDz = (start.z + end.z) * 0.5 - cameraPos.z;
		if (midDx * midDx + midDy * midDy + midDz * midDz > 64.0 * 64.0) return;

		float progress = Mth.clamp(ageSec / DURATION_SEC, 0.0f, 1.0f);
		float extend = Mth.clamp(ageSec / 0.08f, 0.0f, 1.0f);
		float fade = 1.0f - smoothstep(0.35f, 1.0f, progress);
		if (fade <= 0.01f) return;

		Vec3 rawRight = dir.cross(new Vec3(0.0, 1.0, 0.0));
		Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : rawRight.normalize();
		Vec3 up = right.cross(dir).normalize();

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitChainAndHead(pose, vc, cameraPos, start, dir, right, up, dist * extend, progress, fade));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitThunderRings(pose, vc, cameraPos, start, dir, right, up, end, dist * extend, progress, fade));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitGlows(pose, vc, cameraPos, start, dir, dist * extend, fade));
	}

	private void emitChainAndHead(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 start, Vec3 dir, Vec3 right, Vec3 up, double reach, float progress, float fade) {
		int links = Math.max(8, Math.min(28, (int) (reach * 2.2)));
		for (int i = 0; i < links; i++) {
			double t = i / (double) Math.max(1, links - 1);
			double d = reach * t;
			float crackle = (float) Math.sin(ageSec * 36.0f + i * 2.1f) * 0.065f * (1.0f - progress * 0.5f);
			float crackleUp = (float) Math.cos(ageSec * 29.0f + i * 1.7f) * 0.055f * (1.0f - progress * 0.5f);
			Vec3 p = start.add(dir.scale(d)).add(right.scale(crackle)).add(up.scale(crackleUp));

			boolean coreWhite = (i % 2 == 0);
			float size = coreWhite ? 0.048f : 0.036f;
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
					size, size, size,
					coreWhite ? 0.90f : 0.34f,
					coreWhite ? 0.98f : 0.90f,
					1.00f,
					0.95f * fade);
		}

		// Harpoon tip cluster at the tip of the beam
		Vec3 head = start.add(dir.scale(reach));
		float hs = 0.088f * (0.75f + 0.25f * fade);
		WeaponVoxelRenderer.emitCube(pose, vc,
				(float) (head.x - cam.x), (float) (head.y - cam.y), (float) (head.z - cam.z),
				hs, hs, hs, 0.92f, 0.99f, 1.00f, fade);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 barb = head.subtract(dir.scale(0.18)).add(right.scale(side * 0.14));
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (barb.x - cam.x), (float) (barb.y - cam.y), (float) (barb.z - cam.z),
					0.045f, 0.045f, 0.045f, 0.42f, 0.94f, 1.00f, fade);
		}
	}

	private void emitThunderRings(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 start, Vec3 dir, Vec3 right, Vec3 up, Vec3 end, double reach, float progress, float fade) {
		// Two crackling perpendicular rings along the harpoon shaft
		for (int ring = 0; ring < 2; ring++) {
			double d = reach * (ring == 0 ? 0.42 : 0.86);
			Vec3 center = start.add(dir.scale(d));
			float radius = 0.24f + 0.28f * progress + ring * 0.08f;
			int segments = 12;
			for (int i = 0; i < segments; i++) {
				float ang = (i / (float) segments) * (float) (Math.PI * 2.0) + ageSec * (ring == 0 ? 7.0f : -6.0f);
				Vec3 p = center.add(right.scale(Math.cos(ang) * radius)).add(up.scale(Math.sin(ang) * radius));
				float s = 0.032f;
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
						s, s, s, 0.56f, 0.95f, 1.00f, 0.78f * fade);
			}
		}

		// Expanding thunder ring around impact point if an entity was hit
		if (targetId >= 0) {
			float impR = 0.35f + progress * 1.15f;
			for (int i = 0; i < 14; i++) {
				float ang = (i / 14.0f) * (float) (Math.PI * 2.0) - ageSec * 5.0f;
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (end.x - cam.x + Math.cos(ang) * impR),
						(float) (end.y - cam.y),
						(float) (end.z - cam.z + Math.sin(ang) * impR),
						0.040f, 0.025f, 0.040f, 0.75f, 0.98f, 1.00f, 0.70f * fade);
			}
		}
	}

	private void emitGlows(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 start, Vec3 dir, double reach, float fade) {
		Vec3 head = start.add(dir.scale(reach));
		WeaponVoxelRenderer.emitGlowSprite(pose, vc,
				(float) (head.x - cam.x), (float) (head.y - cam.y), (float) (head.z - cam.z),
				0.72f, 0.65f, 0.96f, 1.00f, 0.72f * fade);
	}

	private static float smoothstep(float low, float high, float x) {
		float t = Mth.clamp((x - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
