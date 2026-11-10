package dev.flake.ui;

import dev.flake.ability.ClassSkills;
import dev.flake.build.Loadout;
import dev.flake.build.LoadoutCommands;
import dev.flake.duel.ControlHammer;
import dev.flake.duel.CrystalClaw;
import dev.flake.duel.DuelMatch;
import dev.flake.duel.DuelRuntime;
import dev.flake.duel.MatchRuntime;
import dev.flake.duel.Matadora;
import dev.flake.duel.ShockwaveVisuals;
import dev.flake.shared.FlakeI18n;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Networking and per-viewer views for independent lobby rooms.
 * Every action is validated by {@link LobbyManager}/{@link LobbyRoom}; this class only routes packets
 * and builds snapshots that respect room visibility.
 */
public final class FlakeUiServer implements ModInitializer {
	private final HashMap<UUID, Integer> lastAction = new HashMap<>();
	private static final HashMap<UUID, HashMap<String, Integer>> effectTotals = new HashMap<>();

	@Override public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(LobbyActionPayload.TYPE, LobbyActionPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BuildApplyPayload.TYPE, BuildApplyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BuildResultPayload.TYPE, BuildResultPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LobbySnapshotPayload.TYPE, LobbySnapshotPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HudStatePayload.TYPE, HudStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LobbyActionPayload.TYPE, (payload, context) ->
				context.server().execute(() -> act(context.server(), context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(BuildApplyPayload.TYPE, (payload, context) ->
				context.server().execute(() -> applyBuild(context.server(), context.player(), payload)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 2 == 0) broadcastHud(server);
			if (server.getTickCount() % 10 == 0) broadcast(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			lastAction.remove(handler.player.getUUID());
			effectTotals.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> { lastAction.clear(); effectTotals.clear(); });
	}

	private void act(MinecraftServer server, ServerPlayer player, LobbyActionPayload action) {
		var manager = LobbyManager.of(server);
		if (manager == null) return;
		int now = server.getTickCount();
		Integer last = lastAction.get(player.getUUID());
		if (last != null && now - last < 2 && action.action() != LobbyActionPayload.QUERY) {
			send(player, snapshot(server, player, FlakeI18n.tr("flake.lobby.err.rate_limit")));
			return;
		}
		lastAction.put(player.getUUID(), now);
		String roomId = action.roomId() == null || action.roomId().isBlank() ? "" : action.roomId();
		var room = manager.roomOf(player.getUUID());
		String error = switch (action.action()) {
			case LobbyActionPayload.QUERY -> "";
			case LobbyActionPayload.CREATE -> room == null
					? manager.create(player, action.name(), action.mode(), action.visibility(), action.arena(),
							action.teamCount(), action.teamSize())
					: FlakeI18n.tr("flake.lobby.err.already_in_room");
			case LobbyActionPayload.JOIN -> room == null ? joinById(manager, player, roomId) : FlakeI18n.tr("flake.lobby.err.already_in_room");
			case LobbyActionPayload.LEAVE -> {
				DuelRuntime.stopFor(player);
				yield manager.leave(player);
			}
			case LobbyActionPayload.READY, LobbyActionPayload.NOT_READY -> room == null
					? FlakeI18n.tr("flake.lobby.err.join_first")
					: room.ready(player.getUUID(), action.action() == LobbyActionPayload.READY, LoadoutCommands.loadout(player.getUUID()).errors().isEmpty());
			case LobbyActionPayload.SWITCH_TEAM -> room == null
					? FlakeI18n.tr("flake.lobby.err.join_first")
					: room.switchTeam(player.getUUID(), action.teamCount());
			case LobbyActionPayload.START -> room == null ? FlakeI18n.tr("flake.lobby.err.join_first") : room.start(player.getUUID());
			case LobbyActionPayload.STOP -> room == null ? FlakeI18n.tr("flake.lobby.err.no_match") : room.stopMatch(player.getUUID());
			case LobbyActionPayload.SETTINGS -> room == null ? FlakeI18n.tr("flake.lobby.err.join_first")
					: room.updateSettings(player.getUUID(), action.mode(), action.visibility(), action.arena(),
							action.teamCount(), action.teamSize());
			case LobbyActionPayload.KICK -> room == null ? FlakeI18n.tr("flake.lobby.err.not_in")
					: withTarget(action.target(), target -> room.kick(player.getUUID(), target));
			case LobbyActionPayload.INVITE -> room == null ? FlakeI18n.tr("flake.lobby.err.not_in")
					: withTarget(action.target(), target -> room.invite(player.getUUID(), target));
			case LobbyActionPayload.UNINVITE -> room == null ? FlakeI18n.tr("flake.lobby.err.not_in")
					: withTarget(action.target(), target -> room.revokeInvite(player.getUUID(), target));
			case LobbyActionPayload.TRANSFER -> room == null ? FlakeI18n.tr("flake.lobby.err.not_in")
					: withTarget(action.target(), target -> room.transferOwnership(player.getUUID(), target));
			case LobbyActionPayload.DELETE -> manager.delete(player);
			default -> FlakeI18n.tr("flake.lobby.err.unknown_action");
		};
		broadcast(server);
		broadcastHud(server);
		if (!error.isEmpty()) send(player, snapshot(server, player, error));
	}

	private static String joinById(LobbyManager manager, ServerPlayer player, String roomId) {
		if (roomId.isBlank()) {
			var first = manager.visibleRooms(player.getUUID()).stream()
					.filter(r -> r.joinableBy(player.getUUID())).findFirst().orElse(null);
			if (first == null) return FlakeI18n.tr("flake.lobby.err.no_open_rooms");
			return manager.join(player, first.id());
		}
		try {
			return manager.join(player, UUID.fromString(roomId));
		} catch (IllegalArgumentException invalid) {
			return FlakeI18n.tr("flake.lobby.err.not_found");
		}
	}

	private static String withTarget(String raw, java.util.function.Function<UUID, String> action) {
		try {
			return action.apply(UUID.fromString(raw));
		} catch (IllegalArgumentException | NullPointerException invalid) {
			return FlakeI18n.tr("flake.lobby.err.not_found");
		}
	}

	private void applyBuild(MinecraftServer server, ServerPlayer player, BuildApplyPayload payload) {
		var id = player.getUUID();
		if (DuelRuntime.locked(id)) {
			sendResult(player, new BuildResultPayload(BuildResultPayload.Status.LOCKED, LoadoutCommands.revision(id),
					FlakeI18n.tr("flake.editor.error.locked")));
			return;
		}
		Loadout desired;
		try {
			desired = payload.toLoadout();
		} catch (IllegalArgumentException | NullPointerException invalid) {
			sendResult(player, new BuildResultPayload(BuildResultPayload.Status.INVALID, LoadoutCommands.revision(id),
					FlakeI18n.tr("flake.editor.error.invalid")));
			return;
		}
		var previous = LoadoutCommands.loadout(id);
		var result = LoadoutCommands.apply(id, payload.revision(), desired);
		if (result.ok()) {
			if (result.build().fighterClass() != previous.fighterClass()) LoadoutCommands.onClassChanged(player);
			var room = LobbyManager.of(server) == null ? null : LobbyManager.of(server).roomOf(id);
			if (room != null) room.buildChanged(id);
			sendResult(player, new BuildResultPayload(BuildResultPayload.Status.OK, result.revision(), ""));
			sync(server);
			return;
		}
		String message = switch (result.status()) {
			case STALE -> FlakeI18n.tr("flake.editor.error.stale");
			case SAVE_FAILED -> FlakeI18n.tr("flake.editor.error.save");
			case INVALID -> String.join(" · ", result.errors());
			default -> "";
		};
		BuildResultPayload.Status status = switch (result.status()) {
			case STALE -> BuildResultPayload.Status.STALE;
			case SAVE_FAILED -> BuildResultPayload.Status.SAVE_FAILED;
			default -> BuildResultPayload.Status.INVALID;
		};
		sendResult(player, new BuildResultPayload(status, result.revision(), message));
		sync(server);
	}

	private static void sendResult(ServerPlayer player, BuildResultPayload result) {
		if (ServerPlayNetworking.canSend(player, BuildResultPayload.TYPE)) ServerPlayNetworking.send(player, result);
	}

	public static void sync(MinecraftServer server) {
		if (server == null || LobbyManager.of(server) == null) return;
		broadcastHud(server);
		broadcast(server);
	}

	private static void broadcast(MinecraftServer server) {
		if (LobbyManager.of(server) == null) return;
		for (var player : server.getPlayerList().getPlayers()) send(player, snapshot(server, player, ""));
	}

	private static void broadcastHud(MinecraftServer server) {
		for (var player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, HudStatePayload.TYPE)) {
				ServerPlayNetworking.send(player, hudState(server, player));
			}
		}
	}

	private static HudStatePayload hudState(MinecraftServer server, ServerPlayer player) {
		var id = player.getUUID();
		var build = LoadoutCommands.loadout(id);
		int now = server.getTickCount();
		var manager = LobbyManager.of(server);
		var room = manager == null ? null : manager.roomOf(id);
		var members = room == null ? java.util.Map.<UUID, Boolean>of() : room.lobby().players();
		MatchRuntime runtime = DuelRuntime.runtime(id);
		DuelMatch match = runtime == null ? null : runtime.match();
		var participants = runtime == null ? List.<UUID>of() : runtime.participants();
		HudStatePayload.Fighter first = HudStatePayload.EMPTY_FIGHTER;
		HudStatePayload.Fighter second = HudStatePayload.EMPTY_FIGHTER;
		int firstWins = 0, secondWins = 0, winsNeeded = 1, round = 1;
		if (match != null && participants.size() == 2) {
			UUID leftId = participants.get(0);
			UUID rightId = participants.get(1);
			firstWins = match.firstWins();
			secondWins = match.secondWins();
			if (id.equals(rightId)) {
				leftId = participants.get(1);
				rightId = participants.get(0);
				firstWins = match.secondWins();
				secondWins = match.firstWins();
			}
			winsNeeded = match.winsNeeded();
			round = match.firstWins() + match.secondWins() + 1;
			first = fighterInfo(server, leftId, id, match);
			second = fighterInfo(server, rightId, id, match);
		} else if (!members.isEmpty()) {
			var ordered = members.keySet().stream().sorted(Comparator.comparing(uid -> !uid.equals(id))).toList();
			if (!ordered.isEmpty()) first = fighterInfo(server, ordered.get(0), id, null);
			if (ordered.size() > 1) second = fighterInfo(server, ordered.get(1), id, null);
		}
		var clsSpec = dev.flake.shared.SliceRegistry.fighterClass(build.fighterClass());
		HudStatePayload.Role role = match != null && participants.contains(id) ? HudStatePayload.Role.PARTICIPANT
				: match != null ? HudStatePayload.Role.SPECTATOR : HudStatePayload.Role.NONE;
		String blockReason = "";
		int blockRemaining = 0;
		int stun = ControlHammer.stunRemaining(id, now);
		if (stun > 0) {
			blockReason = "stun";
			blockRemaining = stun;
		} else if (!player.isAlive() || player.isSpectator()) {
			blockReason = "dead";
		} else if (runtime != null && "COUNTDOWN".equals(runtime.phase()) && participants.contains(id)) {
			blockReason = "countdown";
			blockRemaining = runtime.timerTicks();
		} else if (DuelRuntime.protectedInventory(id) && !DuelRuntime.fighting(id)) {
			blockReason = "inventory";
		}
		return new HudStatePayload(
				build.fighterClass().name(),
				build.helmet().name(),
				build.chestplate().name(),
				build.leggings().name(),
				build.boots().name(),
				build.weapon().name(),
				build.passiveItem().name(),
				build.activeItem1().name(),
				build.activeItem2().name(),
				build.cost(),
				Loadout.BUDGET,
				build.errors().isEmpty(),
				clsSpec.cooldownRemaining(id, 1, now),
				clsSpec.cooldownTotal(id, 1, now),
				clsSpec.activeElapsed(id, 1, now),
				clsSpec.activeTotal(id, 1, now),
				clsSpec.cooldownRemaining(id, 2, now),
				clsSpec.cooldownTotal(id, 2, now),
				clsSpec.activeElapsed(id, 2, now),
				clsSpec.activeTotal(id, 2, now),
				DuelRuntime.blocksAbility(id) || !player.isAlive() || player.isSpectator(),
				ShockwaveVisuals.isEnabled(id),
				runtime == null ? "PREPARING" : runtime.phase(),
				round,
				runtime == null ? 0 : runtime.timerTicks(),
				runtime == null ? 0 : runtime.timerTotalTicks(),
				firstWins,
				secondWins,
				winsNeeded,
				first,
				second,
				room == null ? "" : room.title(),
				members.size(),
				members.containsKey(id),
				Boolean.TRUE.equals(members.get(id)),
				LoadoutCommands.revision(id),
				role,
				loadState(LoadoutCommands.state(id)),
				blockReason,
				blockRemaining,
				itemCooldowns(player),
				statuses(player, id, now)
		);
	}

	private static HudStatePayload.LoadState loadState(dev.flake.build.LoadoutStore.State state) {
		return switch (state) {
			case MISSING -> HudStatePayload.LoadState.MISSING;
			case LOADED -> HudStatePayload.LoadState.LOADED;
			case CORRUPT -> HudStatePayload.LoadState.CORRUPT;
		};
	}

	/** Cooldown totals for every cooldown-bearing item the player actually carries. */
	private static List<HudStatePayload.ItemCooldown> itemCooldowns(ServerPlayer player) {
		var list = new ArrayList<HudStatePayload.ItemCooldown>(4);
		var seen = new HashSet<String>();
		for (int slot = 0; slot <= 9; slot++) {
			var stack = slot == 9 ? player.getOffhandItem() : player.getInventory().getItem(slot);
			if (stack.isEmpty()) continue;
			float total = dev.flake.shared.SliceRegistry.cooldownSeconds(stack);
			int totalTicks = Math.round(total * 20f);
			// The hammer's cooldown is shortened by passives, so its base spec is not the real length.
			if (ControlHammer.isHammer(stack)) totalTicks = ControlHammer.cooldownTotalTicks(player.getUUID());
			if (totalTicks <= 0) continue;
			var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (key == null || !seen.add(key.toString())) continue;
			list.add(new HudStatePayload.ItemCooldown(key.toString(), totalTicks));
		}
		return list;
	}

	private static List<HudStatePayload.Status> statuses(ServerPlayer player, UUID id, int now) {
		var list = new ArrayList<HudStatePayload.Status>(6);
		int stun = ControlHammer.stunRemaining(id, now);
		if (stun > 0) list.add(new HudStatePayload.Status("stun", stun, ControlHammer.STUN_TICKS, 0));
		int bulwark = ClassSkills.bulwarkRemaining(id, now);
		if (bulwark > 0) list.add(new HudStatePayload.Status("bulwark", bulwark, ClassSkills.bulwarkTicks(), 1));
		int bloodlust = ClassSkills.bloodlustRemaining(id, now);
		if (bloodlust > 0) list.add(new HudStatePayload.Status("bloodlust", bloodlust, ClassSkills.bloodlustTicks(), 1));
		int matadora = Matadora.remaining(id, now);
		if (matadora > 0) list.add(new HudStatePayload.Status("matadora", matadora, Matadora.BUFF_TICKS, 1));
		int crystalClaw = CrystalClaw.remaining(id, now);
		if (crystalClaw > 0) list.add(new HudStatePayload.Status("crystal_claw", crystalClaw, CrystalClaw.BUFF_TICKS, 1));

		var totals = effectTotals.computeIfAbsent(id, k -> new HashMap<>());
		var live = new HashSet<String>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (list.size() >= HudStatePayload.MAX_STATUSES) break;
			if (!effect.showIcon()) continue;
			var key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
			if (key == null) continue;
			String keyString = key.toString();
			live.add(keyString);
			int duration = effect.getDuration();
			if (effect.isInfiniteDuration()) {
				list.add(new HudStatePayload.Status(keyString, 0, 0, effect.getEffect().value().isBeneficial() ? 1 : 0,
						effect.getAmplifier(), true));
				continue;
			}
			if (duration <= 0) continue;
			int total = totals.getOrDefault(keyString, 0);
			if (total <= 0 || duration > total) {
				total = duration;
				totals.put(keyString, total);
			}
			int tone = effect.getEffect().value().isBeneficial() ? 1 : 0;
			list.add(new HudStatePayload.Status(keyString, duration, total, tone, effect.getAmplifier(), false));
		}
		totals.keySet().retainAll(live);
		return list;
	}

	private static HudStatePayload.Fighter fighterInfo(MinecraftServer server, UUID fighterId, UUID viewerId, DuelMatch match) {
		var target = server.getPlayerList().getPlayer(fighterId);
		String name = target != null ? target.getName().getString() : fighterId.toString().substring(0, 8);
		Loadout build = match != null && match.loadouts().containsKey(fighterId)
				? match.loadouts().get(fighterId) : LoadoutCommands.loadout(fighterId);
		float health = target != null ? Math.max(0f, target.getHealth() + target.getAbsorptionAmount()) : 0f;
		float maxHealth = target != null ? Math.max(1f, target.getMaxHealth()) : 20f;
		return new HudStatePayload.Fighter(name, build.fighterClass().name(), health, maxHealth, fighterId.equals(viewerId));
	}

	/** Snapshot for one viewer: their own room plus every room they may see. */
	private static LobbySnapshotPayload snapshot(MinecraftServer server, ServerPlayer viewer, String error) {
		var manager = LobbyManager.of(server);
		var viewerId = viewer.getUUID();
		var room = manager == null ? null : manager.roomOf(viewerId);
		var members = room == null ? java.util.Map.<UUID, Boolean>of() : room.lobby().players();

		// Members of the viewer's own room always come first, then other online players (bounded payload).
		// This lets the tab overlay show which room every player belongs to instead of hiding outsiders.
		var players = new ArrayList<LobbySnapshotPayload.Player>(Math.min(ROSTER_CAP, server.getPlayerList().getPlayerCount()));
		for (var memberId : members.keySet()) {
			var candidate = server.getPlayerList().getPlayer(memberId);
			players.add(playerRow(memberId, candidate, true, Boolean.TRUE.equals(members.get(memberId)), room, manager));
		}
		var others = server.getPlayerList().getPlayers().stream()
				.filter(candidate -> !members.containsKey(candidate.getUUID()))
				.sorted(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER))
				.limit(Math.max(0, ROSTER_CAP - players.size())).toList();
		for (var candidate : others) {
			players.add(playerRow(candidate.getUUID(), candidate, false, false, room, manager));
		}

		var summaries = new ArrayList<LobbySnapshotPayload.RoomSummary>();
		if (manager != null) {
			for (var candidate : manager.visibleRooms(viewerId)) {
				var ownerPlayer = server.getPlayerList().getPlayer(candidate.owner());
				summaries.add(new LobbySnapshotPayload.RoomSummary(
						candidate.id().toString(),
						candidate.title(),
						ownerPlayer == null ? "" : ownerPlayer.getName().getString(),
						candidate.mode(),
						candidate.arena(),
						candidate.size(),
						candidate.maxPlayers(),
						candidate.state(),
						candidate.visibility() == LobbyRoom.Visibility.PRIVATE,
						candidate.isInvited(viewerId),
						candidate.joinableBy(viewerId),
						candidate.isMember(viewerId)
				));
			}
		}

		MatchRuntime runtime = room == null ? null : room.match();
		return new LobbySnapshotPayload(
				room == null ? "" : room.id().toString(),
				room == null ? "" : room.title(),
				room == null || room.owner() == null ? "" : room.owner().toString(),
				room == null ? "DUEL" : room.mode(),
				room == null ? "PUBLIC" : room.visibility().name(),
				room == null ? "" : room.arena(),
				room == null ? 2 : room.teamCount(),
				room == null ? 1 : room.teamSize(),
				room == null ? 0 : room.maxPlayers(),
				room != null && room.isOwner(viewerId),
				room != null && room.isMember(viewerId),
				Boolean.TRUE.equals(members.get(viewerId)),
				players,
				summaries,
				manager == null ? List.of() : manager.arenaNames(),
				error,
				runtime == null ? "PREPARING" : runtime.phase(),
				runtime == null ? "" : runtime.status()
		);
	}

	/** Upper bound on roster rows per snapshot; keeps the 10-tick broadcast small on busy servers. */
	private static final int ROSTER_CAP = 256;

	private static LobbySnapshotPayload.Player playerRow(UUID id, ServerPlayer online, boolean member, boolean ready,
			LobbyRoom room, LobbyManager manager) {
		var build = LoadoutCommands.loadout(id);
		var playerRoom = manager == null ? null : manager.roomOf(id);
		return new LobbySnapshotPayload.Player(
				id,
				online == null ? id.toString().substring(0, 8) : online.getName().getString(),
				build.fighterClass().name(),
				online == null ? 0 : online.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)),
				online == null ? 0 : online.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)),
				online == null ? 0 : online.connection.latency(),
				member,
				ready,
				build.errors().isEmpty(),
				member && room != null ? room.lobby().teamOf(id) : -1,
				member && room != null && room.isOwner(id),
				member && room != null && room.isInvited(id),
				playerRoom == null ? "" : playerRoom.title());
	}

	private static void send(ServerPlayer player, LobbySnapshotPayload payload) {
		if (ServerPlayNetworking.canSend(player, LobbySnapshotPayload.TYPE)) ServerPlayNetworking.send(player, payload);
	}
}