package dev.flake.features.voidpaladin;

/**
 * Pure "every Nth confirmed hit" counter used by the Void Paladin crit.
 * Kept free of item registration so it can be unit-tested without a Minecraft bootstrap.
 */
public final class CritCounter {
	/** The 4th, 8th, 12th … confirmed hit crits. */
	public static final int EVERY = 4;

	private CritCounter() {}

	/** True when the hit after {@code confirmedHits} landed hits is the crit. */
	public static boolean nextIsCrit(int confirmedHits) {
		int safe = Math.max(0, confirmedHits);
		return safe % EVERY == EVERY - 1;
	}

	/** True when the charged (glinting) state should be shown before the next hit lands. */
	public static boolean showsCharge(int confirmedHits) {
		return nextIsCrit(confirmedHits);
	}

	/** Returns the count after a server-confirmed hit. */
	public static int afterConfirmedHit(int confirmedHits) {
		return Math.max(0, confirmedHits) + 1;
	}
}
