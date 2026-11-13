package dev.flake.features.chainblade.client;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Reworked Chain Blade VFX: sleek glowing crimson-obsidian energy lash rendered in post-shader
 * (`style = 21`) with a sharp razor hook claw & crimson impact sparks (no blocky link cubes).
 */
public final class ChainBladeActor implements EffectActor {
	private static final float DURATION_SEC = 0.68f;
	private static final int SEGMENTS = 14;

	private final int sourceId;
	private final int targetId;
	private final Vec3 fallbackOrigin;
	private final Vec3 fallbackAim;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;

	public ChainBladeActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.targetId = payload.targetId();
		this.fallbackOrigin = payload.origin();
		this.fallbackAim = payload.aim();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.CHAIN_BLADE_PULL;
	}

	@Override public boolean expired() { return ageSec >= DURATION_SEC; }

	private float computeAlpha() {
		float progress = Math.clamp(ageSec / DURATION_SEC, 0f, 1f);
		float popIn = Math.min(1.0f, ageSec / 0.045f);
		float fade = 1.0f - Math.clamp((progress - 0.55f) / 0.45f, 0f, 1f);
		return popIn * fade * fade;
	}

	private Vec3 resolveStart(Level level, float partial) {
		if (level == null) return fallbackOrigin;
		Entity src = level.getEntity(sourceId);
		if (src == null) return fallbackOrigin;
		Vec3 look = src.getViewVector(partial);
		Vec3 rawRight = look.cross(new Vec3(0, 1, 0));
		Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : rawRight.normalize();
		return src.getEyePosition(partial).add(look.scale(0.45)).add(right.scale(0.28)).add(0, -0.26, 0);
	}

	private Vec3 resolveEnd(Level level, float partial) {
		if (level == null || targetId < 0) return fallbackAim;
		Entity tgt = level.getEntity(targetId);
		if (tgt == null) return fallbackAim;
		return tgt.getPosition(partial).add(0, tgt.getBbHeight() * 0.55, 0);
	}

	private Vec3[] buildWhipPoints(Vec3 start, Vec3 end) {
		Vec3 delta = end.subtract(start);
		double len = Math.max(0.25, delta.length());
		Vec3 dir = delta.lengthSqr() > 1e-6 ? delta.scale(1.0 / len) : new Vec3(0, 0, 1);
		Vec3 rawRight = dir.cross(new Vec3(0, 1, 0));
		Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : rawRight.normalize();
		Vec3 up = right.cross(dir).normalize();

		float extend = Math.min(1.0f, ageSec / 0.085f);
		float whipDecay = (float) Math.exp(-ageSec * 8.0f);
		Vec3[] pts = new Vec3[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			float t = (i / (float) SEGMENTS) * extend;
			float sag = (float) Math.sin(t * Math.PI) * (-0.18f * whipDecay - 0.04f);
			float wave = (float) Math.sin(t * 9.0f - ageSec * 24.0f) * (0.14f * whipDecay);
			pts[i] = start.add(dir.scale(t * len)).add(up.scale(sag)).add(right.scale(wave));
		}
		return pts;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		Vec3 start = resolveStart(frame.client.level, frame.partial);
		Vec3 end = resolveEnd(frame.client.level, frame.partial);
		Vec3[] pts = buildWhipPoints(start, end);
		Vec3 mid = start.add(end).scale(0.5);
		float boundRadius = (float) (start.distanceTo(end) * 0.55 + 1.5);
		float alpha = computeAlpha();

		float[] values = slot.values;
		values[slot.position] = (float) (mid.x - frame.camera.x);
		values[slot.position + 1] = (float) (mid.y - frame.camera.y);
		values[slot.position + 2] = (float) (mid.z - frame.camera.z);
		values[slot.position + 3] = boundRadius;

		values[slot.style] = alpha;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 21.0f;
		values[slot.style + 3] = 0.0f;

		values[slot.material] = SEGMENTS;
		for (int i = 0; i < SEGMENTS; i++) {
			int sb = slot.segment + i * 4;
			int eb = slot.segmentEnd + i * 4;
			values[sb] = (float) (pts[i].x - frame.camera.x);
			values[sb + 1] = (float) (pts[i].y - frame.camera.y);
			values[sb + 2] = (float) (pts[i].z - frame.camera.z);
			values[sb + 3] = 0.022f;
			values[eb] = (float) (pts[i + 1].x - frame.camera.x);
			values[eb + 1] = (float) (pts[i + 1].y - frame.camera.y);
			values[eb + 2] = (float) (pts[i + 1].z - frame.camera.z);
			values[eb + 3] = 1.0f;
		}
		return false;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired() || mc.level == null) return;

		float alpha = computeAlpha();
		if (alpha <= 0.015f) return;

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 start = resolveStart(mc.level, partial);
		Vec3 end = resolveEnd(mc.level, partial);
		Vec3 delta = end.subtract(start);
		double len = delta.length();
		if (len < 0.25) return;
		Vec3 dir = delta.scale(1.0 / len);

		Vec3 rawRight = dir.cross(new Vec3(0, 1, 0));
		Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : rawRight.normalize();
		Vec3 up = right.cross(dir).normalize();

		float progress = Math.clamp(ageSec / DURATION_SEC, 0f, 1f);
		float extend = Math.min(1.0f, ageSec / 0.085f);
		Vec3 hookPos = start.add(dir.scale(len * extend));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitRazorHookAndSparks(pose, vc, cameraPos, hookPos, end, dir, right, up, progress, alpha));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc,
						(float) (hookPos.x - cameraPos.x), (float) (hookPos.y - cameraPos.y), (float) (hookPos.z - cameraPos.z),
						0.52f, 1.00f, 0.22f, 0.28f, 0.42f * alpha));
	}

	private void emitRazorHookAndSparks(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 hookPos, Vec3 end, Vec3 dir, Vec3 right, Vec3 up, float progress, float alpha) {
		// Sharp minimalist obsidian-crimson barb clasp at hook tip
		WeaponVoxelRenderer.emitCube(pose, vc,
				(float) (hookPos.x - cam.x), (float) (hookPos.y - cam.y), (float) (hookPos.z - cam.z),
				0.040f, 0.040f, 0.040f, 1.00f, 0.36f, 0.30f, alpha * 0.92f);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 barb = hookPos.subtract(dir.scale(0.14)).add(right.scale(side * 0.095));
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (barb.x - cam.x), (float) (barb.y - cam.y), (float) (barb.z - cam.z),
					0.026f, 0.026f, 0.026f, 0.72f, 0.06f, 0.14f, alpha * 0.88f);
		}

		// Crisp ember sparks bursting backward off impact
		int sparks = 6;
		float burst = Math.min(1.0f, progress / 0.32f);
		for (int i = 0; i < sparks; i++) {
			float ang = (i / (float) sparks) * (float) (Math.PI * 2.0) + progress * 2.8f;
			float rad = 0.14f + 0.38f * burst;
			Vec3 p = end.add(right.scale(Math.cos(ang) * rad))
					.add(up.scale(Math.sin(ang) * rad))
					.add(dir.scale(-0.20f * burst));
			float s = 0.024f * (1.0f - 0.50f * progress);
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
					s, s, s, 1.00f, i % 2 == 0 ? 0.42f : 0.14f, 0.24f, alpha * 0.78f);
		}
	}
}
