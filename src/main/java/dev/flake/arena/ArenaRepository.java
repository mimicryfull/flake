package dev.flake.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Named arenas for the whole world: {@code <world>/flake/arenas/<name>.properties}.
 * The legacy {@code <world>/flake/arena.properties} is exposed as {@code default} for compatibility.
 */
public final class ArenaRepository {
	public static final String DEFAULT = "default";
	private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,24}");

	private final Path root;

	public ArenaRepository(Path flakedir) { this.root = flakedir.toAbsolutePath(); }

	public static ArenaRepository forServer(MinecraftServer server) {
		return new ArenaRepository(server.getWorldPath(LevelResource.ROOT).resolve("flake"));
	}

	public static boolean validName(String name) {
		return name != null && NAME.matcher(name.toLowerCase(Locale.ROOT)).matches();
	}

	private Path namedPath(String name) { return root.resolve("arenas").resolve(name.toLowerCase(Locale.ROOT) + ".properties"); }
	private Path legacyPath() { return root.resolve("arena.properties"); }

	public List<String> names() {
		List<String> names = new ArrayList<>();
		if (Files.exists(legacyPath())) names.add(DEFAULT);
		Path directory = root.resolve("arenas");
		if (Files.isDirectory(directory)) {
			try (var stream = Files.list(directory)) {
				stream.filter(path -> path.getFileName().toString().endsWith(".properties"))
						.map(path -> path.getFileName().toString().replace(".properties", ""))
						.filter(ArenaRepository::validName)
						.sorted(Comparator.naturalOrder())
						.forEach(names::add);
			} catch (IOException ignored) { }
		}
		return names;
	}

	public Optional<Arena> load(String name) throws IOException {
		if (name == null || name.isBlank()) name = DEFAULT;
		Path file = DEFAULT.equalsIgnoreCase(name) ? (Files.exists(legacyPath()) ? legacyPath() : namedPath(DEFAULT)) : namedPath(name);
		return new ArenaStore(file).load();
	}

	/** {@code default} keeps writing the legacy file so existing worlds and commands keep working. */
	public void save(String name, Arena arena) throws IOException {
		if (!validName(name)) throw new IllegalArgumentException("Invalid arena name");
		new ArenaStore(DEFAULT.equalsIgnoreCase(name) ? legacyPath() : namedPath(name)).save(arena);
	}

	public boolean delete(String name) throws IOException {
		if (!validName(name) || DEFAULT.equalsIgnoreCase(name)) return false;
		return Files.deleteIfExists(namedPath(name));
	}

	/** Keeps the legacy command target working while new rooms can pick any named arena. */
	public Path legacyFile() { return legacyPath(); }
}