package dev.flake.features.hud;

import dev.flake.features.loadout.Loadout;
import dev.flake.features.loadout.LoadoutStore;
import dev.flake.combat.CombatModifiers;
import dev.flake.combat.CombatStatuses;
import dev.flake.combat.Stuns;
import dev.flake.features.duel.DuelMatch;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.duel.MatchRuntime;
import dev.flake.features.loadout.LoadoutService;
import dev.flake.features.lobby.LobbyManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

/** HUD read model. Feature status providers hide item implementations from this projection. */
public final class HudProjection {
	private static final Map<MinecraftServer, HudProjection> INSTANCES = new HashMap<>();
	private final Map<UUID, Map<String, Integer>> effectTotals = new HashMap<>();
	private HudProjection() {}

	public static HudStatePayload project(MinecraftServer server, ServerPlayer player) {
		return INSTANCES.computeIfAbsent(server, key -> new HudProjection()).state(server, player);
	}

	public static void disconnect(MinecraftServer server, UUID id) {
		var projection = INSTANCES.get(server);
		if (projection != null) projection.effectTotals.remove(id);
	}

	public static void clear(MinecraftServer server) { INSTANCES.remove(server); }

	private HudStatePayload state(MinecraftServer server, ServerPlayer player) {
		var id = player.getUUID();
		var builds = LoadoutService.of(server);
		var build = builds.get(id);
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
		var clsSpec = dev.flake.content.ContentCatalog.fighterClass(build.fighterClass());
		HudStatePayload.Role role = match != null && participants.contains(id) ? HudStatePayload.Role.PARTICIPANT
				: match != null ? HudStatePayload.Role.SPECTATOR : HudStatePayload.Role.NONE;
		String blockReason = "";
		int blockRemaining = 0;
		int stun = Stuns.remaining(player, now);
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
		return new HudStatePayload(build.fighterClass().name(), build.helmet().name(), build.chestplate().name(),
				build.leggings().name(), build.boots().name(), build.weapon().name(), build.passiveItem().name(),
				build.activeItem1().name(), build.activeItem2().name(), build.errors().isEmpty(),
				clsSpec.cooldownRemaining(player, 1, now), clsSpec.cooldownTotal(player, 1, now),
				clsSpec.activeElapsed(player, 1, now), clsSpec.activeTotal(player, 1, now),
				clsSpec.cooldownRemaining(player, 2, now), clsSpec.cooldownTotal(player, 2, now),
				clsSpec.activeElapsed(player, 2, now), clsSpec.activeTotal(player, 2, now),
				DuelRuntime.blocksAbility(id) || !player.isAlive() || player.isSpectator(), dev.flake.render.vfx.CombatVisuals.isEnabled(player),
				runtime == null ? "PREPARING" : runtime.phase(), round, runtime == null ? 0 : runtime.timerTicks(),
				runtime == null ? 0 : runtime.timerTotalTicks(), firstWins, secondWins, winsNeeded, first, second,
				room == null ? "" : room.title(), members.size(), members.containsKey(id), Boolean.TRUE.equals(members.get(id)),
				builds.revision(id), role, loadState(builds.state(id)), blockReason, blockRemaining,
				itemCooldowns(player), statuses(player, id, now));
	}

	private static HudStatePayload.LoadState loadState(LoadoutStore.State state) {
		return switch (state) {
			case MISSING -> HudStatePayload.LoadState.MISSING;
			case LOADED -> HudStatePayload.LoadState.LOADED;
			case CORRUPT -> HudStatePayload.LoadState.CORRUPT;
		};
	}

	private static List<HudStatePayload.ItemCooldown> itemCooldowns(ServerPlayer player) {
		var list = new ArrayList<HudStatePayload.ItemCooldown>(4);
		var seen = new HashSet<String>();
		for (int slot = 0; slot <= 9; slot++) {
			var stack = slot == 9 ? player.getOffhandItem() : player.getInventory().getItem(slot);
			if (stack.isEmpty()) continue;
			int baseTicks = Math.round(dev.flake.content.ContentCatalog.cooldownSeconds(stack) * 20f);
			int totalTicks = CombatModifiers.itemCooldownTotal(player, stack, baseTicks);
			if (totalTicks <= 0) continue;
			var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (key == null || !seen.add(key.toString())) continue;
			list.add(new HudStatePayload.ItemCooldown(key.toString(), totalTicks));
		}
		return list;
	}

	private List<HudStatePayload.Status> statuses(ServerPlayer player, UUID id, int now) {
		var list = new ArrayList<>(CombatStatuses.statuses(player, now).stream()
				.map(status -> new HudStatePayload.Status(status.id(), status.ticksLeft(), status.totalTicks(), status.tone())).toList());
		var totals = effectTotals.computeIfAbsent(id, key -> new HashMap<>());
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
				list.add(new HudStatePayload.Status(keyString, 0, 0, effect.getEffect().value().isBeneficial() ? 1 : 0, effect.getAmplifier(), true));
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
				? match.loadouts().get(fighterId) : LoadoutService.of(server).get(fighterId);
		float health = target != null ? Math.max(0f, target.getHealth() + target.getAbsorptionAmount()) : 0f;
		float maxHealth = target != null ? Math.max(1f, target.getMaxHealth()) : 20f;
		return new HudStatePayload.Fighter(name, build.fighterClass().name(), health, maxHealth, fighterId.equals(viewerId));
	}
}
