package dev.flake.ui;

import dev.flake.arena.ArenaRepository;
import dev.flake.duel.DuelLobby;
import dev.flake.duel.DuelMatch;
import dev.flake.duel.DuelRuntime;
import dev.flake.duel.MatchRuntime;
import dev.flake.shared.FlakeI18n;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One independent player-created lobby: own owner, visibility, arena, mode, roster, readiness and match.
 * Every mutating call is server-authoritative and returns a localized error string ("" on success).
 */
public final class LobbyRoom {
	public enum Visibility { PUBLIC, PRIVATE }
	public static final Set<String> MODES = Set.of("DUEL", "TEAM_FIGHT");

	private final UUID id;
	private final long createdAt;
	private final DuelLobby lobby = new DuelLobby();
	private final Set<UUID> invites = new LinkedHashSet<>();
	private final Set<UUID> pendingLeaves = new LinkedHashSet<>();
	private final LobbyManager manager;
	private String title;
	private UUID owner;
	private String mode = "DUEL";
	private Visibility visibility = Visibility.PUBLIC;
	private String arena = ArenaRepository.DEFAULT;
	private MatchRuntime match;

	LobbyRoom(LobbyManager manager, UUID id, String title, UUID owner, long createdAt) {
		this.manager = manager;
		this.id = id;
		this.title = title;
		this.owner = owner;
		this.createdAt = createdAt;
	}

	/** Test/unit constructor: room state without a bound server (no match can be started). */
	LobbyRoom(UUID id, String title, UUID owner) {
		this(null, id, title, owner, 0L);
	}

	public UUID id() { return id; }
	public long createdAt() { return createdAt; }
	public String title() { return title; }
	public UUID owner() { return owner; }
	public String mode() { return mode; }
	public Visibility visibility() { return visibility; }
	public String arena() { return arena; }
	public DuelLobby lobby() { return lobby; }
	public int teamCount() { return lobby.teamCount(); }
	public int teamSize() { return lobby.teamSize(); }
	public int maxPlayers() { return lobby.maxPlayers(); }
	public boolean locked() { return lobby.locked(); }
	public boolean isMember(UUID player) { return lobby.players().containsKey(player); }
	public boolean isOwner(UUID player) { return owner != null && owner.equals(player); }
	public boolean isInvited(UUID player) { return invites.contains(player); }
	public int size() { return lobby.players().size(); }
	public boolean empty() { return lobby.players().isEmpty(); }
	public MatchRuntime match() { return match; }

	public String state() {
		if (match != null && match.match() != null) return match.match().state().name();
		return "PREPARING";
	}

	public String status() { return match == null ? "" : match.status(); }

	public boolean visibleTo(UUID viewer) {
		return visibility == Visibility.PUBLIC || isMember(viewer) || isInvited(viewer);
	}

	public boolean joinableBy(UUID viewer) {
		if (locked() || size() >= maxPlayers()) return false;
		if (isMember(viewer)) return false;
		return visibility == Visibility.PUBLIC || isInvited(viewer) || isOwner(viewer);
	}

	// ---- Membership --------------------------------------------------------------------------------

	public String join(UUID player) {
		if (locked()) return FlakeI18n.tr("flake.lobby.err.locked");
		if (isMember(player)) return FlakeI18n.tr("flake.lobby.err.already_in");
		if (size() >= lobby.maxPlayers()) return FlakeI18n.tr("flake.lobby.err.full", size(), lobby.maxPlayers());
		if (visibility == Visibility.PRIVATE && !invites.contains(player) && !isOwner(player))
			return FlakeI18n.tr("flake.lobby.err.private");
		if (!lobby.join(player)) return FlakeI18n.tr("flake.lobby.err.locked");
		invites.remove(player);
		return "";
	}

	public String leave(UUID player) {
		if (!isMember(player)) return FlakeI18n.tr("flake.lobby.err.not_in");
		if (match != null && match.owns(player)) {
			pendingLeaves.add(player);
			match.stop(playerName(player) + " залишив лобі; дуель скасовано.");
			return FlakeI18n.tr("flake.lobby.err.wait_duel");
		}
		if (locked()) return FlakeI18n.tr("flake.lobby.err.wait_duel");
		lobby.leave(player);
		if (player.equals(owner)) {
			var next = lobby.players().keySet().stream().findFirst().orElse(null);
			owner = next;
			if (next != null) invites.clear();
		}
		return "";
	}

	public String kick(UUID actor, UUID target) {
		if (!isOwner(actor)) return FlakeI18n.tr("flake.lobby.err.not_owner");
		if (!isMember(target)) return FlakeI18n.tr("flake.lobby.err.not_in");
		if (target.equals(actor)) return FlakeI18n.tr("flake.lobby.err.not_self");
		if (locked() || (match != null && match.match() != null)) return FlakeI18n.tr("flake.lobby.err.wait_duel");
		lobby.leave(target);
		invites.remove(target);
		return "";
	}

	public String transferOwnership(UUID actor, UUID target) {
		if (!isOwner(actor)) return FlakeI18n.tr("flake.lobby.err.not_owner");
		if (!isMember(target)) return FlakeI18n.tr("flake.lobby.err.not_in");
		if (target.equals(actor)) return FlakeI18n.tr("flake.lobby.err.not_self");
		if (locked() || (match != null && match.match() != null)) return FlakeI18n.tr("flake.lobby.err.wait_duel");
		owner = target;
		return "";
	}

	public String invite(UUID actor, UUID target) {
		if (!isOwner(actor)) return FlakeI18n.tr("flake.lobby.err.not_owner");
		if (isMember(target)) return FlakeI18n.tr("flake.lobby.err.already_in_room");
		invites.add(target);
		return "";
	}

	public String revokeInvite(UUID actor, UUID target) {
		if (!isOwner(actor)) return FlakeI18n.tr("flake.lobby.err.not_owner");
		invites.remove(target);
		return "";
	}

	// ---- Readiness / teams -------------------------------------------------------------------------

	public String ready(UUID player, boolean ready, boolean buildValid) {
		if (locked() || (match != null && match.match() != null)) return FlakeI18n.tr("flake.lobby.err.locked");
		if (!isMember(player)) return FlakeI18n.tr("flake.lobby.err.join_first");
		if (ready && !buildValid) return FlakeI18n.tr("flake.lobby.err.invalid_build");
		lobby.setReady(player, ready);
		return "";
	}

	public String switchTeam(UUID player, int team) {
		if (locked() || (match != null && match.match() != null)) return FlakeI18n.tr("flake.lobby.err.locked");
		if (!isMember(player)) return FlakeI18n.tr("flake.lobby.err.join_first");
		if (!lobby.switchTeam(player, team)) return FlakeI18n.tr("flake.lobby.err.team_full");
		return "";
	}

	public void buildChanged(UUID player) { lobby.buildChanged(player); }

	// ---- Settings ----------------------------------------------------------------------------------

	public String updateSettings(UUID actor, String rawMode, String rawVisibility, String rawArena, int teams, int size) {
		if (!isOwner(actor)) return FlakeI18n.tr("flake.lobby.err.not_owner");
		if (locked() || (match != null && match.match() != null)) return FlakeI18n.tr("flake.lobby.err.locked");
		String m = rawMode == null || rawMode.isBlank() ? mode : rawMode.trim().toUpperCase();
		if (!MODES.contains(m)) return FlakeI18n.tr("flake.lobby.err.bad_mode");
		Visibility v = switch (rawVisibility == null ? "" : rawVisibility.trim().toUpperCase()) {
			case "PRIVATE" -> Visibility.PRIVATE;
			case "PUBLIC" -> Visibility.PUBLIC;
			default -> visibility;
		};
		String a = rawArena == null || rawArena.isBlank() ? arena : rawArena.trim().toLowerCase();
		if (!ArenaRepository.validName(a)) return FlakeI18n.tr("flake.lobby.err.bad_arena");
		mode = m;
		visibility = v;
		arena = a;
		lobby.configure(m.equals("DUEL") ? 2 : Math.clamp(teams, 2, 4), m.equals("DUEL") ? 1 : Math.clamp(size, 1, 5));
		lobby.resetReady();
		return "";
	}

	// ---- Match -------------------------------------------------------------------------------------

	public MatchRuntime ensureMatch() {
		if (manager == null) return null;
		if (match == null) {
			match = DuelRuntime.create(manager.server(), id, lobby, manager.arenas(), this::arena);
		}
		return match;
	}

	public String start(UUID actor) {
		if (!isOwner(actor)) return FlakeI18n.tr("flake.lobby.err.not_owner");
		if (locked() || (match != null && match.match() != null)) return FlakeI18n.tr("flake.lobby.err.locked");
		if (size() < 2) return FlakeI18n.tr("flake.lobby.err.need_two");
		var runtime = ensureMatch();
		return runtime == null ? FlakeI18n.tr("flake.lobby.err.not_ready") : runtime.requestStart();
	}

	public String stopMatch(UUID actor) {
		if (!isOwner(actor)) return FlakeI18n.tr("flake.lobby.err.not_owner");
		if (match == null || match.match() == null) return FlakeI18n.tr("flake.lobby.err.no_match");
		match.stop(FlakeI18n.tr("flake.lobby.notice.owner_stopped"));
		return "";
	}

	/** Server-side removal that bypasses readiness locks (disconnect, shutdown). */
	public void forceRemove(UUID player) {
		pendingLeaves.remove(player);
		invites.remove(player);
		lobby.forceLeave(player);
		if (player.equals(owner)) owner = lobby.players().keySet().stream().findFirst().orElse(null);
	}

	/** Completes leaves that were deferred until the running match finished restoring. */
	public void processPendingLeaves() {
		if (pendingLeaves.isEmpty()) return;
		if (match != null && match.match() != null) return;
		for (var id : new LinkedHashSet<>(pendingLeaves)) {
			pendingLeaves.remove(id);
			lobby.forceLeave(id);
			if (id.equals(owner)) owner = lobby.players().keySet().stream().findFirst().orElse(null);
		}
	}

	/** Room-side cleanup when the last member leaves or the server stops. */
	public void close(String reason) {
		if (match != null) match.stop(reason);
		match = null;
		DuelRuntime.remove(id);
		lobby.clear();
		invites.clear();
		owner = null;
	}

	private String playerName(UUID id) {
		var server = manager == null ? null : manager.server();
		var player = server == null ? null : server.getPlayerList().getPlayer(id);
		return player == null ? id.toString().substring(0, 8) : player.getName().getString();
	}

	public static String sanitizeName(String raw) {
		if (raw == null) return "";
		var cleaned = raw.trim();
		if (cleaned.isEmpty() || cleaned.length() > 32) return "";
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if (Character.isISOControl(c) || c == '§') return "";
		}
		return cleaned;
	}

	public static boolean busy(MatchRuntime match) {
		return match != null && match.match() != null && match.match().state() != DuelMatch.State.COUNTDOWN;
	}

	@Override public String toString() { return "LobbyRoom[" + id + " '" + title + "' " + size() + "/" + maxPlayers() + "]"; }
}