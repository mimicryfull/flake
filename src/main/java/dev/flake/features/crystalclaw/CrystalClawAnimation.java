package dev.flake.features.crystalclaw;

/** Intensity envelope of the Crystal Claw frost VFX: crisp shard entry, shimmering plateau, soft shatter-out. Visual only. */
public final class CrystalClawAnimation {
	public static final float ENTER = 0.16f, EXIT = 0.42f;
	/** Fallback end if the server's OFF message is lost; matches the server buff length. */
	public static final float BUFF_SECONDS = CrystalClawRuntime.BUFF_TICKS / 20.0f;

	private CrystalClawAnimation() {}

	public static float intensity(float age, float offAt) {
		if (age < 0) return 0;
		float enter = Math.clamp(age / ENTER, 0, 1);
		enter = enter * enter * (3 - 2 * enter);
		// Two slow shimmer frequencies read as light refracting inside ice.
		float shimmer = 1 + 0.055f * (float) Math.sin(age * 3.1) + 0.03f * (float) Math.sin(age * 7.7);
		float exit = Math.clamp((age - offAt) / EXIT, 0, 1);
		exit = 1 - exit * exit * (3 - 2 * exit);
		return enter * shimmer * exit * 0.94f;
	}

	/** Activation prismatic burst: ~40 ms snap to full strength, then an ease-out over ~0.5 s. */
	public static final float BURST_RISE = 0.04f, BURST_FALL = 0.50f;

	public static float burst(float age) {
		if (age < 0 || age >= BURST_RISE + BURST_FALL) return 0;
		if (age < BURST_RISE) { float t = age / BURST_RISE; return t * t; }
		float t = (age - BURST_RISE) / BURST_FALL;
		float u = 1 - t;
		return u * u * u;
	}

	public static boolean finished(float age, float offAt) { return age >= offAt + EXIT; }
}
