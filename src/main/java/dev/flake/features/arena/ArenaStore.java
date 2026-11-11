package dev.flake.features.arena;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Objects;
import java.util.Properties;

public final class ArenaStore {
	private final Path file;

	public ArenaStore(Path file) {
		this.file = file.toAbsolutePath();
	}

	public Optional<Arena> load() throws IOException {
		if (Files.notExists(file)) return Optional.empty();
		Properties values = new Properties();
		try (InputStream input = Files.newInputStream(file)) {
			values.load(input);
		}
		try {
			if (!"1".equals(values.getProperty("version"))) throw new IllegalArgumentException("Unsupported arena format");
			var bounds = new Arena.Bounds(integer(values, "minX"), integer(values, "minY"), integer(values, "minZ"),
					integer(values, "maxX"), integer(values, "maxY"), integer(values, "maxZ"));
			return Optional.of(new Arena(required(values, "dimension"), bounds,
					position(values, "first"), position(values, "second"), position(values, "lobby")));
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new IOException("Invalid arena configuration: " + file, exception);
		}
	}

	public void save(Arena arena) throws IOException {
		Properties values = new Properties();
		values.setProperty("version", "1");
		values.setProperty("dimension", arena.dimension());
		var bounds = arena.bounds();
		values.setProperty("minX", Integer.toString(bounds.minX()));
		values.setProperty("minY", Integer.toString(bounds.minY()));
		values.setProperty("minZ", Integer.toString(bounds.minZ()));
		values.setProperty("maxX", Integer.toString(bounds.maxX()));
		values.setProperty("maxY", Integer.toString(bounds.maxY()));
		values.setProperty("maxZ", Integer.toString(bounds.maxZ()));
		putPosition(values, "first", arena.firstSpawn());
		putPosition(values, "second", arena.secondSpawn());
		putPosition(values, "lobby", arena.lobby());
		Files.createDirectories(file.getParent());
		Path temporary = Files.createTempFile(file.getParent(), "flake-arena-", ".tmp");
		try {
			try (OutputStream output = Files.newOutputStream(temporary)) {
				values.store(output, null);
			}
			Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static String required(Properties values, String key) {
		return Objects.requireNonNull(values.getProperty(key), "Missing " + key);
	}

	private static int integer(Properties values, String key) {
		return Integer.parseInt(required(values, key));
	}

	private static Arena.Position position(Properties values, String prefix) {
		return new Arena.Position(Double.parseDouble(required(values, prefix + ".x")),
				Double.parseDouble(required(values, prefix + ".y")), Double.parseDouble(required(values, prefix + ".z")),
				Float.parseFloat(required(values, prefix + ".yaw")), Float.parseFloat(required(values, prefix + ".pitch")));
	}

	private static void putPosition(Properties values, String prefix, Arena.Position position) {
		values.setProperty(prefix + ".x", Double.toString(position.x()));
		values.setProperty(prefix + ".y", Double.toString(position.y()));
		values.setProperty(prefix + ".z", Double.toString(position.z()));
		values.setProperty(prefix + ".yaw", Float.toString(position.yaw()));
		values.setProperty(prefix + ".pitch", Float.toString(position.pitch()));
	}
}
