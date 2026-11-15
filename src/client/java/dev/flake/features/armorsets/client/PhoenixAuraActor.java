package dev.flake.features.armorsets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.armorsets.PhoenixArmor;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Yellow-orange Phoenix ground aura (style = 27) with gentle swirling voxels that stay non-obstructive.
 */
public final class PhoenixAuraActor implements EffectActor {
	private static final float LEASE_TIMEOUT_SEC = 1.8f;
	private static final float FADE_OUT_SEC = 0.25f;
	private static final float RADIUS = (float) PhoenixArmor.AURA_RADIUS;
	private static final int EMBER_COUNT = 10;

	private final int sourceId;
	private final GroundAuraTracker tracker;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float lastRefreshSec;
	private float fadeStartSec = -1f;
	private boolean empowered;

	public PhoenixAuraActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.tracker = new GroundAuraTracker(sourceId, payload.origin());
		this.empowered = payload.yaw() > 0.5f;
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId) return false;
		if (payload.phase() == CombatVfxPayload.Phase.PHOENIX_AURA) {
			lastRefreshSec = ageSec;
			fadeStartSec = -1f;
			empowered = payload.yaw() > 0.5f;
			return true;
		}
		if (payload.phase() == CombatVfxPayload.Phase.PHOENIX_AURA_OFF) {
			if (fadeStartSec < 0f) fadeStartSec = ageSec;
			return true;
		}
		return false;
	}

	@Override
	public boolean expired() {
		if (fadeStartSec >= 0f) return ageSec - fadeStartSec >= FADE_OUT_SEC;
		return ageSec - lastRefreshSec >= LEASE_TIMEOUT_SEC;
	}

	private float computeOpacity() {
		float spawn = Math.clamp(ageSec / 0.20f, 0f, 1f);
		float opacity = spawn * spawn * (3.0f - 2.0f * spawn);
		if (fadeStartSec >= 0f) {
			opacity *= Math.clamp(1.0f - (ageSec - fadeStartSec) / FADE_OUT_SEC, 0f, 1f);
		} else {
			float stale = ageSec - lastRefreshSec;
			if (stale > 1.35f) opacity *= Math.clamp(1.0f - (stale - 1.35f) / 0.45f, 0f, 1f);
		}
		return opacity;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		Vec3 center = tracker.resolve(frame.client.level, frame.partial);
		float groundY = tracker.groundY();

		float[] values = slot.values;
		values[slot.position] = (float) (center.x - frame.camera.x);
		values[slot.position + 1] = (float) (groundY - frame.camera.y);
		values[slot.position + 2] = (float) (center.z - frame.camera.z);
		values[slot.position + 3] = RADIUS;

		values[slot.style] = computeOpacity();
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 27.0f;
		values[slot.style + 3] = empowered ? 1.0f : 0.0f;
		return false;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 center = tracker.resolve(mc.level, partial);
		double dx = center.x - cameraPos.x;
		double dy = center.y - cameraPos.y;
		double dz = center.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 56.0 * 56.0) return;

		float opacity = computeOpacity();
		if (opacity <= 0.01f) return;

		float groundDy = (float) (tracker.groundY() - cameraPos.y);
		poseStack.pushPose();
		poseStack.translate((float) dx, groundDy, (float) dz);
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitRingEmbers(pose, vc, opacity));
		poseStack.popPose();
	}

	private void emitRingEmbers(PoseStack.Pose pose, VertexConsumer vc, float opacity) {
		int count = empowered ? EMBER_COUNT + 4 : EMBER_COUNT;
		float cycle = (ageSec % 2.0f) / 2.0f;
		float pulse = Math.max(0f, 1.0f - cycle / 0.35f);
		for (int i = 0; i < count; i++) {
			float phase = (ageSec * (empowered ? 0.78f : 0.56f) + i / (float) count) % 1.0f;
			float ang = i * 2.39996f + ageSec * 0.95f;
			float dist = 1.25f + (RADIUS - 1.35f) * (0.45f + 0.55f * (i % 3) / 2.0f);
			float x = (float) Math.cos(ang) * dist;
			float z = (float) Math.sin(ang) * dist;
			float y = 0.06f + phase * 0.72f;
			float fade = (float) Math.sin(phase * Math.PI);
			float size = (0.022f + (i % 2) * 0.008f) * (0.80f + 0.25f * pulse);
			boolean gold = (i % 2 == 0);
			WeaponVoxelRenderer.emitCube(pose, vc, x, y, z, size, size * 1.2f, size,
					1.00f,
					gold ? 0.78f : 0.42f,
					gold ? 0.16f : 0.06f,
					opacity * fade * 0.65f);
		}
	}
}
