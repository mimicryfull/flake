package dev.flake.duel;

/** Timing of the Void Paladin crit: the cut snaps in and fades by ~0.42 s; blood keeps falling and settles until ~0.7 s. */
public final class CritAnimation {
	public static final float DURATION = 0.7f;

	private CritAnimation() {}

	public static float alpha(float age) {
		if (age < 0 || age >= DURATION) return 0;
		float rise = Math.clamp(age / 0.015f, 0, 1);
		float t = Math.clamp((age - 0.35f) / (DURATION - 0.35f), 0, 1);
		float fall = 1 - t * t * (3 - 2 * t);
		return rise * fall;
	}
}
