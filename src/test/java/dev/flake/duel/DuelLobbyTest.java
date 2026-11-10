package dev.flake.duel;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuelLobbyTest {
	@Test
	void onlyTwoParticipantsAndBothMustBeReady() {
		var lobby = new DuelLobby();
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		assertTrue(lobby.join(first));
		assertFalse(lobby.join(first));
		assertTrue(lobby.setReady(first, true));
		assertFalse(lobby.allReady());
		assertTrue(lobby.join(second));
		assertFalse(lobby.join(UUID.randomUUID()));
		assertTrue(lobby.setReady(second, true));
		assertTrue(lobby.allReady());
		assertTrue(lobby.setReady(first, false));
		assertFalse(lobby.allReady());
	}

	@Test
	void changingBuildClearsOnlyOwnersReadyState() {
		var lobby = new DuelLobby();
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		lobby.join(first);
		lobby.join(second);
		lobby.setReady(first, true);
		lobby.setReady(second, true);
		lobby.buildChanged(first);
		assertFalse(lobby.players().get(first));
		assertTrue(lobby.players().get(second));
		assertFalse(lobby.allReady());
	}

	@Test
	void leavingClearsRemainingPlayersReadyState() {
		var lobby = new DuelLobby();
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		lobby.join(first);
		lobby.join(second);
		lobby.setReady(first, true);
		lobby.setReady(second, true);
		assertTrue(lobby.leave(first));
		assertFalse(lobby.players().get(second));
		assertFalse(lobby.setReady(first, true));
		assertFalse(lobby.leave(first));
	}

	@Test
	void supportsConfigurableTeamsAndPreventsTeamOverflow() {
		var lobby = new DuelLobby();
		lobby.configure(3, 2);
		assertEquals(3, lobby.teamCount());
		assertEquals(2, lobby.teamSize());
		assertEquals(6, lobby.maxPlayers());

		var p1 = UUID.randomUUID();
		var p2 = UUID.randomUUID();
		var p3 = UUID.randomUUID();
		assertTrue(lobby.join(p1));
		assertTrue(lobby.join(p2));
		assertTrue(lobby.join(p3));

		assertEquals(0, lobby.teamOf(p1));
		assertEquals(0, lobby.teamOf(p2));
		assertEquals(1, lobby.teamOf(p3));

		// Team 0 already has 2/2 players, so p3 cannot switch to Team 0
		assertFalse(lobby.switchTeam(p3, 0));
		// Team 2 has 0/2 players, so p3 can switch to Team 2
		assertTrue(lobby.switchTeam(p3, 2));
		assertEquals(2, lobby.teamOf(p3));
	}
}
