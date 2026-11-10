package dev.flake.arena;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ArenaRepositoryTest {
	private final Arena.Position lobby = new Arena.Position(0, 64, 0, 0, 0);

	private Arena arena(String dimension) {
		return new Arena(dimension, new Arena.Bounds(-8, 60, -8, 8, 70, 8),
				new Arena.Position(-4, 64, 0, 90, 0), new Arena.Position(4, 64, 0, -90, 0), lobby);
	}

	@Test void namedArenasRoundTripIndependently(@TempDir Path world) throws Exception {
		var repository = new ArenaRepository(world);
		repository.save("arena_one", arena("minecraft:overworld"));
		repository.save("arena_two", arena("minecraft:the_nether"));
		assertEquals(java.util.List.of("arena_one", "arena_two"), repository.names());
		assertEquals("minecraft:overworld", repository.load("arena_one").orElseThrow().dimension());
		assertEquals("minecraft:the_nether", repository.load("arena_two").orElseThrow().dimension());
	}

	@Test void defaultWritesTheLegacyFileSoOldWorldsKeepWorking(@TempDir Path world) throws Exception {
		var repository = new ArenaRepository(world);
		repository.save(ArenaRepository.DEFAULT, arena("minecraft:overworld"));
		assertTrue(Files.exists(world.resolve("arena.properties")));
		assertEquals("minecraft:overworld", repository.load(ArenaRepository.DEFAULT).orElseThrow().dimension());
	}

	@Test void legacyFileIsVisibleAsDefaultWithoutMigration(@TempDir Path world) throws Exception {
		Files.createDirectories(world);
		new ArenaStore(world.resolve("arena.properties")).save(arena("minecraft:overworld"));
		var repository = new ArenaRepository(world);
		assertEquals(java.util.List.of("default"), repository.names());
		assertTrue(repository.load("default").isPresent());
	}

	@Test void namesAreValidatedAndDefaultCannotBeDeleted(@TempDir Path world) throws Exception {
		var repository = new ArenaRepository(world);
		repository.save("custom", arena("minecraft:overworld"));
		assertFalse(repository.delete(ArenaRepository.DEFAULT));
		assertTrue(repository.delete("custom"));
		assertTrue(repository.names().isEmpty());
		assertFalse(ArenaRepository.validName("Bad Name"));
		assertFalse(ArenaRepository.validName(""));
		assertTrue(ArenaRepository.validName("arena_2-b"));
	}
}