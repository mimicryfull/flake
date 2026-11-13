package dev.flake.features.chaosbroadsword.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/**
 * Heavy two-handed spin-sweep pose tracker for Chaos Broadsword.
 * Builds windup weight, leans deep into the ground, drives a heavy 360-degree spin, and settles smoothly.
 */
public final class BroadswordSpinPose {
	private static final float DURATION_SEC = 0.82f;
	private static final Map<Integer, Long> ACTIVE = new ConcurrentHashMap<>();

	private BroadswordSpinPose() {}

	public static void start(int entityId) {
		if (entityId < 0) return;
		ACTIVE.put(entityId, System.nanoTime());
	}

	/** Heavy ease-in/ease-out stance envelope [0..1]. */
	public static float weight(int entityId) {
		float elapsed = elapsedSec(entityId);
		if (elapsed < 0f) return 0f;
		float in = smoothstep(0.0f, 0.13f, elapsed);
		float out = 1.0f - smoothstep(0.56f, DURATION_SEC, elapsed);
		return in * out;
	}

	/**
	 * Spin angle in degrees: brief heavy windup counter-turn (+28 deg),
	 * then weighty momentum whip through -360 deg.
	 */
	public static float spinDegrees(int entityId) {
		float elapsed = elapsedSec(entityId);
		if (elapsed < 0f) return 0f;
		if (elapsed < 0.13f) {
			float w = smoothstep(0.0f, 0.13f, elapsed);
			return 28.0f * w;
		}
		float t = Math.clamp((elapsed - 0.13f) / 0.45f, 0f, 1f);
		// Heavy start -> fast mid-swing -> weighty deceleration
		float eased = t * t * (3f - 2f * t);
		return 28.0f - 388.0f * eased;
	}

	/** Normalized swing progress [0..1] for first-person slash travel. */
	public static float swingProgress(int entityId) {
		float elapsed = elapsedSec(entityId);
		if (elapsed < 0f) return 0f;
		if (elapsed < 0.13f) {
			return -0.22f * smoothstep(0.0f, 0.13f, elapsed);
		}
		float t = Math.clamp((elapsed - 0.13f) / 0.45f, 0f, 1f);
		float eased = t * t * (3f - 2f * t);
		return -0.22f + 1.22f * eased;
	}

	public static float localFovBoost() {
		var player = Minecraft.getInstance().player;
		if (player == null) return 0f;
		return weight(player.getId()) * 0.06f;
	}

	private static float elapsedSec(int entityId) {
		Long start = ACTIVE.get(entityId);
		if (start == null) return -1f;
		float elapsed = (System.nanoTime() - start) / 1_000_000_000.0f;
		if (elapsed < 0f || elapsed >= DURATION_SEC) {
			ACTIVE.remove(entityId, start);
			return -1f;
		}
		return elapsed;
	}

	private static float smoothstep(float lo, float hi, float x) {
		float t = Math.clamp((x - lo) / (hi - lo), 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
