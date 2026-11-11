package dev.flake.render.vfx;

/** Starts on first presentation, then uses uncapped elapsed time (not rendered frame count). */
public final class VfxTimeline {
	private long previous;
	private boolean started;
	private double seconds;

	public void reset() { started = false; seconds = 0; }
	public float sample(long now, boolean paused) {
		if (started && !paused) seconds += Math.max(0L, now - previous) / 1_000_000_000.0;
		started = true;
		previous = now;
		return (float) seconds;
	}
}
