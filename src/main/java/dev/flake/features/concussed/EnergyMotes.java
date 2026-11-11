package dev.flake.features.concussed;

/** Bounded, deterministic 3D ejecta. Analytic drag avoids simulation and frame-rate dependence. */
public final class EnergyMotes {
	public static final int COUNT = 12;
	public static final float DURATION = 1.15f;
	private static final double DRAG = 3.8;
	private static final double[] SPEEDS = new double[COUNT];
	private static final float[] LIFETIMES = new float[COUNT];
	private static final float[] SIZES = new float[COUNT];
	static {
		for (int i = 0; i < COUNT; i++) {
			SPEEDS[i] = 11.0 + 8.0 * variation(i);
			LIFETIMES[i] = 0.65f + 0.5f * (float) variation(i + 4);
			SIZES[i] = 0.025f + 0.027f * (float) variation(i + 7);
		}
	}
	private EnergyMotes() {}

	public static double directionY(int i) { return 1.0 - (i + 0.5) * 2.0 / COUNT; }
	public static double angle(int i, int seed) { return i * 2.3999632297 + seed * 0.6180339887; }
	public static double speed(int i) { return SPEEDS[i]; }
	public static float lifetime(int i) { return LIFETIMES[i]; }
	public static float size(int i) { return SIZES[i]; }
	private static double variation(int i) { return 0.5 + 0.5 * Math.sin(i * 7.13 + 1.7); }
	public static double distance(int i, float seconds) {
		return speed(i) * travel(seconds);
	}
	public static double travel(float seconds) { return -Math.expm1(-DRAG * Math.max(0, seconds)) / DRAG; }
	public static float opacity(int i, float seconds) {
		if (seconds <= 0 || seconds >= lifetime(i)) return 0;
		float t = seconds / lifetime(i);
		float fade = 1 - t * t * (3 - 2 * t);
		return Math.min(1, seconds / 0.025f) * fade;
	}
}
