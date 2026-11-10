package dev.flake.duel;

/** Compact hammer-contact burst: a short, tight pressure shell instead of the wide shockwave ring. */
public final class HammerAnimation {
	public static final float DURATION = 0.36f;
	public static final float RADIUS = 1.15f;

	private HammerAnimation() {}

	public static float radius(float age) {
		if (age <= 0f) return 0.12f;
		float grow = (1f - (float) Math.exp(-age * 17f)) * RADIUS;
		float impact = (float) Math.exp(-Math.pow((age - 0.075f) / 0.045f, 2)) * 0.14f;
		return 0.12f + grow + impact;
	}

	public static float opacity(float age) {
		if (age <= 0f || age >= DURATION) return 0f;
		float in = Math.clamp(age / 0.03f, 0f, 1f);
		float out = 1f - smoothstep(0.16f, DURATION, age);
		return in * out;
	}

	private static float smoothstep(float edge0, float edge1, float value) {
		float t = Math.clamp((value - edge0) / (edge1 - edge0), 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
