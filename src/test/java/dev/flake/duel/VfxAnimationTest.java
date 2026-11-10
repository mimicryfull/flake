package dev.flake.duel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VfxAnimationTest {
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

	@Test void animationTimeDoesNotDependOnFpsAndDoesNotClampLongFrames() {
		for (int fps : new int[] {5, 20, 60, 240}) {
			var clock = new VfxTimeline();
			clock.sample(0, false);
			float age = 0;
			for (int i = 1; i <= fps; i++) age = clock.sample(i * 1_000_000_000L / fps, false);
			assertEquals(1f, age, 0.00001);
		}
		var clock = new VfxTimeline();
		assertEquals(0, clock.sample(100, false));
		assertEquals(0, clock.sample(1_000_000_100, true));
		assertEquals(0.5f, clock.sample(1_500_000_100, false));
		clock.reset();
		assertEquals(0, clock.sample(9_000_000_000L, false));
	}
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
	@Test void impactIsBoundedHasRecoilAndFullyDisappears() {
		assertEquals(0, ImpactAnimation.envelope(-1));
		assertEquals(0, ImpactAnimation.envelope(0));
		assertTrue(ImpactAnimation.envelope(0.06f) > 0.9f);
		boolean recoil = false;
		for (int i = 0; i <= 1000; i++) {
			float age = i / 1000f;
			assertTrue(ImpactAnimation.envelope(age) >= 0 && ImpactAnimation.envelope(age) <= 1);
			assertTrue(Math.abs(ImpactAnimation.displacement(age)) <= 1);
			recoil |= ImpactAnimation.displacement(age) < -0.01;
		}
		assertTrue(recoil);
		assertEquals(0, ImpactAnimation.envelope(ImpactAnimation.DURATION));
		assertEquals(0, ImpactAnimation.displacement(ImpactAnimation.DURATION));
	}
}
