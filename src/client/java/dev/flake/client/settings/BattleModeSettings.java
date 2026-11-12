package dev.flake.client.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Persistent toggle for Flake's battle HUD (PvP dock, skills, items, statuses, build summary). */
public final class BattleModeSettings {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("flake-battlemode.properties");
	private static boolean enabled = true;

	private BattleModeSettings() {}

	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
		save();
	}

	public static boolean toggle() {
		enabled = !enabled;
		save();
		return enabled;
	}

	public static void load() {
		if (!Files.exists(FILE)) return;
		try (var in = Files.newInputStream(FILE)) {
			var p = new Properties();
			p.load(in);
			enabled = Boolean.parseBoolean(p.getProperty("battle.mode", "true"));
		} catch (IOException ignored) { }
	}

	public static void save() {
		var p = new Properties();
		p.setProperty("battle.mode", Boolean.toString(enabled));
		try (var out = Files.newOutputStream(FILE)) {
			p.store(out, "Flake battle mode settings");
		} catch (IOException ignored) { }
	}
}
