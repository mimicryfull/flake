package dev.flake.features.concussed;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConcussedLifecycleTest {
	@Test void lethalHitMayResetEveryCastDuringTheTick() {
		var casts = new HashMap<UUID, Object>();
		casts.put(UUID.randomUUID(), new Object());
		casts.put(UUID.randomUUID(), new Object());
		var calls = new AtomicInteger();
		assertDoesNotThrow(() -> ConcussedAbilities.tickActiveCasts(casts, (id, cast) -> {
			calls.incrementAndGet();
			casts.clear(); // Synchronous match restoration during hurtServer.
			return true;
		}));
		assertEquals(1, calls.get());
		assertTrue(casts.isEmpty());
	}

	@Test void aReplacementCastIsNotRemovedByTheOldCastCompleting() {
		var id = UUID.randomUUID();
		var casts = new HashMap<UUID, Object>();
		var replacement = new Object();
		casts.put(id, new Object());
		ConcussedAbilities.tickActiveCasts(casts, (player, cast) -> {
			casts.put(player, replacement);
			return true;
		});
		assertSame(replacement, casts.get(id));
	}

	@Test void castsAddedByCallbacksWaitUntilTheNextTick() {
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		var casts = new HashMap<UUID, Object>();
		casts.put(first, new Object());
		var calls = new AtomicInteger();
		ConcussedAbilities.tickActiveCasts(casts, (player, cast) -> {
			calls.incrementAndGet();
			casts.put(second, new Object());
			return true;
		});
		assertEquals(1, calls.get());
		assertFalse(casts.containsKey(first));
		assertTrue(casts.containsKey(second));
	}
}
