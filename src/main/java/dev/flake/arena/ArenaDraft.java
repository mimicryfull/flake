package dev.flake.arena;

public final class ArenaDraft {
	private String dimension;
	private BlockPosition firstCorner;
	private BlockPosition secondCorner;
	private Arena.Position firstSpawn;
	private Arena.Position secondSpawn;
	private Arena.Position lobby;

	public void setCorner(String dimension, BlockPosition corner, boolean first) {
		checkDimension(dimension);
		if (first) firstCorner = corner; else secondCorner = corner;
	}

	public void setPosition(String dimension, Arena.Position position, Point point) {
		checkDimension(dimension);
		switch (point) {
			case FIRST -> firstSpawn = position;
			case SECOND -> secondSpawn = position;
			case LOBBY -> lobby = position;
		}
	}

	private void checkDimension(String value) {
		if (dimension != null && !dimension.equals(value)) throw new IllegalArgumentException("Усі точки мають бути в одному вимірі. Почни нову чернетку.");
		dimension = value;
	}

	public Arena build() {
		if (dimension == null || firstCorner == null || secondCorner == null || firstSpawn == null || secondSpawn == null || lobby == null)
			throw new IllegalStateException("Задай обидва кути, обидва спавни та лобі.");
		var bounds = new Arena.Bounds(Math.min(firstCorner.x(), secondCorner.x()), Math.min(firstCorner.y(), secondCorner.y()),
				Math.min(firstCorner.z(), secondCorner.z()), Math.max(firstCorner.x(), secondCorner.x()),
				Math.max(firstCorner.y(), secondCorner.y()), Math.max(firstCorner.z(), secondCorner.z()));
		return new Arena(dimension, bounds, firstSpawn, secondSpawn, lobby);
	}

	public enum Point { FIRST, SECOND, LOBBY }
	public record BlockPosition(int x, int y, int z) {}
}
