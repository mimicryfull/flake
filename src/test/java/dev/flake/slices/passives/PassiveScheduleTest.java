package dev.flake.slices.passives;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The fight tick runs every tick for every participant, so the passive must tolerate arbitrary call
 * rates: no double healing within an interval and no dependence on global tick alignment.
 */
class PassiveScheduleTest {
	@Test void regenerationFiresOncePerIntervalRegardlessOfCallRate() {
		var id = UUID.randomUUID();
		PassivesSlice.resetSchedule(id);
		assertTrue(PassivesSlice.regenerationDue(id, 100), "first tick arms the interval");
		for (int tick = 101; tick < 160; tick++) {
			assertFalse(PassivesSlice.regenerationDue(id, tick), "no second heal before 60 ticks: " + tick);
		}
		assertTrue(PassivesSlice.regenerationDue(id, 160), "heals again exactly one interval later");
		assertFalse(PassivesSlice.regenerationDue(id, 161));
	}

	@Test void severalPlayersKeepIndependentIntervals() {
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		PassivesSlice.resetSchedule(first);
		PassivesSlice.resetSchedule(second);
		assertTrue(PassivesSlice.regenerationDue(first, 0));
		assertTrue(PassivesSlice.regenerationDue(second, 30), "another player is not blocked by the first");
		assertFalse(PassivesSlice.regenerationDue(first, 30));
		assertTrue(PassivesSlice.regenerationDue(first, 60));
		assertTrue(PassivesSlice.regenerationDue(second, 90));
	}

	@Test void resetClearsTheScheduleForANewMatch() {
		var id = UUID.randomUUID();
		assertTrue(PassivesSlice.regenerationDue(id, 500));
		assertFalse(PassivesSlice.regenerationDue(id, 501));
		PassivesSlice.resetSchedule(id);
		assertTrue(PassivesSlice.regenerationDue(id, 502), "a fresh match heals on its first fight tick");
	}

	@Test void intervalIsIndependentOfGlobalTickAlignment() {
		var id = UUID.randomUUID();
		PassivesSlice.resetSchedule(id);
		int start = 1_000_003; // not a multiple of 60
		assertTrue(PassivesSlice.regenerationDue(id, start));
		assertFalse(PassivesSlice.regenerationDue(id, start + 59));
		assertTrue(PassivesSlice.regenerationDue(id, start + 60));
	}
}