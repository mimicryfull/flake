package dev.flake.duel;

/** Timing of the Void Paladin crit: live pixel-art crescent slash and bouncing droplets over 0.75 s. */
public final class CritAnimation {
	public static final float DURATION = 0.75f;

	private CritAnimation() {}

	public static float alpha(float age) {
		if (age < 0 || age >= DURATION) return 0;
		float rise = Math.clamp(age / 0.015f, 0, 1);
		float t = Math.clamp((age - 0.55f) / (DURATION - 0.55f), 0, 1);
		float fall = 1 - t * t * (3 - 2 * t);
		return rise * fall;
	}
}
