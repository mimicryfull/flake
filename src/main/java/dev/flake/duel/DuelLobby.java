package dev.flake.duel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DuelLobby {
	private final Map<UUID, Boolean> players = new LinkedHashMap<>();
	private final Map<UUID, Integer> teams = new LinkedHashMap<>();
	private int teamCount = 2;
	private int teamSize = 1;
	private boolean locked;

	public void configure(int teamCount, int teamSize) {
		if (locked) return;
		this.teamCount = Math.clamp(teamCount, 2, 4);
		this.teamSize = Math.clamp(teamSize, 1, 5);
	}

	public int teamCount() { return teamCount; }
	public int teamSize() { return teamSize; }
	public int maxPlayers() { return teamCount * teamSize; }

	public boolean join(UUID player) {
		if (locked || players.containsKey(player) || players.size() >= maxPlayers()) return false;
		int assignedTeam = findAvailableTeam();
		if (assignedTeam < 0) return false;
		players.put(player, false);
		teams.put(player, assignedTeam);
		return true;
	}

	private int findAvailableTeam() {
		for (int t = 0; t < teamCount; t++) {
			if (teamMemberCount(t) < teamSize) return t;
		}
		return -1;
	}

	public int teamMemberCount(int teamIndex) {
		int count = 0;
		for (int t : teams.values()) {
			if (t == teamIndex) count++;
		}
		return count;
	}

	public int teamOf(UUID player) {
		return teams.getOrDefault(player, 0);
	}

	public boolean switchTeam(UUID player, int targetTeam) {
		if (locked || !players.containsKey(player) || targetTeam < 0 || targetTeam >= teamCount) return false;
		int current = teamOf(player);
		if (current == targetTeam) return true;
		if (teamMemberCount(targetTeam) >= teamSize) return false;
		teams.put(player, targetTeam);
		players.put(player, false);
		return true;
	}

	/** Server-side removal that ignores the readiness lock (disconnect/shutdown cleanup). */
	public boolean forceLeave(UUID player) {
		if (players.remove(player) == null) return false;
		teams.remove(player);
		players.replaceAll((id, ready) -> false);
		return true;
	}

	public boolean leave(UUID player) {
		if (locked || players.remove(player) == null) return false;
		teams.remove(player);
		players.replaceAll((id, ready) -> false);
		return true;
	}

	public boolean setReady(UUID player, boolean ready) {
		if (locked || !players.containsKey(player)) return false;
		players.put(player, ready);
		return true;
	}

	public void buildChanged(UUID player) {
		if (players.containsKey(player)) players.put(player, false);
	}

	public boolean allReady() {
		if (players.size() < 2 || !players.values().stream().allMatch(Boolean::booleanValue)) return false;
		long populatedTeams = teams.values().stream().distinct().count();
		return populatedTeams >= 2 || players.size() == maxPlayers();
	}

	public Map<UUID, Boolean> players() {
		return Map.copyOf(players);
	}

	public Map<UUID, Integer> teams() {
		return Map.copyOf(teams);
	}

	public void lock() { locked = true; }
	public void unlock() { locked = false; }
	public boolean locked() { return locked; }
	public void resetReady() { players.replaceAll((id, ready) -> false); }

	public void clear() {
		locked = false;
		teamCount = 2;
		teamSize = 1;
		players.clear();
		teams.clear();
	}
}
