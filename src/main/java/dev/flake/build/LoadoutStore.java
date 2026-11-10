package dev.flake.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** Per-player build persistence: {@code <world>/flake/builds/<uuid>.properties}, atomic v1 files. */
public final class LoadoutStore {
	public enum State { MISSING, LOADED, CORRUPT }

	public record Result(State state, Loadout build) {
		public static final Result MISSING = new Result(State.MISSING, null);
		public static final Result CORRUPT = new Result(State.CORRUPT, null);
	}

	private final Path directory;

	public LoadoutStore(Path directory) {
		this.directory = directory.toAbsolutePath();
	}

	private Path file(UUID player) {
		return directory.resolve(player + ".properties");
	}

	public Optional<Loadout> load(UUID player) {
		return Optional.ofNullable(loadResult(player).build());
	}

	/** Distinguishes "no file yet" from "unreadable file" so the UI can tell the truth. */
	public Result loadResult(UUID player) {
		Path file = file(player);
		if (Files.notExists(file)) return Result.MISSING;
		try (InputStream input = Files.newInputStream(file)) {
			Properties values = new Properties();
			values.load(input);
			if (!"1".equals(values.getProperty("version"))) return Result.CORRUPT;
			var build = new Loadout(
					Loadout.FighterClass.valueOf(required(values, "class")),
					Loadout.ArmorMaterial.valueOf(required(values, "helmet")),
					Loadout.ArmorMaterial.valueOf(required(values, "chestplate")),
					Loadout.ArmorMaterial.valueOf(required(values, "leggings")),
					Loadout.ArmorMaterial.valueOf(required(values, "boots")),
					Loadout.Weapon.valueOf(required(values, "weapon")),
					Loadout.PassiveItem.valueOf(required(values, "passive")),
					Loadout.ActiveItem.valueOf(required(values, "active1")),
					Loadout.ActiveItem.valueOf(required(values, "active2")));
			return build.errors().isEmpty() ? new Result(State.LOADED, build) : Result.CORRUPT;
		} catch (IOException | IllegalArgumentException | NullPointerException invalid) {
			return Result.CORRUPT;
		}
	}

	public boolean save(UUID player, Loadout build) throws IOException {
		Properties values = new Properties();
		values.setProperty("version", "1");
		values.setProperty("class", build.fighterClass().name());
		values.setProperty("helmet", build.helmet().name());
		values.setProperty("chestplate", build.chestplate().name());
		values.setProperty("leggings", build.leggings().name());
		values.setProperty("boots", build.boots().name());
		values.setProperty("weapon", build.weapon().name());
		values.setProperty("passive", build.passiveItem().name());
		values.setProperty("active1", build.activeItem1().name());
		values.setProperty("active2", build.activeItem2().name());
		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory, "flake-build-", ".tmp");
		try {
			try (OutputStream output = Files.newOutputStream(temporary)) {
				values.store(output, null);
			}
			Files.move(temporary, file(player), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary);
		}
		return true;
	}

	private static String required(Properties values, String key) {
		return values.getProperty(key);
	}
}