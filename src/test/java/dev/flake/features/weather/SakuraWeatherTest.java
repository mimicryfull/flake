package dev.flake.features.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SakuraWeatherTest {

	@Test
	void clampsBoundsAndComputesSphereAndBoxWeights() {
		assertEquals(SakuraWeather.MIN_INTENSITY, SakuraWeather.clampIntensity(0.01f));
		assertEquals(SakuraWeather.MAX_INTENSITY, SakuraWeather.clampIntensity(99.0f));
		assertEquals(SakuraWeather.DEFAULT_INTENSITY, SakuraWeather.clampIntensity(Float.NaN));
		assertEquals(SakuraWeather.MIN_RADIUS, SakuraWeather.clampRadius(0.5f));
		assertEquals(SakuraWeather.MAX_RADIUS, SakuraWeather.clampRadius(999.0f));

		var sphere = SakuraWeather.Region.sphere("  SakuraZone ", "minecraft:overworld", 10, 70, 20, 12f, 1.5f);
		assertEquals("sakurazone", sphere.name());
		assertEquals(1.0f, sphere.weightAt(10, 70, 20), 1e-5f);
		assertEquals(0.0f, sphere.weightAt(22.1, 70, 20), 1e-5f);
		assertTrue(sphere.weightAt(20.5, 70, 20) > 0.0f && sphere.weightAt(20.5, 70, 20) < 1.0f);
		assertEquals(0.0, sphere.distanceSqToPoint(10, 70, 20), 1e-6);
		assertEquals(9.0, sphere.distanceSqToPoint(25, 70, 20), 1e-4);

		var box = SakuraWeather.Region.box("shrine", "minecraft:overworld", 30, 80, 40, 10, 60, 20, 1.2f);
		assertEquals(10.0, box.x1());
		assertEquals(30.0, box.x2());
		assertEquals(1.0f, box.weightAt(20, 70, 30), 1e-5f);
		assertEquals(0.0f, box.weightAt(9.5, 70, 30), 1e-5f);
		assertTrue(box.weightAt(10.8, 70, 30) > 0.0f && box.weightAt(10.8, 70, 30) < 1.0f);
		assertEquals(4.0, box.distanceSqToPoint(8, 70, 30), 1e-6);
	}

	@Test
	void saveAndLoadRoundTripsMultipleRegions(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("flake/weather.properties");
		assertTrue(SakuraWeather.load(file).isEmpty());

		var regions = List.of(
				SakuraWeather.Region.sphere("tree", "minecraft:overworld", 12.5, 68.0, -4.5, 18.0f, 1.4f),
				SakuraWeather.Region.box("courtyard", "minecraft:the_nether", -20, 60, -20, 20, 90, 20, 2.0f));
		SakuraWeather.save(file, regions);
		assertEquals(regions, SakuraWeather.load(file));

		SakuraWeather.save(file, List.of());
		assertTrue(SakuraWeather.load(file).isEmpty());
	}
}
