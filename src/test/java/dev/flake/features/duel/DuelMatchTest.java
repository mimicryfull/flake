package dev.flake.features.duel;

import dev.flake.features.loadout.Loadout;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuelMatchTest {
	private final UUID first = UUID.randomUUID();
	private final UUID second = UUID.randomUUID();

	private DuelMatch match() {
		return new DuelMatch(first, Loadout.defaultLoadout(), second, Loadout.defaultLoadout(), 2);
	}

	@Test
	void requiresRestorationAfterEveryRoundIncludingLast() {
		var match = match();
		match.startRound();
		match.finishRound(DuelMatch.Result.FIRST);
		assertEquals(DuelMatch.State.RESTORING, match.state());
		assertThrows(IllegalStateException.class, match::startRound);
		match.restorationComplete();
		match.startRound();
		match.finishRound(DuelMatch.Result.FIRST);
		assertThrows(IllegalStateException.class, match::winner);
		match.restorationComplete();
		assertEquals(DuelMatch.State.FINISHED, match.state());
		assertEquals(first, match.winner());
	}

	@Test
	void drawDoesNotAwardAWin() {
		var match = match();
		match.startRound();
		match.finishRound(DuelMatch.Result.DRAW);
		match.restorationComplete();
		assertEquals(0, match.firstWins());
		assertEquals(0, match.secondWins());
		assertEquals(DuelMatch.State.COUNTDOWN, match.state());
	}

	@Test
	void cancellationStopsTransitions() {
		var match = match();
		match.cancel();
		assertEquals(DuelMatch.State.CANCELLED, match.state());
		assertThrows(IllegalStateException.class, match::startRound);
		assertThrows(IllegalStateException.class, match::restorationComplete);
	}

	@Test
	void validatesParticipantsAndLoadouts() {
		assertThrows(IllegalArgumentException.class, () -> new DuelMatch(first, Loadout.defaultLoadout(), first, Loadout.defaultLoadout(), 2));
		var invalid = new Loadout(Loadout.FighterClass.VAMPIRE, Loadout.Armor.IRON, Loadout.Weapon.BOW,
				List.of(Loadout.Accessory.REGENERATION, Loadout.Accessory.MOBILITY));
		assertThrows(IllegalArgumentException.class, () -> new DuelMatch(first, invalid, second, Loadout.defaultLoadout(), 2));
	}
}
