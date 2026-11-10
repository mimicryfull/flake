package dev.flake.duel;

import dev.flake.build.Loadout;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DuelMatch {
	public enum State { COUNTDOWN, FIGHTING, RESTORING, FINISHED, CANCELLED }
	public enum Result { FIRST, SECOND, DRAW }

	private final UUID first;
	private final UUID second;
	private final Map<UUID, Loadout> loadouts;
	private final int winsNeeded;
	private int firstWins;
	private int secondWins;
	private State state = State.COUNTDOWN;

	public DuelMatch(UUID first, Loadout firstLoadout, UUID second, Loadout secondLoadout, int winsNeeded) {
		this.first = Objects.requireNonNull(first);
		this.second = Objects.requireNonNull(second);
		Objects.requireNonNull(firstLoadout);
		Objects.requireNonNull(secondLoadout);
		if (first.equals(second)) throw new IllegalArgumentException("Duel requires two different players");
		if (winsNeeded < 1) throw new IllegalArgumentException("Wins needed must be positive");
		if (!firstLoadout.errors().isEmpty() || !secondLoadout.errors().isEmpty()) throw new IllegalArgumentException("Invalid loadout");
		this.loadouts = Map.of(first, firstLoadout, second, secondLoadout);
		this.winsNeeded = winsNeeded;
	}

	public void startRound() {
		require(State.COUNTDOWN);
		state = State.FIGHTING;
	}

	public void finishRound(Result result) {
		require(State.FIGHTING);
		switch (Objects.requireNonNull(result)) {
			case FIRST -> firstWins++;
			case SECOND -> secondWins++;
			case DRAW -> { }
		}
		state = State.RESTORING;
	}

	public void restorationComplete() {
		require(State.RESTORING);
		state = firstWins >= winsNeeded || secondWins >= winsNeeded ? State.FINISHED : State.COUNTDOWN;
	}

	public void cancel() {
		if (state == State.FINISHED || state == State.CANCELLED) throw new IllegalStateException("Match already ended");
		state = State.CANCELLED;
	}

	public State state() { return state; }
	public UUID first() { return first; }
	public UUID second() { return second; }
	public int firstWins() { return firstWins; }
	public int secondWins() { return secondWins; }
	public int winsNeeded() { return winsNeeded; }
	public Map<UUID, Loadout> loadouts() { return loadouts; }
	public UUID winner() {
		if (state != State.FINISHED) throw new IllegalStateException("Match is not finished");
		return firstWins >= winsNeeded ? first : second;
	}

	private void require(State expected) {
		if (state != expected) throw new IllegalStateException("Expected " + expected + ", got " + state);
	}
}
