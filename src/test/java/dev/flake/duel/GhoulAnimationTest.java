package dev.flake.duel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhoulAnimationTest {
	@Test void onlyActualEligibleBloodlustHealingGetsFeedback() {
		assertTrue(GhoulAnimation.confirmedHealing(true, 4, 10, 10.72f));
		assertFalse(GhoulAnimation.confirmedHealing(false, 4, 10, 10.32f)); // Leech alone
		assertFalse(GhoulAnimation.confirmedHealing(true, 4, 20, 20)); // Full health
		assertFalse(GhoulAnimation.confirmedHealing(true, 0, 10, 11));
		assertFalse(GhoulAnimation.confirmedHealing(true, 4, 10, Float.NaN));
	}

	@Test void envelopesHaveDistinctClimaxesAndFiniteTails() {
		assertEquals(0, GhoulAnimation.compression(-1));
		assertTrue(GhoulAnimation.compression(0.04f) > 0.99f);
		assertEquals(0, GhoulAnimation.compression(0.15f));
		assertEquals(0, GhoulAnimation.flash(0.04f));
		assertEquals(1, GhoulAnimation.flash(GhoulAnimation.CONTACT_DELAY));
		assertEquals(0, GhoulAnimation.flash(0.14f));
		assertEquals(1, GhoulAnimation.aura(4.9f, 5));
		assertTrue(GhoulAnimation.aura(5.15f, 5) > 0);
		assertEquals(0, GhoulAnimation.aura(5.39f, 5));
		assertEquals(0, GhoulAnimation.aura(1.39f, 1)); // Early server OFF
		assertEquals(0.15f, GhoulAnimation.siphonDuration(0));
		assertEquals(0.35f, GhoulAnimation.siphonDuration(100));
		float duration = GhoulAnimation.siphonDuration(3);
		assertEquals(0, GhoulAnimation.absorption(0.1f, duration));
		assertTrue(GhoulAnimation.absorption(duration + 0.025f, duration) > 0.99f);
		assertEquals(0, GhoulAnimation.absorption(duration + 0.24f, duration));
	}

	@Test void worldHistoryInterpolatesTimestampsAcrossDifferentFrameRates() {
		for (int fps : new int[] {5, 20, 60, 144, 240}) {
			var trail = new GhoulAnimation.Trail();
			trail.reset(0, 1000, 200, -300);
			for (int frame = 1; frame <= fps; frame++) {
				double t = frame / (double) fps;
				trail.update(t, 1000 + 8 * t, 200 + t, -300 + t * 3);
			}
			double[] p = new double[4];
			trail.sample(0.82, p);
			assertEquals(1000 + 8 * .82, p[0], .000001);
			assertEquals(200 + .82, p[1], .000001);
			assertEquals(-300 + .82 * 3, p[2], .000001);
			assertEquals(.82, p[3], .000001);
		}
	}

	@Test void historyDoesNotBridgeTeleportsPausesOrLongStalls() {
		var trail = new GhoulAnimation.Trail();
		var p = new double[4];
		trail.reset(0, 0, 0, 0);
		trail.update(.1, 1, 0, 0);
		trail.update(.1, 1, 0, 0);
		trail.sample(.075, p); assertEquals(.75, p[0], .00001);
		trail.update(.2, 100, 20, 30);
		trail.sample(0, p); assertEquals(100, p[0]); assertEquals(.2, p[3]);
		trail.update(2, 101, 20, 30);
		trail.sample(.2, p); assertEquals(101, p[0]); assertEquals(2, p[3]);
	}
}
