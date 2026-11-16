package dev.flake.features.weather;

import dev.flake.Flake;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Server-authoritative region-scoped sakura weather persisted per world ({@code <world>/flake/weather.properties})
 * and synchronized to connected clients. Weather petals only appear inside configured regions (spheres or boxes).
 */
public final class SakuraWeather {
	public static final String DEFAULT_REGION_NAME = "sakura";
	public static final float DEFAULT_INTENSITY = 1.0f;
	public static final float MIN_INTENSITY = 0.2f;
	public static final float MAX_INTENSITY = 3.0f;
	public static final float MIN_RADIUS = 2.0f;
	public static final float MAX_RADIUS = 256.0f;

	public enum Shape {
		SPHERE("sphere"),
		BOX("box");

		private final String id;

		Shape(String id) { this.id = id; }
		public String id() { return id; }

		public static Shape parse(String raw) {
			if (raw == null) return SPHERE;
			return switch (raw.trim().toLowerCase(Locale.ROOT)) {
				case "box", "cuboid", "cube" -> BOX;
				default -> SPHERE;
			};
		}
	}

	public record Region(
			String name,
			String dimension,
			Shape shape,
			double x1,
			double y1,
			double z1,
			double x2,
			double y2,
			double z2,
			float radius,
			float intensity) {

		public Region {
			name = normalizeName(name);
			if (name.isEmpty()) name = DEFAULT_REGION_NAME;
			dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension.trim();
			shape = shape == null ? Shape.SPHERE : shape;
			intensity = clampIntensity(intensity);
			if (shape == Shape.SPHERE) {
				radius = clampRadius(radius);
				x2 = x1;
				y2 = y1;
				z2 = z1;
			} else {
				radius = 0.0f;
				double minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
				double minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
				double minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
				x1 = minX; y1 = minY; z1 = minZ;
				x2 = maxX; y2 = maxY; z2 = maxZ;
			}
		}

		public static Region sphere(String name, String dimension, double cx, double cy, double cz, float radius, float intensity) {
			return new Region(name, dimension, Shape.SPHERE, cx, cy, cz, cx, cy, cz, radius, intensity);
		}

		public static Region box(String name, String dimension, double x1, double y1, double z1,
				double x2, double y2, double z2, float intensity) {
			return new Region(name, dimension, Shape.BOX, x1, y1, z1, x2, y2, z2, 0.0f, intensity);
		}

		public Region withIntensity(float newIntensity) {
			return new Region(name, dimension, shape, x1, y1, z1, x2, y2, z2, radius, newIntensity);
		}

		public double minX() { return shape == Shape.SPHERE ? x1 - radius : x1; }
		public double minY() { return shape == Shape.SPHERE ? y1 - radius : y1; }
		public double minZ() { return shape == Shape.SPHERE ? z1 - radius : z1; }
		public double maxX() { return shape == Shape.SPHERE ? x1 + radius : x2; }
		public double maxY() { return shape == Shape.SPHERE ? y1 + radius : y2; }
		public double maxZ() { return shape == Shape.SPHERE ? z1 + radius : z2; }

		/** Squared Euclidean distance from {@code (px, py, pz)} to this region surface ({@code 0} if inside). */
		public double distanceSqToPoint(double px, double py, double pz) {
			if (shape == Shape.SPHERE) {
				double dx = px - x1;
				double dy = py - y1;
				double dz = pz - z1;
				double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
				double out = Math.max(0.0, dist - radius);
				return out * out;
			}
			double dx = px < x1 ? x1 - px : (px > x2 ? px - x2 : 0.0);
			double dy = py < y1 ? y1 - py : (py > y2 ? py - y2 : 0.0);
			double dz = pz < z1 ? z1 - pz : (pz > z2 ? pz - z2 : 0.0);
			return dx * dx + dy * dy + dz * dz;
		}

		/** Smooth {@code 0..1} alpha weight inside the region with soft boundary fade and {@code 0} outside. */
		public float weightAt(double px, double py, double pz) {
			if (shape == Shape.SPHERE) {
				double dx = px - x1;
				double dy = py - y1;
				double dz = pz - z1;
				double distSq = dx * dx + dy * dy + dz * dz;
				if (distSq >= (double) radius * radius) return 0.0f;
				float dist = (float) Math.sqrt(distSq);
				float fade = Math.min(4.0f, Math.max(0.5f, radius * 0.25f));
				float t = Mth.clamp((radius - dist) / fade, 0.0f, 1.0f);
				return t * t * (3.0f - 2.0f * t);
			}
			double mx = Math.min(px - x1, x2 - px);
			double my = Math.min(py - y1, y2 - py);
			double mz = Math.min(pz - z1, z2 - pz);
			if (mx <= 0.0 || my <= 0.0 || mz <= 0.0) return 0.0f;
			float wx = axisFade(mx, x2 - x1);
			float wy = axisFade(my, y2 - y1);
			float wz = axisFade(mz, z2 - z1);
			return wx * wy * wz;
		}

		private static float axisFade(double margin, double extent) {
			if (extent <= 0.0) return 0.0f;
			double fade = Math.min(3.5, Math.max(0.25, extent * 0.25));
			float t = Mth.clamp((float) (margin / fade), 0.0f, 1.0f);
			return t * t * (3.0f - 2.0f * t);
		}
	}

	private static final Map<MinecraftServer, Map<String, Region>> SERVER_REGIONS = new ConcurrentHashMap<>();
	private static boolean installed;

	private SakuraWeather() {}

	public static String normalizeName(String raw) {
		return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
	}

	public static float clampIntensity(float intensity) {
		if (!Float.isFinite(intensity)) return DEFAULT_INTENSITY;
		return Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, intensity));
	}

	public static float clampRadius(float radius) {
		if (!Float.isFinite(radius)) return 16.0f;
		return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
	}

	public static synchronized void install() {
		if (installed) return;
		installed = true;
		PayloadTypeRegistry.clientboundPlay().register(SakuraWeatherPayload.TYPE, SakuraWeatherPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVER_REGIONS.put(server, loadMap(storePath(server))));
		ServerLifecycleEvents.SERVER_STOPPED.register(SERVER_REGIONS::remove);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayer(handler.player, server));
	}

	public static List<Region> regions(MinecraftServer server) {
		if (server == null) return List.of();
		Map<String, Region> map = SERVER_REGIONS.computeIfAbsent(server, s -> loadMap(storePath(s)));
		synchronized (map) {
			return List.copyOf(map.values());
		}
	}

	public static Region get(MinecraftServer server, String name) {
		if (server == null) return null;
		String key = normalizeName(name);
		if (key.isEmpty()) return null;
		Map<String, Region> map = SERVER_REGIONS.computeIfAbsent(server, s -> loadMap(storePath(s)));
		synchronized (map) {
			return map.get(key);
		}
	}

	public static void upsert(MinecraftServer server, Region region) {
		if (server == null || region == null) return;
		Map<String, Region> map = SERVER_REGIONS.computeIfAbsent(server, s -> loadMap(storePath(s)));
		List<Region> snapshot;
		synchronized (map) {
			map.put(region.name(), region);
			snapshot = List.copyOf(map.values());
		}
		persistAndBroadcast(server, snapshot);
	}

	public static boolean remove(MinecraftServer server, String name) {
		if (server == null) return false;
		String key = normalizeName(name);
		if (key.isEmpty()) return false;
		Map<String, Region> map = SERVER_REGIONS.computeIfAbsent(server, s -> loadMap(storePath(s)));
		List<Region> snapshot;
		synchronized (map) {
			if (map.remove(key) == null) return false;
			snapshot = List.copyOf(map.values());
		}
		persistAndBroadcast(server, snapshot);
		return true;
	}

	public static int clear(MinecraftServer server) {
		if (server == null) return 0;
		Map<String, Region> map = SERVER_REGIONS.computeIfAbsent(server, s -> loadMap(storePath(s)));
		int removed;
		synchronized (map) {
			removed = map.size();
			if (removed == 0) return 0;
			map.clear();
		}
		persistAndBroadcast(server, List.of());
		return removed;
	}

	private static void persistAndBroadcast(MinecraftServer server, List<Region> snapshot) {
		try {
			save(storePath(server), snapshot);
		} catch (IOException ex) {
			Flake.LOGGER.warn("Failed to persist sakura weather regions", ex);
		}
		var payload = new SakuraWeatherPayload(snapshot);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, SakuraWeatherPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void syncPlayer(ServerPlayer player, MinecraftServer server) {
		if (ServerPlayNetworking.canSend(player, SakuraWeatherPayload.TYPE)) {
			ServerPlayNetworking.send(player, new SakuraWeatherPayload(regions(server)));
		}
	}

	private static Path storePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("flake").resolve("weather.properties");
	}

	private static Map<String, Region> loadMap(Path file) {
		Map<String, Region> map = new LinkedHashMap<>();
		for (Region region : load(file)) {
			map.put(region.name(), region);
		}
		return map;
	}

	public static List<Region> load(Path file) {
		if (file == null || Files.notExists(file)) return List.of();
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			props.load(in);
		} catch (IOException ex) {
			Flake.LOGGER.warn("Failed to load weather regions from {}", file, ex);
			return List.of();
		}
		int version = parseInt(props.getProperty("version"), 0);
		if (version < 2) return List.of();
		int count = Math.max(0, Math.min(256, parseInt(props.getProperty("count"), 0)));
		List<Region> loaded = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String prefix = "r." + i + ".";
			String name = normalizeName(props.getProperty(prefix + "name"));
			if (name.isEmpty()) continue;
			String dim = props.getProperty(prefix + "dim", "minecraft:overworld");
			Shape shape = Shape.parse(props.getProperty(prefix + "shape"));
			String[] coords = props.getProperty(prefix + "coords", "").split(",");
			if (coords.length != 6) continue;
			try {
				double x1 = Double.parseDouble(coords[0].trim());
				double y1 = Double.parseDouble(coords[1].trim());
				double z1 = Double.parseDouble(coords[2].trim());
				double x2 = Double.parseDouble(coords[3].trim());
				double y2 = Double.parseDouble(coords[4].trim());
				double z2 = Double.parseDouble(coords[5].trim());
				float radius = Float.parseFloat(props.getProperty(prefix + "radius", "16.0"));
				float intensity = Float.parseFloat(props.getProperty(prefix + "intensity", "1.0"));
				if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(z1)
						|| !Double.isFinite(x2) || !Double.isFinite(y2) || !Double.isFinite(z2)) {
					continue;
				}
				loaded.add(new Region(name, dim, shape, x1, y1, z1, x2, y2, z2, radius, intensity));
			} catch (NumberFormatException ignored) {}
		}
		return List.copyOf(loaded);
	}

	public static void save(Path file, Collection<Region> regions) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) Files.createDirectories(parent);
		Properties props = new Properties();
		props.setProperty("version", "2");
		int i = 0;
		for (Region r : regions) {
			String prefix = "r." + i + ".";
			props.setProperty(prefix + "name", r.name());
			props.setProperty(prefix + "dim", r.dimension());
			props.setProperty(prefix + "shape", r.shape().id());
			props.setProperty(prefix + "coords",
					r.x1() + "," + r.y1() + "," + r.z1() + "," + r.x2() + "," + r.y2() + "," + r.z2());
			props.setProperty(prefix + "radius", Float.toString(r.radius()));
			props.setProperty(prefix + "intensity", Float.toString(r.intensity()));
			i++;
		}
		props.setProperty("count", Integer.toString(i));
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

	private static int parseInt(String raw, int fallback) {
		if (raw == null) return fallback;
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
