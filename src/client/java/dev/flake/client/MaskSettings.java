package dev.flake.client;

import dev.flake.features.matadora.MatadoraRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Client-only tuning of the MatadoraRuntime overlay (percent, 0..200). Persisted in config/flake-client.properties. */
public final class MaskSettings {
	public static final int[] STEPS = {0, 50, 100, 150, 200};
	public static int density = 100, noise = 100, distortion = 100;
	private static final java.nio.file.Path FILE = FabricLoader.getInstance().getConfigDir().resolve("flake-client.properties");

	private MaskSettings() {}

	public static void load() {
		if (!Files.exists(FILE)) return;
		try (var in = Files.newInputStream(FILE)) {
			var p = new Properties(); p.load(in);
			density = read(p, "mask.density"); noise = read(p, "mask.noise"); distortion = read(p, "mask.distortion");
		} catch (IOException | NumberFormatException ignored) { }
	}

	private static int read(Properties p, String key) { return Math.clamp(Integer.parseInt(p.getProperty(key, "100")), 0, 200); }

	public static int next(int value) {
		for (int step : STEPS) if (step > value) return step;
		return STEPS[0];
	}

	public static void save() {
		var p = new Properties();
		p.setProperty("mask.density", Integer.toString(density));
		p.setProperty("mask.noise", Integer.toString(noise));
		p.setProperty("mask.distortion", Integer.toString(distortion));
		try (var out = Files.newOutputStream(FILE)) { p.store(out, "Flake client settings"); } catch (IOException ignored) { }
	}
}
