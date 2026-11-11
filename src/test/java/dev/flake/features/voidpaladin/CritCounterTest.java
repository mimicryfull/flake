package dev.flake.features.voidpaladin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CritCounterTest {
	@Test void fourthConfirmedHitIsTheCrit() {
		assertFalse(CritCounter.nextIsCrit(0));
		assertFalse(CritCounter.nextIsCrit(1));
		assertFalse(CritCounter.nextIsCrit(2));
		assertTrue(CritCounter.nextIsCrit(3));
		assertFalse(CritCounter.nextIsCrit(4));
		assertTrue(CritCounter.nextIsCrit(7));
		assertTrue(CritCounter.nextIsCrit(11));
	}

	@Test void attemptThatNeverLandsDoesNotAdvanceTheCounter() {
		int confirmed = 0;
		// Three blocked swings: the counter stays where it was.
		assertFalse(CritCounter.nextIsCrit(confirmed));
		assertFalse(CritCounter.nextIsCrit(confirmed));
		assertFalse(CritCounter.nextIsCrit(confirmed));
		assertEquals(0, confirmed);
		confirmed = CritCounter.afterConfirmedHit(confirmed);
		confirmed = CritCounter.afterConfirmedHit(confirmed);
		confirmed = CritCounter.afterConfirmedHit(confirmed);
		assertEquals(3, confirmed);
		assertTrue(CritCounter.nextIsCrit(confirmed));
	}

	@Test void chargeIsShownOnlyBeforeTheCritHit() {
		for (int hits = 0; hits < 12; hits++) {
			assertEquals(CritCounter.nextIsCrit(hits), CritCounter.showsCharge(hits));
		}
		assertFalse(CritCounter.showsCharge(0));
		assertFalse(CritCounter.showsCharge(1));
		assertFalse(CritCounter.showsCharge(2));
		assertTrue(CritCounter.showsCharge(3));
	}

	@Test void negativeAndLargeCountsStaySafe() {
		assertFalse(CritCounter.nextIsCrit(-1));
		assertEquals(1, CritCounter.afterConfirmedHit(-5));
		assertTrue(CritCounter.nextIsCrit(1_000_003));
	}
}
