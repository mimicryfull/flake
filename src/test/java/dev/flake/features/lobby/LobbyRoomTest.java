package dev.flake.features.lobby;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobbyRoomTest {
	private final UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private final UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private final UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
	private final UUID stranger = UUID.fromString("00000000-0000-0000-0000-000000000004");

	private LobbyRoom room() {
		var room = new LobbyRoom(UUID.randomUUID(), "Дружня дуель", owner);
		assertEquals("", room.join(owner));
		return room;
	}

	@Test void ownerJoinsFirstAndKeepsOwnership() {
		var room = room();
		assertEquals(owner, room.owner());
		assertTrue(room.isOwner(owner));
		assertTrue(room.isMember(owner));
		assertEquals(1, room.size());
		assertFalse(room.isOwner(second));
	}

	@Test void duelModeAcceptsExactlyTwoPlayers() {
		var room = room();
		assertEquals("", room.join(second));
		assertFalse(room.join(third).isEmpty());
		assertEquals(2, room.size());
		assertEquals("", room.leave(second));
		assertEquals(1, room.size());
	}

	@Test void publicRoomIsJoinableAndPrivateRoomNeedsInvite() {
		var room = room();
		assertTrue(room.joinableBy(second));
		assertEquals("", room.updateSettings(owner, "DUEL", "PRIVATE", "default", 2, 1));
		assertTrue(room.visibleTo(owner));
		assertFalse(room.visibleTo(second));
		assertFalse(room.joinableBy(second));
		assertFalse(room.join(second).isEmpty());
		assertEquals("", room.invite(owner, second));
		assertTrue(room.visibleTo(second));
		assertTrue(room.isInvited(second));
		assertEquals("", room.join(second));
		assertFalse(room.isInvited(second), "invite is consumed on join");
	}

	@Test void onlyOwnerMayInviteKickTransferOrChangeSettings() {
		var room = room(); room.join(second); room.join(stranger);
		assertFalse(room.invite(second, third).isEmpty());
		assertFalse(room.kick(second, stranger).isEmpty());
		assertFalse(room.transferOwnership(second, stranger).isEmpty());
		assertFalse(room.updateSettings(second, "TEAM_FIGHT", "PUBLIC", "default", 2, 2).isEmpty());
		assertTrue(room.isOwner(owner));
	}

	@Test void kickAndTransferChangeMembershipAndOwnership() {
		var room = room();
		assertEquals("", room.updateSettings(owner, "TEAM_FIGHT", "PUBLIC", "default", 2, 2));
		assertEquals("", room.join(second));
		assertEquals("", room.join(stranger));
		assertFalse(room.kick(owner, owner).isEmpty(), "owner cannot kick themselves");
		assertEquals("", room.kick(owner, stranger));
		assertFalse(room.isMember(stranger));
		assertFalse(room.transferOwnership(owner, stranger).isEmpty(), "target must still be a member");
		assertEquals("", room.transferOwnership(owner, second));
		assertTrue(room.isOwner(second));
		assertFalse(room.isOwner(owner));
	}

	@Test void ownerLeavingHandsTheRoomToTheNextMember() {
		var room = room(); room.join(second);
		room.ready(owner, true, true);
		assertEquals("", room.leave(owner));
		assertEquals(second, room.owner());
		assertFalse(room.isMember(owner));
		assertFalse(room.lobby().players().get(second), "readiness resets on ownership change");
		assertEquals("", room.leave(second));
		assertTrue(room.empty());
	}

	@Test void readinessRequiresMembershipAndAValidBuild() {
		var room = room();
		assertFalse(room.ready(second, true, true).isEmpty());
		assertFalse(room.ready(owner, true, false).isEmpty());
		assertFalse(room.lobby().allReady());
		assertEquals("", room.ready(owner, true, true));
		assertTrue(room.lobby().players().get(owner));
		room.buildChanged(owner);
		assertFalse(room.lobby().players().get(owner));
	}

	@Test void settingsAreValidatedAndResetReadiness() {
		var room = room(); room.join(second); room.ready(owner, true, true);
		assertFalse(room.updateSettings(owner, "NONSENSE", "PUBLIC", "default", 2, 1).isEmpty());
		assertFalse(room.updateSettings(owner, "TEAM_FIGHT", "PUBLIC", "not valid!", 2, 2).isEmpty());
		assertEquals("", room.updateSettings(owner, "TEAM_FIGHT", "PUBLIC", "", 2, 2), "blank arena keeps the current one");
		assertEquals("", room.updateSettings(owner, "TEAM_FIGHT", "PUBLIC", "default", 3, 2));
		assertEquals("TEAM_FIGHT", room.mode());
		assertEquals(3, room.teamCount());
		assertEquals(2, room.teamSize());
		assertEquals(6, room.maxPlayers());
		assertFalse(room.lobby().players().get(owner));
	}

	@Test void delegatedTeamsRespectTeamSize() {
		var room = room();
		room.updateSettings(owner, "TEAM_FIGHT", "PUBLIC", "default", 2, 1);
		assertEquals("", room.join(second));
		assertEquals(0, room.lobby().teamOf(owner));
		assertFalse(room.switchTeam(second, 0).isEmpty(), "team 0 is already full");
		assertEquals("", room.switchTeam(second, 1));
	}

	@Test void matchActionsNeedABoundServerButStayPermissionChecked() {
		var room = room(); room.join(second);
		assertFalse(room.start(second).isEmpty(), "non-owner cannot start");
		assertFalse(room.stopMatch(second).isEmpty(), "non-owner cannot stop");
		assertFalse(room.start(owner).isEmpty(), "no server bound in a unit test");
	}

	@Test void nameSanitizationRejectsControlCharactersAndLongNames() {
		assertEquals("Дружня дуель", LobbyRoom.sanitizeName("  Дружня дуель  "));
		for (String bad : new String[] {"", "   ", "x".repeat(33), "Bad\nname", "§bFake"}) {
			assertEquals("", LobbyRoom.sanitizeName(bad));
		}
	}
}
