package dev.flake.features.chainblade.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 3D metallic-crimson voxel chain link stream connecting the Chain Blade wielder to the pulled enemy.
 */
public final class ChainBladeActor implements EffectActor {
	private static final float DURATION_SEC = 0.72f;
	private static final int LINK_COUNT = 16;

	private final int sourceId;
	private final int targetId;
	private final Vec3 fallbackOrigin;
	private final Vec3 fallbackAim;
	private long startNanos = -1L;
	private float ageSec;

	public ChainBladeActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.targetId = payload.targetId();
		this.fallbackOrigin = payload.origin();
		this.fallbackAim = payload.aim();
	}

	@Override public int sourceId() { return sourceId; }

	@Override public boolean expired() { return startNanos >= 0L && ageSec >= DURATION_SEC; }

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		if (startNanos < 0L) startNanos = frame.nanos;
		ageSec = (frame.nanos - startNanos) / 1_000_000_000.0f;
		return ageSec >= DURATION_SEC;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		if (startNanos < 0L) startNanos = System.nanoTime();
		ageSec = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
		if (ageSec >= DURATION_SEC) return;

		float progress = Math.clamp(ageSec / DURATION_SEC, 0f, 1f);
		float popIn = Math.min(1.0f, ageSec / 0.055f);
		float fade = 1.0f - Math.clamp((progress - 0.58f) / 0.42f, 0f, 1f);
		float alpha = popIn * fade * fade;
		if (alpha <= 0.015f) return;

		Vec3 start = resolveStart(fallbackOrigin);
		Vec3 end = resolveEnd(fallbackAim);
		Vec3 delta = end.subtract(start);
		double len = delta.length();
		if (len < 0.25) return;
		Vec3 dir = delta.scale(1.0 / len);

		Vec3 rawRight = dir.cross(new Vec3(0, 1, 0));
		Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : rawRight.normalize();
		Vec3 up = right.cross(dir).normalize();

		// Extension surges out rapidly on cast, then snaps taut as the enemy is yanked inward.
		float extend = Math.min(1.0f, progress / 0.14f);
		float whipDecay = (float) Math.exp(-ageSec * 7.5f);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitChainLinks(pose, vc, cameraPos, start, dir, right, up, len, extend, whipDecay, alpha));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitHookSparks(pose, vc, cameraPos, end, dir, right, up, progress, alpha));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitHookGlow(pose, vc, cameraPos, start, end, dir, len, extend, alpha));
	}

	private Vec3 resolveStart(Vec3 fallback) {
		var mc = Minecraft.getInstance();
		if (mc.level == null) return fallback;
		Entity src = mc.level.getEntity(sourceId);
		if (src == null) return fallback;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 look = src.getViewVector(partial);
		Vec3 rawRight = look.cross(new Vec3(0, 1, 0));
		Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : rawRight.normalize();
		return src.getEyePosition(partial).add(look.scale(0.50)).add(right.scale(0.28)).add(0, -0.32, 0);
	}

	private Vec3 resolveEnd(Vec3 fallback) {
		var mc = Minecraft.getInstance();
		if (mc.level == null || targetId < 0) return fallback;
		Entity tgt = mc.level.getEntity(targetId);
		if (tgt == null) return fallback;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		return tgt.getPosition(partial).add(0, tgt.getBbHeight() * 0.55, 0);
	}

	private void emitChainLinks(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 start, Vec3 dir, Vec3 right, Vec3 up, double len, float extend, float whipDecay, float alpha) {
		int links = Math.max(6, Math.min(LINK_COUNT, (int) Math.ceil(len * 2.2)));
		for (int i = 0; i < links; i++) {
			float t = (i + 0.5f) / links;
			if (t > extend) continue;

			// Slight whipping S-curve that pulls taut quickly
			float sag = (float) Math.sin(t * Math.PI) * (-0.16f * whipDecay);
			float wave = (float) Math.sin(t * 10.0f - ageSec * 22.0f) * (0.09f * whipDecay);
			Vec3 center = start.add(dir.scale(t * len)).add(up.scale(sag)).add(right.scale(wave));

			boolean crimsonLink = (i % 3 == 1) || i == links - 1;
			boolean horizontal = (i % 2 == 0);
			Vec3 sideA = horizontal ? right : up;
			Vec3 sideB = horizontal ? up : right;

			float r, g, b;
			if (i == links - 1) {
				r = 1.00f; g = 0.24f; b = 0.22f; // bright crimson hook clasp
			} else if (crimsonLink) {
				r = 0.88f; g = 0.12f; b = 0.16f; // deep crimson link
			} else if (i % 2 == 0) {
				r = 0.64f; g = 0.68f; b = 0.74f; // cold steel
			} else {
				r = 0.34f; g = 0.36f; b = 0.42f; // dark forged iron
			}

			float linkHalfSpan = 0.085f;
			float linkHalfLen = Math.min(0.16f, (float) (len / links) * 0.46f);
			float barThick = 0.030f;

			// Two parallel side rails + front/back end caps forming a hollow 3D voxel chain link
			for (int s = -1; s <= 1; s += 2) {
				Vec3 rail = center.add(sideA.scale(s * linkHalfSpan));
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (rail.x - cam.x), (float) (rail.y - cam.y), (float) (rail.z - cam.z),
						barThick, barThick, barThick, r, g, b, alpha * 0.94f);
				Vec3 railFwd = rail.add(dir.scale(linkHalfLen * 0.6f));
				Vec3 railBack = rail.add(dir.scale(-linkHalfLen * 0.6f));
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (railFwd.x - cam.x), (float) (railFwd.y - cam.y), (float) (railFwd.z - cam.z),
						barThick, barThick, barThick, r, g, b, alpha * 0.94f);
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (railBack.x - cam.x), (float) (railBack.y - cam.y), (float) (railBack.z - cam.z),
						barThick, barThick, barThick, r, g, b, alpha * 0.94f);
			}
			// End cap bridging the two rails at front and back
			Vec3 capFwd = center.add(dir.scale(linkHalfLen));
			Vec3 capBack = center.add(dir.scale(-linkHalfLen));
			float capThick = 0.028f;
			for (int s = -1; s <= 1; s++) {
				Vec3 p1 = capFwd.add(sideA.scale(s * linkHalfSpan * 0.55f)).add(sideB.scale(0.01f));
				Vec3 p2 = capBack.add(sideA.scale(s * linkHalfSpan * 0.55f)).add(sideB.scale(-0.01f));
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (p1.x - cam.x), (float) (p1.y - cam.y), (float) (p1.z - cam.z),
						capThick, capThick, capThick, r * 0.92f, g * 0.92f, b * 0.92f, alpha * 0.92f);
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (p2.x - cam.x), (float) (p2.y - cam.y), (float) (p2.z - cam.z),
						capThick, capThick, capThick, r * 0.92f, g * 0.92f, b * 0.92f, alpha * 0.92f);
			}
		}
	}

	private void emitHookSparks(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 end, Vec3 dir, Vec3 right, Vec3 up, float progress, float alpha) {
		int sparks = 8;
		float burst = Math.min(1.0f, progress / 0.35f);
		for (int i = 0; i < sparks; i++) {
			float ang = (i / (float) sparks) * (float) (Math.PI * 2.0) + progress * 2.4f;
			float rad = 0.16f + 0.42f * burst;
			Vec3 p = end.add(right.scale(Math.cos(ang) * rad))
					.add(up.scale(Math.sin(ang) * rad))
					.add(dir.scale(-0.22f * burst));
			float s = 0.034f * (1.0f - 0.45f * progress);
			boolean bright = (i % 2 == 0);
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
					s, s, s,
					bright ? 1.00f : 0.78f,
					bright ? 0.30f : 0.08f,
					bright ? 0.24f : 0.12f,
					alpha * 0.78f);
		}
	}

	private void emitHookGlow(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 start, Vec3 end, Vec3 dir, double len, float extend, float alpha) {
		Vec3 hookPos = start.add(dir.scale(len * extend));
		WeaponVoxelRenderer.emitGlowSprite(pose, vc,
				(float) (hookPos.x - cam.x), (float) (hookPos.y - cam.y), (float) (hookPos.z - cam.z),
				0.72f, 0.98f, 0.16f, 0.20f, 0.44f * alpha);
		WeaponVoxelRenderer.emitGlowSprite(pose, vc,
				(float) (end.x - cam.x), (float) (end.y - cam.y), (float) (end.z - cam.z),
				0.48f, 1.00f, 0.34f, 0.28f, 0.32f * alpha);
	}
}
