package dev.flake.features.chronotrident;

/** Shared timing and expansion curves for the Chrono Trident's purple Chronosphere dome. */
public final class ChronosphereAnimation {
	public static final float DURATION_SECONDS = 3.5f;
	public static final float MAX_RADIUS = 4.75f;
	private static final float EXPAND_END = 0.28f;
	private static final float COLLAPSE_START = 3.08f;

	private ChronosphereAnimation() {}

	public static float radius(float ageSeconds) {
		float t = Math.max(0.0f, ageSeconds);
		if (t < EXPAND_END) {
			float u = t / EXPAND_END;
			// Explosive damped spring launch into full Chronosphere dome
			float spring = 1.0f - (float) (Math.exp(-8.5 * u) * (Math.cos(14.0 * u) + (8.5 / 14.0) * Math.sin(14.0 * u)));
			return 0.25f + (MAX_RADIUS - 0.25f) * Math.clamp(spring, 0.0f, 1.0f);
		}
		if (t >= COLLAPSE_START) {
			float u = Math.clamp((t - COLLAPSE_START) / (DURATION_SECONDS - COLLAPSE_START), 0.0f, 1.0f);
			float shrink = 1.0f - u * u * (0.4f + 0.6f * u);
			return Math.max(0.05f, MAX_RADIUS * shrink);
		}
		// Restrained gravitational breathing hum while active
		float breathe = (float) Math.sin((t - EXPAND_END) * 3.1f) * 0.06f;
		return MAX_RADIUS + breathe;
	}

	public static float opacity(float ageSeconds) {
		if (ageSeconds < 0.0f || ageSeconds >= DURATION_SECONDS) return 0.0f;
		float fadeIn = smoothstep(0.0f, 0.06f, ageSeconds);
		float fadeOut = 1.0f - smoothstep(COLLAPSE_START + 0.06f, DURATION_SECONDS, ageSeconds);
		return fadeIn * fadeOut;
	}

	public static float collapseProgress(float ageSeconds) {
		if (ageSeconds <= COLLAPSE_START) return 0.0f;
		return Math.clamp((ageSeconds - COLLAPSE_START) / (DURATION_SECONDS - COLLAPSE_START), 0.0f, 1.0f);
	}

	private static float smoothstep(float low, float high, float value) {
		float t = Math.clamp((value - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
