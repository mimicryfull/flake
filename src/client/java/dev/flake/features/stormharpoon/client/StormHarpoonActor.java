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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Storm Harpoon hybrid VFX: blazing cyan HDR post-shader laser + bloom & spiraling lightning arcs
 * (`style = 20`, mode 1), paired with a sleek crisp harpoon head & impact thunder ring.
 */
public final class StormHarpoonActor implements EffectActor {
	private static final float DURATION_SEC = 0.55f;
	private static final int ARC_SEGMENTS = 6;

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

	@Override public boolean expired() { return ageSec >= DURATION_SEC; }

	private Vec3 resolveStart(Level level, float partial) {
		if (level == null) return origin;
		Entity caster = level.getEntity(sourceId);
		return caster != null
				? caster.getEyePosition(partial).add(caster.getViewVector(partial).scale(0.45)).add(0.0, -0.15, 0.0)
				: origin;
	}

	private Vec3 resolveEnd(Level level, float partial) {
		if (level == null || targetId < 0) return aim;
		Entity target = level.getEntity(targetId);
		return target != null
				? target.getPosition(partial).add(0.0, target.getBbHeight() * 0.55, 0.0)
				: aim;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		Vec3 start = resolveStart(frame.client.level, frame.partial);
		Vec3 end = resolveEnd(frame.client.level, frame.partial);
		Vec3 delta = end.subtract(start);
		double dist = delta.length();
		if (dist < 0.15) return false;

		Vec3 dir = delta.scale(1.0 / dist);
		float extend = Mth.clamp(ageSec / 0.075f, 0.0f, 1.0f);
		Vec3 tip = start.add(dir.scale(dist * extend));
		Vec3 mid = start.add(tip).scale(0.5);

		float progress = Mth.clamp(ageSec / DURATION_SEC, 0.0f, 1.0f);
		float fade = 1.0f - smoothstep(0.35f, 1.0f, progress);
		float impactFlash = (float) Math.exp(-ageSec * 6.0f);

		float[] values = slot.values;
		values[slot.position] = (float) (mid.x - frame.camera.x);
		values[slot.position + 1] = (float) (mid.y - frame.camera.y);
		values[slot.position + 2] = (float) (mid.z - frame.camera.z);
		values[slot.position + 3] = (float) (dist * 0.55 + 2.0);

		values[slot.style] = fade;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 20.0f;
		values[slot.style + 3] = 1.0f; // mode 1: laser + HDR bloom

		// Segment 0 = primary laser beam
		writeSeg(values, slot, 0, frame.camera, start, tip, 0.048f, impactFlash);

		// Segments 1..ARC_SEGMENTS = spiraling plasma arcs around laser shaft
		Vec3 rawRight = dir.cross(new Vec3(0.0, 1.0, 0.0));
		Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : rawRight.normalize();
		Vec3 up = right.cross(dir).normalize();
		double reach = dist * extend;
		Vec3 prev = start;
		for (int i = 1; i <= ARC_SEGMENTS; i++) {
			float t = i / (float) ARC_SEGMENTS;
			float spin = ageSec * 22.0f + t * 14.0f;
			float amp = i == ARC_SEGMENTS ? 0.0f : (float) Math.sin(t * Math.PI) * 0.18f;
			Vec3 cur = start.add(dir.scale(reach * t))
					.add(right.scale(Math.cos(spin) * amp))
					.add(up.scale(Math.sin(spin) * amp));
			writeSeg(values, slot, i, frame.camera, prev, cur, 0.020f, 0.85f);
			prev = cur;
		}
		values[slot.material] = ARC_SEGMENTS;
		return false;
	}

	private static void writeSeg(float[] values, SlotWriter slot, int idx, Vec3 cam,
			Vec3 a, Vec3 b, float w, float intensity) {
		int sb = slot.segment + idx * 4;
		int eb = slot.segmentEnd + idx * 4;
		values[sb] = (float) (a.x - cam.x);
		values[sb + 1] = (float) (a.y - cam.y);
		values[sb + 2] = (float) (a.z - cam.z);
		values[sb + 3] = w;
		values[eb] = (float) (b.x - cam.x);
		values[eb + 1] = (float) (b.y - cam.y);
		values[eb + 2] = (float) (b.z - cam.z);
		values[eb + 3] = intensity;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired() || mc.level == null) return;

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 start = resolveStart(mc.level, partial);
		Vec3 end = resolveEnd(mc.level, partial);
		Vec3 delta = end.subtract(start);
		double dist = delta.length();
		if (dist < 0.15) return;
		Vec3 dir = delta.scale(1.0 / dist);

		double midDx = (start.x + end.x) * 0.5 - cameraPos.x;
		double midDy = (start.y + end.y) * 0.5 - cameraPos.y;
		double midDz = (start.z + end.z) * 0.5 - cameraPos.z;
		if (midDx * midDx + midDy * midDy + midDz * midDz > 64.0 * 64.0) return;

		float progress = Mth.clamp(ageSec / DURATION_SEC, 0.0f, 1.0f);
		float extend = Mth.clamp(ageSec / 0.075f, 0.0f, 1.0f);
		float fade = 1.0f - smoothstep(0.35f, 1.0f, progress);
		if (fade <= 0.01f) return;

		Vec3 rawRight = dir.cross(new Vec3(0.0, 1.0, 0.0));
		Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : rawRight.normalize();
		Vec3 up = right.cross(dir).normalize();

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitHarpoonHead(pose, vc, cameraPos, start, dir, right, dist * extend, fade));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitThunderRings(pose, vc, cameraPos, start, dir, right, up, end, dist * extend, progress, fade));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc,
						(float) (start.x + dir.x * (dist * extend) - cameraPos.x),
						(float) (start.y + dir.y * (dist * extend) - cameraPos.y),
						(float) (start.z + dir.z * (dist * extend) - cameraPos.z),
						0.85f, 0.65f, 0.96f, 1.00f, 0.75f * fade));
	}

	private void emitHarpoonHead(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 start, Vec3 dir, Vec3 right, double reach, float fade) {
		Vec3 head = start.add(dir.scale(reach));
		float hs = 0.075f * (0.75f + 0.25f * fade);
		WeaponVoxelRenderer.emitCube(pose, vc,
				(float) (head.x - cam.x), (float) (head.y - cam.y), (float) (head.z - cam.z),
				hs, hs, hs, 0.94f, 0.99f, 1.00f, fade);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 barb = head.subtract(dir.scale(0.18)).add(right.scale(side * 0.14));
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (barb.x - cam.x), (float) (barb.y - cam.y), (float) (barb.z - cam.z),
					0.042f, 0.042f, 0.042f, 0.42f, 0.94f, 1.00f, fade);
		}
	}

	private void emitThunderRings(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 start, Vec3 dir, Vec3 right, Vec3 up, Vec3 end, double reach, float progress, float fade) {
		if (targetId >= 0) {
			float impR = 0.35f + progress * 1.15f;
			for (int i = 0; i < 12; i++) {
				float ang = (i / 12.0f) * (float) (Math.PI * 2.0) - ageSec * 5.0f;
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (end.x - cam.x + Math.cos(ang) * impR),
						(float) (end.y - cam.y),
						(float) (end.z - cam.z + Math.sin(ang) * impR),
						0.036f, 0.022f, 0.036f, 0.75f, 0.98f, 1.00f, 0.72f * fade);
			}
		} else {
			Vec3 tip = start.add(dir.scale(reach));
			for (int i = 0; i < 8; i++) {
				float ang = (i / 8.0f) * (float) (Math.PI * 2.0) + ageSec * 7.0f;
				float r = 0.20f + 0.18f * progress;
				Vec3 p = tip.add(right.scale(Math.cos(ang) * r)).add(up.scale(Math.sin(ang) * r));
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
						0.028f, 0.028f, 0.028f, 0.56f, 0.95f, 1.00f, 0.70f * fade);
			}
		}
	}

	private static float smoothstep(float low, float high, float x) {
		float t = Mth.clamp((x - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
