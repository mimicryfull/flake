package dev.flake.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ArenaTest {
	@TempDir Path directory;

	private Arena arena() {
		return new Arena("minecraft:overworld", new Arena.Bounds(-10, 60, -10, 10, 80, 10),
				new Arena.Position(-9.5, 64, 0.5, 90, 0), new Arena.Position(10.9, 64, 0.5, -90, 0),
				new Arena.Position(30, 64, 30, 0, 0));
	}

	@Test
	void savesAndLoadsArena() throws IOException {
		var store = new ArenaStore(directory.resolve("flake/arena.properties"));
		assertTrue(store.load().isEmpty());
		store.save(arena());
		assertEquals(arena(), store.load().orElseThrow());
		store.save(arena());
		assertEquals(arena(), store.load().orElseThrow());
	}

	@Test
	void rejectsOutOfBoundsAndNonFinitePositions() {
		var bounds = arena().bounds();
		assertFalse(bounds.contains(new Arena.Position(11, 64, 0, 0, 0)));
		assertThrows(IllegalArgumentException.class, () -> new Arena("minecraft:overworld", bounds,
				new Arena.Position(11, 64, 0, 0, 0), arena().secondSpawn(), arena().lobby()));
		assertThrows(IllegalArgumentException.class, () -> new Arena.Position(Double.NaN, 0, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Arena.Bounds(1, 0, 0, 0, 0, 0));
	}

	@Test
	void rejectsMalformedConfigurationWithoutOverwritingIt() throws IOException {
		Path file = directory.resolve("arena.properties");
		Files.writeString(file, "version=1\ndimension=minecraft:overworld\n");
		assertThrows(IOException.class, () -> new ArenaStore(file).load());
		assertEquals("version=1\ndimension=minecraft:overworld\n", Files.readString(file));
	}
}
