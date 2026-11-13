package dev.flake.features.controlhammer.client;

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
 * Drops a voxel-built anvil onto the target's head for Control Hammer hits
 * (or renders a compact orange-brown voxel burst when triggered by Axe of Cumshot impact).
 */
public final class HammerStunActor implements EffectActor {
	private static final float ANVIL_DURATION_SEC = 0.88f;
	private static final float AXE_IMPACT_DURATION_SEC = 0.42f;
	private static final float DROP_TIME_SEC = 0.17f;

	private final int sourceId;
	private final int targetId;
	private final Vec3 fallbackPos;
	private final boolean axeImpact;
	private final float durationSec;
	private long startNanos = -1L;
	private float ageSec;

	public HammerStunActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.targetId = payload.targetId();
		this.fallbackPos = payload.origin();
		this.axeImpact = payload.yaw() > 0.5f;
		this.durationSec = axeImpact ? AXE_IMPACT_DURATION_SEC : ANVIL_DURATION_SEC;
	}

	@Override public int sourceId() { return sourceId; }

	@Override public boolean expired() { return startNanos >= 0L && ageSec >= durationSec; }

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		if (startNanos < 0L) startNanos = frame.nanos;
		ageSec = (frame.nanos - startNanos) / 1_000_000_000.0f;
		return ageSec >= durationSec;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		if (startNanos < 0L) startNanos = System.nanoTime();
		ageSec = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
		if (ageSec >= durationSec) return;

		if (axeImpact) {
			submitAxeImpact(poseStack, collector, cameraPos);
			return;
		}

		Vec3 headAnchor = resolveHeadAnchor();
		float cx = (float) (headAnchor.x - cameraPos.x);
		float cz = (float) (headAnchor.z - cameraPos.z);

		float dropT = Math.clamp(ageSec / DROP_TIME_SEC, 0f, 1f);
		float dropOffset = (1.0f - dropT * dropT) * 2.55f;
		// Tiny heavy bounce right after hitting the head
		float sinceHit = ageSec - DROP_TIME_SEC;
		float bounce = sinceHit > 0f && sinceHit < 0.18f
				? (float) Math.abs(Math.sin((sinceHit / 0.18f) * Math.PI)) * 0.14f * (1.0f - sinceHit / 0.18f)
				: 0f;

		float fadeIn = Math.min(1.0f, ageSec / 0.04f);
		float fadeOut = 1.0f - smoothstep(0.62f, ANVIL_DURATION_SEC, ageSec);
		float alpha = fadeIn * fadeOut;
		if (alpha <= 0.01f) return;

		float anvilBaseY = (float) (headAnchor.y - cameraPos.y) + 0.06f + dropOffset + bounce;
		float scale = 0.85f + 0.15f * fadeOut;

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			emitVoxelAnvil(pose, vc, cx, anvilBaseY, cz, scale, alpha);
			if (sinceHit >= 0f) {
				emitImpactSparks(pose, vc, cx, (float) (headAnchor.y - cameraPos.y) + 0.05f, cz, sinceHit);
			}
		});

		if (sinceHit >= 0f && sinceHit < 0.35f) {
			float flash = 1.0f - sinceHit / 0.35f;
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) -> {
				WeaponVoxelRenderer.emitGlowSprite(pose, vc,
						cx, (float) (headAnchor.y - cameraPos.y) + 0.12f, cz,
						1.25f * (0.6f + 0.4f * (1f - flash)),
						0.92f, 0.96f, 1.00f, flash * 0.55f);
			});
		}
	}

	private Vec3 resolveHeadAnchor() {
		var mc = Minecraft.getInstance();
		if (mc.level != null && targetId >= 0) {
			Entity target = mc.level.getEntity(targetId);
			if (target != null) {
				return new Vec3(target.getX(), target.getY() + target.getBbHeight(), target.getZ());
			}
		}
		return fallbackPos.add(0, 0.80, 0);
	}

	private void emitVoxelAnvil(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float baseY, float cz, float s, float alpha) {
		float v = 0.095f * s; // voxel half-step grid

		// 1. Wide heavy base foot (5 x 1 x 3 voxels)
		for (int gx = -2; gx <= 2; gx++) {
			for (int gz = -1; gz <= 1; gz++) {
				boolean edge = Math.abs(gx) == 2 || Math.abs(gz) == 1;
				float tone = edge ? 0.24f : 0.32f;
				WeaponVoxelRenderer.emitCube(pose, vc,
						cx + gx * (v * 1.75f), baseY + v * 0.8f, cz + gz * (v * 1.85f),
						v * 0.88f, v * 0.80f, v * 0.92f,
						tone, tone + 0.02f, tone + 0.05f, alpha);
			}
		}

		// 2. Narrow waist/neck (3 x 2 x 1.5 voxels)
		for (int gy = 1; gy <= 2; gy++) {
			for (int gx = -1; gx <= 1; gx++) {
				float tone = (gx == 0) ? 0.30f : 0.22f;
				WeaponVoxelRenderer.emitCube(pose, vc,
						cx + gx * (v * 1.55f), baseY + v * (1.5f + gy * 1.5f), cz,
						v * 0.82f, v * 0.78f, v * 1.35f,
						tone, tone + 0.02f, tone + 0.05f, alpha);
			}
		}

		// 3. Top striking face (6 x 2 x 3 voxels, bright forged steel rim on top)
		for (int gy = 0; gy < 2; gy++) {
			for (int gx = -2; gx <= 3; gx++) {
				for (int gz = -1; gz <= 1; gz++) {
					boolean topLayer = (gy == 1);
					boolean rim = topLayer && (gx == -2 || gx == 3 || Math.abs(gz) == 1);
					float r = rim ? 0.76f : (topLayer ? 0.56f : 0.38f);
					float g = rim ? 0.80f : (topLayer ? 0.60f : 0.41f);
					float b = rim ? 0.88f : (topLayer ? 0.66f : 0.46f);
					WeaponVoxelRenderer.emitCube(pose, vc,
							cx + (gx - 0.35f) * (v * 1.75f), baseY + v * (5.2f + gy * 1.65f), cz + gz * (v * 1.85f),
							v * 0.88f, v * 0.84f, v * 0.92f,
							r, g, b, alpha);
				}
			}
		}

		// 4. Anvil horn protruding from the front (-X side)
		WeaponVoxelRenderer.emitCube(pose, vc,
				cx - 3.3f * (v * 1.75f), baseY + v * 6.5f, cz,
				v * 1.05f, v * 0.75f, v * 1.15f,
				0.64f, 0.68f, 0.75f, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc,
				cx - 4.3f * (v * 1.75f), baseY + v * 6.8f, cz,
				v * 0.75f, v * 0.52f, v * 0.68f,
				0.82f, 0.86f, 0.94f, alpha);
	}

	private void emitImpactSparks(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float y, float cz, float sinceHit) {
		float t = Math.clamp(sinceHit / 0.32f, 0f, 1f);
		float fade = 1.0f - t;
		if (fade <= 0.02f) return;

		int sparks = 14;
		for (int i = 0; i < sparks; i++) {
			float ang = (i / (float) sparks) * (float) (Math.PI * 2.0);
			float r = 0.25f + t * 1.15f;
			float sx = cx + (float) Math.cos(ang) * r;
			float sz = cz + (float) Math.sin(ang) * r;
			float sy = y + (float) Math.sin(t * Math.PI) * 0.28f;

			float size = 0.052f * (1.0f - 0.5f * t);
			boolean whiteSpark = (i % 2 == 0);
			WeaponVoxelRenderer.emitCube(pose, vc, sx, sy, sz, size, size, size,
					whiteSpark ? 0.96f : 1.00f,
					whiteSpark ? 0.98f : 0.65f,
					whiteSpark ? 1.00f : 0.22f,
					fade * 0.92f);
		}
	}

	private void submitAxeImpact(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		float t = Math.clamp(ageSec / AXE_IMPACT_DURATION_SEC, 0f, 1f);
		float fade = 1.0f - t * t;
		if (fade <= 0.02f) return;

		float cx = (float) (fallbackPos.x - cameraPos.x);
		float cy = (float) (fallbackPos.y - cameraPos.y);
		float cz = (float) (fallbackPos.z - cameraPos.z);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			int count = 12;
			for (int i = 0; i < count; i++) {
				float ang = i * 2.39996f;
				float pitch = ((i % 3) - 1) * 0.35f;
				float r = 0.15f + t * 0.85f;
				float vx = cx + (float) Math.cos(ang) * r;
				float vy = cy + pitch * r + 0.12f * (float) Math.sin(t * Math.PI);
				float vz = cz + (float) Math.sin(ang) * r;
				float s = 0.055f * (1.0f - 0.45f * t);
				boolean brown = (i % 3 == 1);
				WeaponVoxelRenderer.emitCube(pose, vc, vx, vy, vz, s, s, s,
						brown ? 0.52f : 1.00f,
						brown ? 0.24f : 0.50f,
						brown ? 0.08f : 0.12f,
						fade * 0.90f);
			}
		});

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) -> {
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, cx, cy, cz, 0.95f * (0.6f + 0.4f * t),
					1.0f, 0.52f, 0.14f, fade * 0.45f);
		});
	}

	private static float smoothstep(float lo, float hi, float x) {
		float t = Math.clamp((x - lo) / (hi - lo), 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
