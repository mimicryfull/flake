package dev.flake.features.chronotrident.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Springy-bouncy animation pose for Chrono Trident activation:
 * lifts and flips the trident upside-down, then slams it prongs-first into the ground
 * with an elastic bounce before settling back.
 */
public final class ChronoPlantPose {
	private static final float WINDUP_END = 0.18f;
	private static final float HOLD_END = 0.58f;
	private static final float DURATION_SEC = 0.88f;
	private static final Map<Integer, Long> ACTIVE = new ConcurrentHashMap<>();

	private ChronoPlantPose() {}

	public static void start(int entityId) {
		if (entityId < 0) return;
		ACTIVE.put(entityId, System.nanoTime());
	}

	/** Overall pose blend envelope [0..1]. */
	public static float weight(int entityId) {
		float elapsed = elapsedSec(entityId);
		if (elapsed < 0f) return 0f;
		float in = smoothstep(0.0f, 0.06f, elapsed);
		float out = 1.0f - smoothstep(HOLD_END, DURATION_SEC, elapsed);
		return in * out;
	}

	/** Windup hoist [0..1] peaking at WINDUP_END before the downward slam. */
	public static float lift(int entityId) {
		float elapsed = elapsedSec(entityId);
		if (elapsed < 0f || elapsed >= WINDUP_END + 0.08f) return 0f;
		float up = smoothstep(0.0f, 0.11f, elapsed);
		float down = 1.0f - smoothstep(WINDUP_END - 0.02f, WINDUP_END + 0.08f, elapsed);
		return up * down;
	}

	/**
	 * Springy-bouncy slam depth: 0 during windup, overshoots to ~1.33 on impact
	 * and bounces elastically back to 1.0 before returning cleanly.
	 */
	public static float slamSpring(int entityId) {
		float elapsed = elapsedSec(entityId);
		if (elapsed <= WINDUP_END) return 0f;
		float dt = elapsed - WINDUP_END;
		float spring = 1.0f - (float) (Math.exp(-7.2 * dt) * Math.cos(19.5 * dt));
		float release = 1.0f - smoothstep(HOLD_END + 0.04f, DURATION_SEC, elapsed);
		return Math.max(0f, spring) * release;
	}

	/** Upside-down flip progress [0..1] (smoothly flips prongs down, then returns at end). */
	public static float flipProgress(int entityId) {
		float elapsed = elapsedSec(entityId);
		if (elapsed < 0f) return 0f;
		float flip = smoothstep(0.01f, WINDUP_END, elapsed);
		float unflip = 1.0f - smoothstep(HOLD_END + 0.05f, DURATION_SEC, elapsed);
		return flip * unflip;
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

	private static float smoothstep(float low, float high, float x) {
		float t = Math.clamp((x - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
