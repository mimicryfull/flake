package dev.flake.duel;

/** Timing and easing of the Cube of Darkness VFX: contraction → shot → impact. Visual only. */
public final class DarkBoltAnimation {
	public static final float CHARGE = 0.14f, SHOT = 0.16f, HIT = 0.55f;
	public static final float DURATION = CHARGE + SHOT + HIT;
	public static final int FRAGMENTS = 8;

	private DarkBoltAnimation() {}

	/** Radius of the dark orb at the cube: quick swell, then a cubic squeeze right before the release. */
	public static float orbRadius(float age) {
		if (age < 0 || age >= CHARGE + 0.04f) return 0;
		float t = Math.clamp(age / CHARGE, 0, 1);
		float swell = 1 - (1 - Math.min(1, t * 3)) * (1 - Math.min(1, t * 3));
		float squeeze = t * t * t;
		float release = Math.clamp((age - CHARGE) / 0.04f, 0, 1);
		return (0.07f + 0.30f * swell * (1 - squeeze) + 0.06f * squeeze) * (1 - release);
	}

	/** 0..1 position of the head along the curved path; accelerates sharply (ease-in quart). */
	public static float head(float age) {
		float t = Math.clamp((age - CHARGE) / SHOT, 0, 1);
		return t * t * t * t * (2 - t) * 0.999f + (t >= 1 ? 0.001f : 0);
	}

	/** Tail follows with a delay and eases in-out so the short trail shrinks away. */
	public static float tail(float age) {
		float t = Math.clamp((age - CHARGE - 0.04f) / (SHOT + 0.10f), 0, 1);
		return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}

	/** 0 before impact, then normalized 0..1 impact time. */
	public static float hit(float age) {
		float t = (age - CHARGE - SHOT) / HIT;
		return t <= 0 || t >= 1 ? 0 : Math.max(t, 0.001f);
	}

	public static float easeOutCubic(float t) {
		float u = 1 - Math.clamp(t, 0, 1);
		return 1 - u * u * u;
	}
}
