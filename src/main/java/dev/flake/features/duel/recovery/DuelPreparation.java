package dev.flake.features.duel.recovery;

import java.util.ArrayList;
import java.util.List;

/** Two-phase preparation: every original is durable before the first player is changed. */
public final class DuelPreparation {
	private DuelPreparation() {}

	public static <T> void run(List<T> participants, Step<T> save, Step<T> activate, Step<T> rollback) throws Exception {
		var saved = new ArrayList<T>();
		try {
			for (T player : participants) { save.run(player); saved.add(player); }
			for (T player : participants) activate.run(player);
		} catch (Exception failure) {
			for (T player : saved.reversed()) {
				try { rollback.run(player); }
				catch (Exception recoveryFailure) { failure.addSuppressed(recoveryFailure); }
			}
			throw failure;
		}
	}

	@FunctionalInterface public interface Step<T> { void run(T player) throws Exception; }
}
