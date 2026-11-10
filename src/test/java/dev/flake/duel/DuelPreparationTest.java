package dev.flake.duel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuelPreparationTest {
	@Test void savesBothBeforeTouchingEitherPlayer() throws Exception {
		var events = new ArrayList<String>();
		DuelPreparation.run(List.of("A", "B"), p -> events.add("save" + p), p -> events.add("kit" + p), p -> events.add("restore" + p));
		assertEquals(List.of("saveA", "saveB", "kitA", "kitB"), events);
	}

	@Test void secondSaveFailureNeverIssuesAnyGear() {
		var events = new ArrayList<String>();
		assertThrows(IOException.class, () -> DuelPreparation.run(List.of("A", "B"), p -> {
			if (p.equals("B")) throw new IOException("disk full");
			events.add("save" + p);
		}, p -> events.add("kit" + p), p -> events.add("restore" + p)));
		assertEquals(List.of("saveA", "restoreA"), events);
	}

	@Test void failedTeleportRestoresBothSavedPlayers() {
		var events = new ArrayList<String>();
		assertThrows(IOException.class, () -> DuelPreparation.run(List.of("A", "B"), p -> events.add("save" + p), p -> {
			events.add("kit" + p);
			if (p.equals("B")) throw new IOException("teleport failed");
		}, p -> events.add("restore" + p)));
		assertEquals(List.of("saveA", "saveB", "kitA", "kitB", "restoreB", "restoreA"), events);
	}

	@Test void oneRecoveryFailureDoesNotSkipTheOtherPlayer() {
		var restored = new ArrayList<String>();
		var failure = assertThrows(IOException.class, () -> DuelPreparation.run(List.of("A", "B"), p -> {}, p -> {
			throw new IOException("start failed");
		}, p -> { restored.add(p); if (p.equals("B")) throw new IOException("journal retained"); }));
		assertEquals(List.of("B", "A"), restored);
		assertEquals(1, failure.getSuppressed().length);
	}
}
