package dev.flake.features.junglespear.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/**
 * Client-side procedural lunge pose tracker for Jungle Spear dashes.
 * Computes a smooth attack-lunge envelope (fast snap in, hold through dash, smooth stand-up recovery).
 */
public final class SpearLungePose {
	private static final float DURATION_SEC = 0.68f;
	private static final Map<Integer, Long> ACTIVE = new ConcurrentHashMap<>();

	private SpearLungePose() {}

	public static void start(int entityId) {
		if (entityId < 0) return;
		ACTIVE.put(entityId, System.nanoTime());
	}

	public static float weight(int entityId) {
		Long start = ACTIVE.get(entityId);
		if (start == null) return 0f;

		float elapsed = (System.nanoTime() - start) / 1_000_000_000.0f;
		if (elapsed < 0f || elapsed >= DURATION_SEC) {
			ACTIVE.remove(entityId, start);
			return 0f;
		}

		// 0.00 -> 0.11s: snap into lunge; 0.11 -> 0.42s: hold flight pose; 0.42 -> 0.68s: smoothly stand back up
		float in = smoothstep(0.0f, 0.11f, elapsed);
		float out = 1.0f - smoothstep(0.42f, DURATION_SEC, elapsed);
		return in * out;
	}

	public static float localFovBoost() {
		var player = Minecraft.getInstance().player;
		if (player == null) return 0f;
		return weight(player.getId()) * 0.075f;
	}

	private static float smoothstep(float lo, float hi, float x) {
		float t = Math.clamp((x - lo) / (hi - lo), 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
