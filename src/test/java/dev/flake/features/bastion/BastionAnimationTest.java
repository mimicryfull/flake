package dev.flake.features.bastion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BastionAnimationTest {
	@Test void bastionAssemblyOvershootsThenSettlesAndExitKeepsItsTail() {
		assertEquals(0, BastionAnimation.assembly(-1));
		assertEquals(0, BastionAnimation.assembly(0));
		assertTrue(BastionAnimation.assembly(0.24f) > 1.02f);
		assertEquals(1, BastionAnimation.assembly(1));
		assertEquals(0, BastionAnimation.Bulwark.burst(0));
		assertTrue(BastionAnimation.Bulwark.burst(0.28f) > 0.99f);
		assertTrue(BastionAnimation.Bulwark.intensity(4.2f, 4) > 0);
		assertEquals(0, BastionAnimation.Bulwark.intensity(4.62f, 4));
		assertFalse(BastionAnimation.Bulwark.finished(1.1f, 1));
		assertTrue(BastionAnimation.Bulwark.finished(1.63f, 1));
		assertEquals(0, BastionAnimation.Bulwark.hit(-1));
		assertTrue(BastionAnimation.Bulwark.hit(0.035f) > 0.9f);
		assertEquals(0, BastionAnimation.Bulwark.hit(0.46f));
	}

	@Test void bastionContactHasACompactFlashAndLongerSuppressionTail() {
		assertEquals(0, BastionAnimation.Ram.impact(0.09f));
		assertEquals(1, BastionAnimation.Ram.impact(0.10f));
		assertEquals(0, BastionAnimation.Ram.impact(0.24f));
		assertTrue(BastionAnimation.Ram.suppression(0.5f) > 0.99f);
		assertEquals(0, BastionAnimation.Ram.suppression(BastionAnimation.Ram.DURATION));
		for (int i = 0; i <= 1200; i++) {
			float age = i / 1000f;
			assertTrue(Float.isFinite(BastionAnimation.assembly(age)));
			assertTrue(BastionAnimation.Ram.impact(age) >= 0 && BastionAnimation.Ram.impact(age) <= 1);
			assertTrue(BastionAnimation.Ram.suppression(age) >= 0 && BastionAnimation.Ram.suppression(age) <= 1);
		}
	}
}
