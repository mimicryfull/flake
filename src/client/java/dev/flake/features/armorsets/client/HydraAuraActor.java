package dev.flake.features.armorsets.client;

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
import net.minecraft.world.phys.Vec3;

/**
 * Red & dark-red Hydra regeneration aura (style = 29) + local first-person frenzy indicator
 * for the 20% opacity burgundy overlay.
 */
public final class HydraAuraActor implements EffectActor {
	private static final float LEASE_TIMEOUT_SEC = 1.8f;
	private static final float FADE_OUT_SEC = 0.25f;
	private static final float AURA_RADIUS = 2.6f;

	private static volatile int localPlayerFrenzySourceId = -1;
	private static volatile long localFrenzyExpiresNano;

	private final int sourceId;
	private final GroundAuraTracker tracker;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float lastRefreshSec;
	private float fadeStartSec = -1f;
	private boolean frenzy;

	public HydraAuraActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.tracker = new GroundAuraTracker(sourceId, payload.origin());
		this.frenzy = payload.yaw() > 0.5f;
		updateLocalOverlay();
	}

	public static boolean isLocalFrenzyActive(int playerId) {
		return localPlayerFrenzySourceId == playerId && System.nanoTime() < localFrenzyExpiresNano;
	}

	public static void clearLocalOverlay() {
		localPlayerFrenzySourceId = -1;
		localFrenzyExpiresNano = 0L;
	}

	private void updateLocalOverlay() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.player.getId() == sourceId) {
			if (frenzy && fadeStartSec < 0f) {
				localPlayerFrenzySourceId = sourceId;
				localFrenzyExpiresNano = System.nanoTime() + 1_800_000_000L;
			} else if (localPlayerFrenzySourceId == sourceId) {
				clearLocalOverlay();
			}
		}
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId) return false;
		if (payload.phase() == CombatVfxPayload.Phase.HYDRA_AURA) {
			lastRefreshSec = ageSec;
			fadeStartSec = -1f;
			frenzy = payload.yaw() > 0.5f;
			updateLocalOverlay();
			return true;
		}
		if (payload.phase() == CombatVfxPayload.Phase.HYDRA_AURA_OFF) {
			if (fadeStartSec < 0f) fadeStartSec = ageSec;
			if (localPlayerFrenzySourceId == sourceId) clearLocalOverlay();
			return true;
		}
		return false;
	}

	@Override
	public boolean expired() {
		boolean done = fadeStartSec >= 0f
				? (ageSec - fadeStartSec >= FADE_OUT_SEC)
				: (ageSec - lastRefreshSec >= LEASE_TIMEOUT_SEC);
		if (done && localPlayerFrenzySourceId == sourceId) clearLocalOverlay();
		return done;
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
		float[] values = slot.values;
		values[slot.position] = (float) (center.x - frame.camera.x);
		values[slot.position + 1] = (float) (tracker.groundY() - frame.camera.y);
		values[slot.position + 2] = (float) (center.z - frame.camera.z);
		values[slot.position + 3] = frenzy ? AURA_RADIUS : 1.75f;

		values[slot.style] = computeOpacity();
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 29.0f;
		values[slot.style + 3] = frenzy ? 1.0f : 0.0f;
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

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitBloodVoxels(pose, vc, opacity));
		poseStack.popPose();
	}

	private void emitBloodVoxels(PoseStack.Pose pose, VertexConsumer vc, float opacity) {
		int count = frenzy ? 16 : 6;
		float speed = frenzy ? 0.85f : 0.45f;
		for (int i = 0; i < count; i++) {
			float phase = (ageSec * speed + i / (float) count) % 1.0f;
			float ang = i * 2.39996f + ageSec * (frenzy ? 1.65f : 0.85f);
			float radius = frenzy ? (0.70f + 0.55f * (i % 3) / 2.0f) : 0.72f;
			float x = (float) Math.cos(ang) * radius;
			float z = (float) Math.sin(ang) * radius;
			float y = 0.10f + phase * 1.60f;
			float fade = (float) Math.sin(phase * Math.PI);
			float size = (frenzy ? 0.028f : 0.018f) + (i % 2) * 0.008f;
			boolean darkRed = (i % 2 == 0);
			WeaponVoxelRenderer.emitCube(pose, vc, x, y, z, size, size * 1.25f, size,
					darkRed ? 0.46f : 0.88f,
					darkRed ? 0.03f : 0.08f,
					darkRed ? 0.08f : 0.14f,
					opacity * fade * (frenzy ? 0.82f : 0.45f));
		}
	}
}
