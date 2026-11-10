package dev.flake.duel;

/** Visual timing for Bastion's two actives. Purely presentational; never feeds back into combat. */
public final class BastionAnimation {
	private BastionAnimation() {}

	/** Cinematic overlap only: compression never delays the authoritative dash or damage. */
	public static final class Ram {
		public static final float IMPACT_START = 0.10f;
		public static final float DURATION = 1.08f;

		private Ram() {}

		public static float impact(float age) {
			if (age < IMPACT_START) return 0;
			return (1 - smoothstep(IMPACT_START, IMPACT_START + 0.13f, age));
		}

		public static float suppression(float age) {
			if (age < IMPACT_START || age >= DURATION) return 0;
			float rise = smoothstep(IMPACT_START, IMPACT_START + 0.04f, age);
			float fall = 1 - smoothstep(DURATION - 0.34f, DURATION, age);
			return rise * fall;
		}

		public static boolean finished(float age) { return age >= DURATION; }
	}

	/** Bulwark: heavy lock-in, stable defensive plateau, then a slow fade rather than a hard cut. */
	public static final class Bulwark {
		public static final float ENTER = 0.38f;
		public static final float EXIT = 0.62f;
		public static final float BUFF_SECONDS = 80 / 20.0f;

		private Bulwark() {}

		public static float intensity(float age, float offAt) {
			if (age < 0) return 0;
			// Keep arriving fragments visible, rather than fading the entire assembly in late.
			float enter = smoothstep(0, 0.045f, age);
			float breath = 1 + 0.035f * (float) Math.sin(age * 2.1) + 0.018f * (float) Math.sin(age * 5.3 + 1.2);
			float exit = 1 - smoothstep(offAt, offAt + EXIT, age);
			return enter * breath * exit;
		}

		public static float burst(float age) {
			return smoothstep(0.23f, 0.28f, age) * (1 - smoothstep(0.28f, 0.48f, age));
		}

		public static float hit(float age) {
			if (age < 0 || age >= 0.46f) return 0;
			float rise = smoothstep(0, 0.025f, age);
			float t = age / 0.46f;
			float fall = 1 - t * t * (3 - 2 * t);
			return rise * fall;
		}

		public static boolean finished(float age, float offAt) { return age >= offAt + EXIT; }
	}

	/** Damped second-order step; stagger is applied before sampling. GLSL uses the same curve. */
	public static float assembly(float progress) {
		if (progress <= 0) return 0;
		if (progress >= 1) return 1;
		return 1 - (float) (Math.exp(-8 * progress) * (Math.cos(11 * progress) + 0.5 * Math.sin(11 * progress)));
	}

	private static float smoothstep(float low, float high, float value) {
		float t = Math.clamp((value - low) / (high - low), 0, 1);
		return t * t * (3 - 2 * t);
	}
}
