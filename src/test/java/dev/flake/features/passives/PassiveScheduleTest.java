package dev.flake.features.passives;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The fight tick runs every tick for every participant, so the passive must tolerate arbitrary call
 * rates: no double healing within an interval and no dependence on global tick alignment.
 */
class PassiveScheduleTest {
	private final RegenerationSchedule schedule = new RegenerationSchedule();
	@Test void separateServerSchedulesDoNotSharePlayerState() {
		var player = UUID.randomUUID();
		var anotherServer = new RegenerationSchedule();
		assertTrue(schedule.due(player, 50));
		assertFalse(schedule.due(player, 51));
		assertTrue(anotherServer.due(player, 51));
	}
	@Test void regenerationFiresOncePerIntervalRegardlessOfCallRate() {
		var id = UUID.randomUUID();
		schedule.reset(id);
		assertTrue(schedule.due(id, 100), "first tick arms the interval");
		for (int tick = 101; tick < 160; tick++) {
			assertFalse(schedule.due(id, tick), "no second heal before 60 ticks: " + tick);
		}
		assertTrue(schedule.due(id, 160), "heals again exactly one interval later");
		assertFalse(schedule.due(id, 161));
	}

	@Test void severalPlayersKeepIndependentIntervals() {
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		schedule.reset(first);
		schedule.reset(second);
		assertTrue(schedule.due(first, 0));
		assertTrue(schedule.due(second, 30), "another player is not blocked by the first");
		assertFalse(schedule.due(first, 30));
		assertTrue(schedule.due(first, 60));
		assertTrue(schedule.due(second, 90));
	}

	@Test void resetClearsTheScheduleForANewMatch() {
		var id = UUID.randomUUID();
		assertTrue(schedule.due(id, 500));
		assertFalse(schedule.due(id, 501));
		schedule.reset(id);
		assertTrue(schedule.due(id, 502), "a fresh match heals on its first fight tick");
	}

	@Test void intervalIsIndependentOfGlobalTickAlignment() {
		var id = UUID.randomUUID();
		schedule.reset(id);
		int start = 1_000_003; // not a multiple of 60
		assertTrue(schedule.due(id, start));
		assertFalse(schedule.due(id, start + 59));
		assertTrue(schedule.due(id, start + 60));
	}
}
