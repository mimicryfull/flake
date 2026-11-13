package dev.flake.features.chaosbroadsword.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/**
 * Client-side procedural spin-sweep pose tracker for Chaos Broadsword.
 * Plants both feet wide, holds the broadsword forward two-handed, and whips through a 360-degree spin.
 */
public final class BroadswordSpinPose {
	private static final float DURATION_SEC = 0.68f;
	private static final Map<Integer, Long> ACTIVE = new ConcurrentHashMap<>();

	private BroadswordSpinPose() {}

	public static void start(int entityId) {
		if (entityId < 0) return;
		ACTIVE.put(entityId, System.nanoTime());
	}

	/** Envelope [0..1]: fast brace into stance -> hold during spin -> smooth recovery. */
	public static float weight(int entityId) {
		float elapsed = elapsedSec(entityId);
		if (elapsed < 0f) return 0f;
		float in = smoothstep(0.0f, 0.08f, elapsed);
		float out = 1.0f - smoothstep(0.48f, DURATION_SEC, elapsed);
		return in * out;
	}

	/** Normalized progress [0..1] of the full 360-degree torso spin. */
	public static float spinProgress(int entityId) {
		float elapsed = elapsedSec(entityId);
		if (elapsed < 0f) return 0f;
		float t = Math.clamp((elapsed - 0.04f) / 0.44f, 0f, 1f);
		// Fast explosive start with crisp follow-through
		return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
	}

	public static float localFovBoost() {
		var player = Minecraft.getInstance().player;
		if (player == null) return 0f;
		return weight(player.getId()) * 0.055f;
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
