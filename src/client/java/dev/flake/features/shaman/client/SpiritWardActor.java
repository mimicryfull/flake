package dev.flake.features.shaman.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.shaman.ShamanRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/** Smoothly following emerald-teal 3D voxel Spirit Ward ring (r = 4.0) with rising spirit ember cubes. */
public final class SpiritWardActor implements EffectActor {
	private static final float DURATION_SEC = ShamanRuntime.WARD_TICKS / 20.0f;
	private static final float WARD_RADIUS = (float) ShamanRuntime.WARD_RADIUS;
	private static final int RING_SEGMENTS = 32;
	private static final int EMBER_COUNT = 16;

	private final int sourceId;
	private final VfxTimeline timeline = new VfxTimeline();
	private Vec3 lastOrigin;
	private float ageSec;

	public SpiritWardActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.lastOrigin = payload.origin();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.SHAMAN_WARD;
	}

	@Override
	public boolean expired() {
		return ageSec >= DURATION_SEC;
	}

	@Override public boolean write(RenderFrame frame, SlotWriter slot) { return expired(); }

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		Vec3 center = lastOrigin;
		if (mc.level != null) {
			var entity = mc.level.getEntity(sourceId);
			if (entity != null) {
				float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
				center = entity.getPosition(partial);
				lastOrigin = center;
			}
		}

		double dx = center.x - cameraPos.x;
		double dy = center.y - cameraPos.y;
		double dz = center.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float spawnRaw = Math.clamp(ageSec / 0.18f, 0f, 1f);
		float spawn = spawnRaw * spawnRaw * (3.0f - 2.0f * spawnRaw);
		float tailFade = Math.clamp((DURATION_SEC - ageSec) / 0.35f, 0f, 1f);
		float opacity = spawn * tailFade;
		if (opacity <= 0.01f) return;

		// Subtle 1s heal pulse beat
		float healPhase = ageSec % 1.0f;
		float pulse = Math.max(0.0f, 1.0f - healPhase / 0.38f);
		float radius = WARD_RADIUS * (0.82f + 0.18f * spawn);

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitSpiritRing(pose, vc, radius, pulse, opacity));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitSpiritEmbers(pose, vc, radius, pulse, opacity));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitWardGlows(pose, vc, radius, pulse, opacity));

		poseStack.popPose();
	}

	private void emitSpiritRing(PoseStack.Pose pose, VertexConsumer vc, float radius, float pulse, float opacity) {
		float segStep = (float) (Math.PI * 2.0 / RING_SEGMENTS);
		float spin = ageSec * 0.42f;

		for (int i = 0; i < RING_SEGMENTS; i++) {
			float ang = i * segStep + spin;
			float x = (float) Math.cos(ang) * radius;
			float z = (float) Math.sin(ang) * radius;
			boolean pillar = (i % 4 == 0);
			float wave = (float) Math.sin(ageSec * 3.0f + i * 0.6f) * 0.025f;
			float hy = pillar ? (0.075f + 0.030f * pulse) : 0.024f;
			float w = pillar ? 0.055f : 0.042f;
			float r = pillar ? 0.24f : 0.10f;
			float g = pillar ? (0.94f + 0.06f * pulse) : 0.80f;
			float b = pillar ? 0.78f : 0.64f;
			WeaponVoxelRenderer.emitCube(pose, vc, x, 0.04f + hy + wave, z, w, hy, w, r, g, b, opacity * (pillar ? 0.90f : 0.72f));
		}
	}

	private void emitSpiritEmbers(PoseStack.Pose pose, VertexConsumer vc, float radius, float pulse, float opacity) {
		// Soft inner spirit circle shimmer
		float innerRadius = radius * 0.95f;
		float segStep = (float) (Math.PI * 2.0 / 24);
		float counterSpin = -ageSec * 0.55f;
		for (int i = 0; i < 24; i++) {
			float ang = i * segStep + counterSpin;
			float x = (float) Math.cos(ang) * innerRadius;
			float z = (float) Math.sin(ang) * innerRadius;
			WeaponVoxelRenderer.emitCube(pose, vc, x, 0.028f, z, 0.052f, 0.012f, 0.052f,
					0.16f, 0.90f, 0.74f, opacity * (0.28f + 0.18f * pulse));
		}

		// Gently rising emerald-teal spirit ember cubes across the ward zone
		for (int i = 0; i < EMBER_COUNT; i++) {
			float phase = (ageSec * 0.62f + i / (float) EMBER_COUNT) % 1.0f;
			float ang = i * 2.39996f + ageSec * 0.45f;
			float radialFrac = 0.22f + ((i * 37) % 100) / 100.0f * 0.74f;
			float rDist = radius * radialFrac;
			float ex = (float) Math.cos(ang) * rDist;
			float ez = (float) Math.sin(ang) * rDist;
			float ey = 0.08f + phase * 1.45f;
			float fade = (float) Math.sin(phase * Math.PI);
			float size = (0.024f + (i % 3) * 0.008f) * (0.65f + 0.35f * fade);
			boolean bright = (i % 3 == 0);
			WeaponVoxelRenderer.emitCube(pose, vc, ex, ey, ez, size, size * 1.2f, size,
					bright ? 0.38f : 0.12f,
					bright ? 1.00f : 0.86f,
					bright ? 0.86f : 0.70f,
					opacity * fade * 0.72f);
		}
	}

	private void emitWardGlows(PoseStack.Pose pose, VertexConsumer vc, float radius, float pulse, float opacity) {
		float spin = ageSec * 0.42f;
		float glowAlpha = opacity * (0.24f + 0.22f * pulse);
		float glowRad = 0.42f + 0.16f * pulse;
		for (int i = 0; i < 8; i++) {
			float ang = i * (float) (Math.PI * 0.25) + spin;
			float x = (float) Math.cos(ang) * radius;
			float z = (float) Math.sin(ang) * radius;
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, x, 0.14f, z, glowRad,
					0.16f, 0.96f, 0.76f, glowAlpha);
		}
	}
}
