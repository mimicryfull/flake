package dev.flake.duel;

/** Presentation math only. No damage, eligibility, cooldown or healing calculations. */
public final class GhoulAnimation {
	public static final float BUFF_SECONDS = 100 / 20f;
	public static final float EXIT = 0.38f;
	public static final float LUNGE_SECONDS = 0.82f;
	public static final float CONTACT_DELAY = 0.055f;
	private GhoulAnimation() {}

	public static float smooth(float low, float high, float value) {
		float t = Math.clamp((value - low) / (high - low), 0, 1);
		return t * t * (3 - 2 * t);
	}

	public static float compression(float age) {
		if (age < 0) return 0;
		return smooth(0, 0.018f, age) * (1 - smooth(0.065f, 0.14f, age));
	}

	public static float aura(float age, float offAt) {
		if (age < 0) return 0;
		return smooth(0, 0.09f, age) * (1 - smooth(offAt, offAt + EXIT, age));
	}

	public static float flash(float age) {
		if (age < CONTACT_DELAY) return 0;
		return 1 - smooth(CONTACT_DELAY, CONTACT_DELAY + 0.075f, age);
	}

	public static float siphonDuration(double distance) {
		return (float) Math.clamp(0.15 + Math.max(0, distance) * 0.024, 0.15, 0.35);
	}

	public static float absorption(float age, float duration) {
		return smooth(duration - 0.025f, duration + 0.018f, age)
				* (1 - smooth(duration + 0.045f, duration + 0.23f, age));
	}

	public static boolean confirmedHealing(boolean bloodlust, float taken, float before, float after) {
		return bloodlust && taken > 0 && Float.isFinite(after) && after > before;
	}

	/** Fixed-size timestamped world-space history. Samples interpolate time, not render-frame indices. */
	public static final class Trail {
		public static final int CAPACITY = 16;
		public static final double STEP = 0.025;
		private final double[] x = new double[CAPACITY], y = new double[CAPACITY], z = new double[CAPACITY], time = new double[CAPACITY];
		private double lastX, lastY, lastZ, lastTime, nextTime;
		private int head;
		private boolean initialized;

		public void reset(double now, double px, double py, double pz) {
			for (int i = 0; i < CAPACITY; i++) { x[i] = px; y[i] = py; z[i] = pz; time[i] = now; }
			lastX = px; lastY = py; lastZ = pz; lastTime = now; nextTime = now + STEP;
			head = 0; initialized = true;
		}

		public void update(double now, double px, double py, double pz) {
			double dx = px - lastX, dy = py - lastY, dz = pz - lastZ;
			if (!initialized || now - lastTime > 0.3 || dx * dx + dy * dy + dz * dz > 36) { reset(now, px, py, pz); return; }
			if (now <= lastTime) return;
			while (nextTime <= now) {
				double t = (nextTime - lastTime) / (now - lastTime);
				head = (head + 1) % CAPACITY;
				x[head] = lastX + dx * t; y[head] = lastY + dy * t; z[head] = lastZ + dz * t; time[head] = nextTime;
				nextTime += STEP;
			}
			lastTime = now; lastX = px; lastY = py; lastZ = pz;
		}

		/** xyz + sampled timestamp into a caller-owned scratch array. No per-sample allocations. */
		public void sample(double at, double[] out) {
			if (at >= time[head]) {
				double d = lastTime - time[head], t = d > 0 ? Math.clamp((at - time[head]) / d, 0, 1) : 0;
				out[0] = x[head] + (lastX - x[head]) * t; out[1] = y[head] + (lastY - y[head]) * t;
				out[2] = z[head] + (lastZ - z[head]) * t; out[3] = Math.min(at, lastTime); return;
			}
			for (int i = 0; i < CAPACITY - 1; i++) {
				int newer = Math.floorMod(head - i, CAPACITY), older = Math.floorMod(newer - 1, CAPACITY);
				if (time[older] > at) continue;
				double span = time[newer] - time[older], t = span > 0 ? Math.clamp((at - time[older]) / span, 0, 1) : 0;
				out[0] = x[older] + (x[newer] - x[older]) * t; out[1] = y[older] + (y[newer] - y[older]) * t;
				out[2] = z[older] + (z[newer] - z[older]) * t; out[3] = time[older] + span * t; return;
			}
			int oldest = (head + 1) % CAPACITY;
			out[0] = x[oldest]; out[1] = y[oldest]; out[2] = z[oldest]; out[3] = time[oldest];
		}
	}
}
