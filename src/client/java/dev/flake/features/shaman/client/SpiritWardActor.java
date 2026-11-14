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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Emerald Spirit Ward aura (style = 19): smoothly tracks entity XZ and finds stable ground Y across
 * the player's footprint so jumps & ledge transitions look clean without flickering.
 */
public final class SpiritWardActor implements EffectActor {
	private static final float DURATION_SEC = ShamanRuntime.WARD_TICKS / 20.0f;
	private static final float WARD_RADIUS = (float) ShamanRuntime.WARD_RADIUS;
	private static final int EMBER_COUNT = 14;
	private static final double[] SAMPLE_OFFSETS = {0.0, 0.0, 0.26, 0.26, -0.26, 0.26, 0.26, -0.26, -0.26, -0.26};

	private final int sourceId;
	private final VfxTimeline timeline = new VfxTimeline();
	private Vec3 lastOrigin;
	private float smoothedGroundY;
	private float ageSec;

	public SpiritWardActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.lastOrigin = payload.origin();
		this.smoothedGroundY = (float) payload.origin().y;
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

	private Vec3 resolveCenter(Level level, float partial) {
		if (level != null) {
			Entity entity = level.getEntity(sourceId);
			if (entity != null) {
				lastOrigin = entity.getPosition(partial);
				float targetGroundY;
				if (entity.onGround()) {
					targetGroundY = (float) lastOrigin.y;
				} else {
					double bestY = Double.NEGATIVE_INFINITY;
					for (int i = 0; i < SAMPLE_OFFSETS.length; i += 2) {
						Vec3 probe = lastOrigin.add(SAMPLE_OFFSETS[i], 0.35, SAMPLE_OFFSETS[i + 1]);
						BlockHitResult hit = level.clip(new ClipContext(
								probe, probe.subtract(0.0, 6.0, 0.0),
								ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
						if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y <= lastOrigin.y + 0.15) {
							bestY = Math.max(bestY, hit.getLocation().y);
						}
					}
					targetGroundY = bestY > Double.NEGATIVE_INFINITY ? (float) bestY : (float) lastOrigin.y;
				}
				smoothedGroundY += (targetGroundY - smoothedGroundY) * 0.35f;
			}
		}
		return lastOrigin;
	}

	private float computeOpacity() {
		float spawnRaw = Math.clamp(ageSec / 0.18f, 0f, 1f);
		float spawn = spawnRaw * spawnRaw * (3.0f - 2.0f * spawnRaw);
		float tailFade = Math.clamp((DURATION_SEC - ageSec) / 0.35f, 0f, 1f);
		return spawn * tailFade;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		Vec3 center = resolveCenter(frame.client.level, frame.partial);
		float opacity = computeOpacity();
		float spawnRaw = Math.clamp(ageSec / 0.18f, 0f, 1f);
		float radius = WARD_RADIUS * (0.85f + 0.15f * spawnRaw);
		float jumpHeight = Math.max(0.0f, (float) (center.y - smoothedGroundY));

		float[] values = slot.values;
		values[slot.position] = (float) (center.x - frame.camera.x);
		values[slot.position + 1] = (float) (smoothedGroundY - frame.camera.y);
		values[slot.position + 2] = (float) (center.z - frame.camera.z);
		values[slot.position + 3] = radius;

		values[slot.style] = opacity;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 19.0f;
		values[slot.style + 3] = jumpHeight;
		return false;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 center = resolveCenter(mc.level, partial);
		double dx = center.x - cameraPos.x;
		double dy = center.y - cameraPos.y;
		double dz = center.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float opacity = computeOpacity();
		if (opacity <= 0.01f) return;

		float healPhase = ageSec % 1.0f;
		float pulse = Math.max(0.0f, 1.0f - healPhase / 0.38f);
		float groundDy = (float) (smoothedGroundY - cameraPos.y);

		poseStack.pushPose();
		poseStack.translate((float) dx, groundDy, (float) dz);
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitGroundEmbers(pose, vc, pulse, opacity));
		poseStack.popPose();

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitBodyWisps(pose, vc, pulse, opacity));
		poseStack.popPose();
	}

	private void emitGroundEmbers(PoseStack.Pose pose, VertexConsumer vc, float pulse, float opacity) {
		for (int i = 0; i < 8; i++) {
			float ang = i * (float) (Math.PI * 0.25) + ageSec * 0.55f;
			float x = (float) Math.cos(ang) * WARD_RADIUS;
			float z = (float) Math.sin(ang) * WARD_RADIUS;
			float h = 0.045f + 0.025f * pulse;
			WeaponVoxelRenderer.emitCube(pose, vc, x, h, z, 0.040f, h, 0.040f,
					0.24f, 0.96f, 0.52f, opacity * 0.65f);
		}
	}

	private void emitBodyWisps(PoseStack.Pose pose, VertexConsumer vc, float pulse, float opacity) {
		for (int i = 0; i < EMBER_COUNT; i++) {
			float phase = (ageSec * 0.72f + i / (float) EMBER_COUNT) % 1.0f;
			float ang = i * 2.39996f + ageSec * 1.45f;
			float rDist = 0.62f + 0.28f * (1.0f - phase * 0.5f);
			float ex = (float) Math.cos(ang) * rDist;
			float ez = (float) Math.sin(ang) * rDist;
			float ey = 0.12f + phase * 1.65f;
			float fade = (float) Math.sin(phase * Math.PI);
			float size = (0.022f + (i % 2) * 0.008f) * (0.70f + 0.30f * pulse);
			boolean bright = (i % 3 == 0);
			WeaponVoxelRenderer.emitCube(pose, vc, ex, ey, ez, size, size * 1.25f, size,
					bright ? 0.58f : 0.16f,
					bright ? 1.00f : 0.92f,
					bright ? 0.74f : 0.48f,
					opacity * fade * 0.74f);
		}
	}
}
