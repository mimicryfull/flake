package dev.flake.features.matadora;

/** Intensity envelope of the MatadoraRuntime dark-focus VFX: quick entry, living plateau, soft exit. Visual only. */
public final class MaskAnimation {
	public static final float ENTER = 0.22f, EXIT = 0.45f;
	/** Fallback end if the server's OFF message is lost; matches the server buff length. */
	public static final float BUFF_SECONDS = MatadoraRuntime.BUFF_TICKS / 20.0f;

	private MaskAnimation() {}

	public static float intensity(float age, float offAt) {
		if (age < 0) return 0;
		float enter = Math.clamp(age / ENTER, 0, 1);
		enter = 1 - (1 - enter) * (1 - enter) * (1 - enter);
		float breath = 1 + 0.05f * (float) Math.sin(age * 2.4);
		float exit = Math.clamp((age - offAt) / EXIT, 0, 1);
		exit = 1 - exit * exit * (3 - 2 * exit);
		return enter * breath * exit * 0.96f;
	}

	/** Activation chromatic-aberration burst: ~50 ms snap to full strength, then a smooth ease-out over ~0.55 s. */
	public static final float BURST_RISE = 0.05f, BURST_FALL = 0.55f;

	public static float burst(float age) {
		if (age < 0 || age >= BURST_RISE + BURST_FALL) return 0;
		if (age < BURST_RISE) { float t = age / BURST_RISE; return t * t; }
		float t = (age - BURST_RISE) / BURST_FALL;
		float u = 1 - t;
		return u * u * u;
	}

	public static boolean finished(float age, float offAt) { return age >= offAt + EXIT; }
}
