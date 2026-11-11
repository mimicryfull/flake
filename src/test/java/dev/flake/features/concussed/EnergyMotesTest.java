package dev.flake.features.concussed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnergyMotesTest {
	@Test void motesOriginateAtDetonationDecelerateAndExpire() {
		for (int i = 0; i < EnergyMotes.COUNT; i++) {
			assertEquals(0, EnergyMotes.distance(i, 0));
			assertEquals(0, EnergyMotes.opacity(i, 0));
			assertTrue(EnergyMotes.opacity(i, 0.1f) > 0);
			assertEquals(0, EnergyMotes.opacity(i, EnergyMotes.lifetime(i)));
			assertEquals(0, EnergyMotes.opacity(i, EnergyMotes.DURATION));
			double early = EnergyMotes.distance(i, 0.2f) - EnergyMotes.distance(i, 0.1f);
			double late = EnergyMotes.distance(i, 0.7f) - EnergyMotes.distance(i, 0.6f);
			assertTrue(early > late && late > 0);
			assertTrue(EnergyMotes.distance(i, EnergyMotes.DURATION) < 6.5);
			assertTrue(EnergyMotes.size(i) > 0);
			assertTrue(Math.abs(EnergyMotes.directionY(i)) < 1);
		}
	}
}
