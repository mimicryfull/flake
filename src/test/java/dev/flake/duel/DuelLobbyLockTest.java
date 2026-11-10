package dev.flake.duel;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuelLobbyLockTest {
	@Test void activeLobbyCannotBeMutatedUntilReturnCompletes() {
		var lobby = new DuelLobby(); var first = UUID.randomUUID(); var second = UUID.randomUUID();
		lobby.join(first); lobby.join(second); lobby.setReady(first, true); lobby.setReady(second, true);
		lobby.lock();
		assertFalse(lobby.leave(first)); assertFalse(lobby.setReady(first, false)); assertFalse(lobby.join(UUID.randomUUID()));
		assertTrue(lobby.allReady());
		lobby.unlock(); lobby.resetReady();
		assertFalse(lobby.allReady()); assertTrue(lobby.leave(first));
	}
}
