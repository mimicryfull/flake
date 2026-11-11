package dev.flake.features.concussed;

/** Visual timing is independent of the server's knockback and cooldown. */
public final class ShockwaveAnimation {
	public static final float DURATION_SECONDS = 0.85f;
	public static final int MAX_WAVES = 4;
	public static final float VFX_DURATION_SECONDS = EnergyMotes.DURATION;

	private ShockwaveAnimation() {}

	public static float chargeProgress(float seconds) {
		return Math.clamp(seconds / (Shockwave.CHARGE_TICKS / 20.0f), 0, 1);
	}

	public static float chargeRadius(float seconds) {
		float t = chargeProgress(seconds);
		if (t < 0.48f) {
			float spring = spring(t / 0.48f, 7, 13);
			return 0.18f + 1.42f * spring;
		}
		float collapse = Math.clamp((t - 0.48f) / 0.48f, 0, 1);
		float remaining = 1 - collapse * collapse * collapse;
		return 0.045f + 1.55f * remaining * remaining;
	}

	public static float radius(float seconds, float maximum) {
		float t = Math.max(0, seconds);
		// Damped spring: an explosive launch, recoil, then settle. The visible boundary
		// never exceeds the actual ability radius, unlike a generic overshooting ease.
		float expansion = Math.clamp(spring(t, 12, 25), 0, 1);
		return 0.045f + (maximum - 0.045f) * expansion;
	}

	public static float opacity(float seconds) {
		if (seconds < 0 || seconds >= DURATION_SECONDS) return 0;
		return smoothstep(0, 0.012f, seconds) * (1 - smoothstep(0.16f, DURATION_SECONDS, seconds));
	}

	private static float spring(float t, float damping, float frequency) {
		return (float) (1 - Math.exp(-damping * t)
				* (Math.cos(frequency * t) + damping / frequency * Math.sin(frequency * t)));
	}

	private static float smoothstep(float low, float high, float value) {
		float t = Math.clamp((value - low) / (high - low), 0, 1);
		return t * t * (3 - 2 * t);
	}
}
