package dev.flake.features.chaosbroadsword;

/** Visual timing for the broadsword sweep, independent of server damage. */
public final class ChaosBroadswordAnimation {
	public static final float DURATION = 0.52f;
	public static final float RADIUS = 4.5f;

	private ChaosBroadswordAnimation() {}

	public static float progress(float seconds) {
		return Math.clamp(seconds / DURATION, 0f, 1f);
	}

	public static float radius(float seconds) {
		if (seconds <= 0f) return 0.1f;
		float expansion = (float) (1.0 - Math.exp(-seconds * 13.0));
		return 0.1f + (RADIUS - 0.1f) * expansion;
	}

	public static float opacity(float seconds) {
		if (seconds < 0f || seconds >= DURATION) return 0f;
		float appear = Math.clamp(seconds / 0.03f, 0f, 1f);
		float fade = 1f - smoothstep(0.30f, DURATION, seconds);
		return appear * fade;
	}

	private static float smoothstep(float low, float high, float value) {
		float t = Math.clamp((value - low) / (high - low), 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
