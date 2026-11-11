package dev.flake.features.passives;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pure per-player interval rule; each server owns a separate schedule. */
public final class RegenerationSchedule {
	public static final int INTERVAL_TICKS = 60;
	private final Map<UUID, Integer> lastHeal = new HashMap<>();

	public boolean due(UUID player, int now) {
		Integer last = lastHeal.get(player);
		if (last != null && now - last < INTERVAL_TICKS) return false;
		lastHeal.put(player, now);
		return true;
	}

	public void reset(UUID player) { lastHeal.remove(player); }
}
