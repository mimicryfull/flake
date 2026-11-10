package dev.flake.duel;

import java.util.Optional;

public final class Shockwave {
	public static final double RADIUS = 5.0;
	public static final double STRENGTH = 1.2;
	public static final double UPWARD = 0.35;
	public static final int COOLDOWN_TICKS = 60;
	public static final int CHARGE_TICKS = 22;

	private Shockwave() {}

	public static Optional<Impulse> impulse(double dx, double dy, double dz) {
		if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return Optional.empty();
		if (dx * dx + dy * dy + dz * dz > RADIUS * RADIUS) return Optional.empty();
		double horizontal = Math.hypot(dx, dz);
		if (horizontal < 1.0e-6) return Optional.of(new Impulse(0, UPWARD, 0));
		return Optional.of(new Impulse(dx / horizontal * STRENGTH, UPWARD, dz / horizontal * STRENGTH));
	}

	public record Impulse(double x, double y, double z) {}
}
