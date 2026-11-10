package dev.flake.duel;

import java.util.Optional;

/**
 * Directional ground-hugging electric earthquake for the {@code CONCUSSED} class.
 * Damage grows with forward distance; knockback stays inverse-distance but is deliberately restrained.
 */
public final class ElectricQuake {
	public static final double MIN_FORWARD = 0.35;
	public static final double RANGE = 9.0;
	public static final double HALF_WIDTH = 1.85;
	public static final double VERTICAL_TOLERANCE = 2.2;

	public static final float MAX_DAMAGE = 9.0f;
	public static final float MIN_DAMAGE = 2.5f;

	public static final double MAX_STRENGTH = 0.18;
	public static final double MIN_STRENGTH = 0.07;
	public static final double MAX_UPWARD = 0.12;
	public static final double MIN_UPWARD = 0.05;

	public static final int COOLDOWN_TICKS = 80;
	public static final int PROPAGATION_TICKS = 10;
	public static final float SHADER_DURATION_SECONDS = 1.10f;

	private ElectricQuake() {}

	public static Optional<Hit> evaluate(double dirX, double dirZ, double offsetX, double offsetY, double offsetZ) {
		if (!Double.isFinite(dirX) || !Double.isFinite(dirZ)
				|| !Double.isFinite(offsetX) || !Double.isFinite(offsetY) || !Double.isFinite(offsetZ)) {
			return Optional.empty();
		}
		double dirLen = Math.hypot(dirX, dirZ);
		double fx = dirLen < 1.0e-6 ? 0.0 : dirX / dirLen;
		double fz = dirLen < 1.0e-6 ? 1.0 : dirZ / dirLen;

		double forward = offsetX * fx + offsetZ * fz;
		double lateral = Math.abs(-offsetX * fz + offsetZ * fx);

		if (forward < MIN_FORWARD || forward > RANGE) return Optional.empty();
		if (lateral > HALF_WIDTH || Math.abs(offsetY) > VERTICAL_TOLERANCE) return Optional.empty();

		double distanceRatio = Math.clamp((forward - MIN_FORWARD) / (RANGE - MIN_FORWARD), 0.0, 1.0);
		float damage = MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * powerAtDistance(forward);
		double strength = MAX_STRENGTH - distanceRatio * (MAX_STRENGTH - MIN_STRENGTH);
		double upward = MAX_UPWARD - distanceRatio * (MAX_UPWARD - MIN_UPWARD);

		return Optional.of(new Hit(forward, lateral, distanceRatio, damage, fx * strength, upward, fz * strength));
	}

	/** Normalize the playable corridor so its nearest valid point is exactly 2.5 HP. */
	public static float powerAtDistance(double distance) {
		double t = Math.clamp((distance - MIN_FORWARD) / (RANGE - MIN_FORWARD), 0.0, 1.0);
		return (float) (t * Math.sqrt(t));
	}

	public static float wavefrontDistance(float ageSeconds) {
		float t = Math.clamp(ageSeconds / (PROPAGATION_TICKS / 20.0f), 0.0f, 1.0f);
		float eased = t * t * (3.0f - 2.0f * t);
		return (float) (MIN_FORWARD + (RANGE - MIN_FORWARD) * eased);
	}

	public static float shaderOpacity(float ageSeconds) {
		if (ageSeconds < 0.0f || ageSeconds >= SHADER_DURATION_SECONDS) return 0.0f;
		float fadeIn = smoothstep(0.0f, 0.03f, ageSeconds);
		float fadeOut = 1.0f - smoothstep(0.38f, SHADER_DURATION_SECONDS, ageSeconds);
		return fadeIn * fadeOut;
	}

	private static float smoothstep(float low, float high, float value) {
		float t = Math.clamp((value - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	public record Hit(
			double forwardDistance,
			double lateralDistance,
			double distanceRatio,
			float damage,
			double impulseX,
			double impulseY,
			double impulseZ
	) {}
}
