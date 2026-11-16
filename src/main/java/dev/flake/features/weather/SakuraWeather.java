package dev.flake.features.weather;

import dev.flake.Flake;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Server-authoritative custom weather state persisted per world ({@code <world>/flake/weather.properties})
 * and synchronized to all connected clients.
 */
public final class SakuraWeather {
	public static final float DEFAULT_INTENSITY = 1.0f;
	public static final float MIN_INTENSITY = 0.2f;
	public static final float MAX_INTENSITY = 3.0f;

	public enum Mode {
		NONE("none"),
		SAKURA("sakura");

		private final String id;

		Mode(String id) { this.id = id; }
		public String id() { return id; }

		public static Mode parse(String raw) {
			if (raw == null) return NONE;
			return switch (raw.trim().toLowerCase(Locale.ROOT)) {
				case "sakura", "cherry", "petals" -> SAKURA;
				default -> NONE;
			};
		}
	}

	public record State(Mode mode, float intensity) {
		public State {
			mode = mode == null ? Mode.NONE : mode;
			intensity = clampIntensity(intensity);
		}

		public static final State OFF = new State(Mode.NONE, DEFAULT_INTENSITY);

		public SakuraWeatherPayload toPayload() {
			return new SakuraWeatherPayload(mode.id(), mode == Mode.NONE ? 0.0f : intensity);
		}
	}

	private static final Map<MinecraftServer, State> SERVER_STATES = new ConcurrentHashMap<>();
	private static boolean installed;

	private SakuraWeather() {}

	public static float clampIntensity(float intensity) {
		if (!Float.isFinite(intensity)) return DEFAULT_INTENSITY;
		return Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, intensity));
	}

	public static synchronized void install() {
		if (installed) return;
		installed = true;
		PayloadTypeRegistry.clientboundPlay().register(SakuraWeatherPayload.TYPE, SakuraWeatherPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVER_STATES.put(server, load(storePath(server))));
		ServerLifecycleEvents.SERVER_STOPPED.register(SERVER_STATES::remove);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayer(handler.player, server));
	}

	public static State current(MinecraftServer server) {
		if (server == null) return State.OFF;
		return SERVER_STATES.computeIfAbsent(server, s -> load(storePath(s)));
	}

	public static void set(MinecraftServer server, Mode mode, float intensity) {
		if (server == null) return;
		State updated = new State(mode, clampIntensity(intensity));
		SERVER_STATES.put(server, updated);
		try {
			save(storePath(server), updated);
		} catch (IOException ex) {
			Flake.LOGGER.warn("Failed to persist weather state", ex);
		}
		var payload = updated.toPayload();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, SakuraWeatherPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void syncPlayer(ServerPlayer player, MinecraftServer server) {
		if (ServerPlayNetworking.canSend(player, SakuraWeatherPayload.TYPE)) {
			ServerPlayNetworking.send(player, current(server).toPayload());
		}
	}

	private static Path storePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("flake").resolve("weather.properties");
	}

	public static State load(Path file) {
		if (file == null || Files.notExists(file)) return State.OFF;
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			props.load(in);
		} catch (IOException ex) {
			Flake.LOGGER.warn("Failed to load weather state from {}", file, ex);
			return State.OFF;
		}
		Mode mode = Mode.parse(props.getProperty("mode", "none"));
		float intensity = DEFAULT_INTENSITY;
		String rawIntensity = props.getProperty("intensity");
		if (rawIntensity != null) {
			try {
				intensity = clampIntensity(Float.parseFloat(rawIntensity));
			} catch (NumberFormatException ignored) {}
		}
		return new State(mode, intensity);
	}

	public static void save(Path file, State state) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) Files.createDirectories(parent);
		Properties props = new Properties();
		props.setProperty("version", "1");
		props.setProperty("mode", state.mode().id());
		props.setProperty("intensity", Float.toString(state.intensity()));
		Path tmp = Files.createTempFile(parent, "flake-weather-", ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(tmp)) {
				props.store(out, null);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}
