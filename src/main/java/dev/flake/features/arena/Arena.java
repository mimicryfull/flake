package dev.flake.features.arena;

import java.util.Objects;

public record Arena(String dimension, Bounds bounds, Position firstSpawn, Position secondSpawn, Position lobby) {
	public Arena {
		Objects.requireNonNull(dimension);
		Objects.requireNonNull(bounds);
		Objects.requireNonNull(firstSpawn);
		Objects.requireNonNull(secondSpawn);
		Objects.requireNonNull(lobby);
		if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid dimension identifier");
		if (!bounds.contains(firstSpawn) || !bounds.contains(secondSpawn)) throw new IllegalArgumentException("Spawn must be inside arena");
	}

	public record Position(double x, double y, double z, float yaw, float pitch) {
		public Position {
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
					|| !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Position must be finite");
		}
	}

	public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		public Bounds {
			if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Invalid arena bounds");
		}

		public boolean contains(Position position) {
			return position.x() >= minX && position.x() < (double) maxX + 1
					&& position.y() >= minY && position.y() < (double) maxY + 1
					&& position.z() >= minZ && position.z() < (double) maxZ + 1;
		}
	}
}
