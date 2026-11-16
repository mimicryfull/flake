package dev.flake.features.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SakuraWeatherTest {

	@Test
	void parsesAliasesAndClampsIntensity() {
		assertEquals(SakuraWeather.Mode.SAKURA, SakuraWeather.Mode.parse("sakura"));
		assertEquals(SakuraWeather.Mode.SAKURA, SakuraWeather.Mode.parse("  CHERRY "));
		assertEquals(SakuraWeather.Mode.SAKURA, SakuraWeather.Mode.parse("petals"));
		assertEquals(SakuraWeather.Mode.NONE, SakuraWeather.Mode.parse("clear"));
		assertEquals(SakuraWeather.Mode.NONE, SakuraWeather.Mode.parse(null));

		assertEquals(SakuraWeather.MIN_INTENSITY, SakuraWeather.clampIntensity(0.01f));
		assertEquals(SakuraWeather.MAX_INTENSITY, SakuraWeather.clampIntensity(99.0f));
		assertEquals(SakuraWeather.DEFAULT_INTENSITY, SakuraWeather.clampIntensity(Float.NaN));
		assertEquals(1.6f, SakuraWeather.clampIntensity(1.6f));
	}

	@Test
	void saveAndLoadRoundTripsState(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("flake/weather.properties");
		assertEquals(SakuraWeather.State.OFF, SakuraWeather.load(file));

		var enabled = new SakuraWeather.State(SakuraWeather.Mode.SAKURA, 1.8f);
		SakuraWeather.save(file, enabled);
		assertEquals(enabled, SakuraWeather.load(file));

		SakuraWeather.save(file, SakuraWeather.State.OFF);
		assertEquals(SakuraWeather.State.OFF, SakuraWeather.load(file));
	}
}
