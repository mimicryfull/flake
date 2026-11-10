package dev.flake.duel;

/** A bounded perception impulse, not camera shake. Consecutive hits restart, never accumulate. */
public final class ImpactAnimation {
	public static final float DURATION = 0.6f;
	private ImpactAnimation() {}
	public static float envelope(float seconds) {
		if (seconds < 0 || seconds >= DURATION) return 0;
		float rise = Math.clamp(seconds / 0.045f, 0, 1);
		float fade = Math.clamp((seconds - 0.12f) / (DURATION - 0.12f), 0, 1);
		return rise * rise * (3 - 2 * rise) * (1 - fade * fade * (3 - 2 * fade));
	}
	public static float displacement(float seconds) {
		float envelope = envelope(seconds);
		if (envelope == 0) return 0;
		return envelope * (float) (Math.cos(Math.max(0, seconds - 0.045) * 19) * Math.exp(-4 * Math.max(0, seconds - 0.045)));
	}
}
