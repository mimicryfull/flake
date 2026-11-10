package dev.flake.duel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShockwaveTest {
	@Test
	void pushesAwayWithinRadius() {
		var impulse = Shockwave.impulse(3, 0, 4).orElseThrow();
		assertEquals(0.72, impulse.x(), 0.00001);
		assertEquals(0.96, impulse.z(), 0.00001);
		assertTrue(impulse.y() > 0);
		assertTrue(Shockwave.impulse(5.01, 0, 0).isEmpty());
		assertTrue(Shockwave.impulse(0, 5.01, 0).isEmpty());
	}

	@Test
	void handlesOverlappingPositionsAndInvalidCoordinates() {
		var impulse = Shockwave.impulse(0, 0, 0).orElseThrow();
		assertEquals(0, impulse.x());
		assertEquals(0, impulse.z());
		assertTrue(Shockwave.impulse(Double.NaN, 0, 0).isEmpty());
	}
}
