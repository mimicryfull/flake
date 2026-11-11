package dev.flake.features.duel;

import dev.flake.features.duel.recovery.DuelRecovery;

import dev.flake.features.duel.kit.DuelEquipment;

import dev.flake.combat.Stuns;
import dev.flake.features.arena.ArenaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * Static entry point for combat state. It owns no match itself: every {@link MatchRuntime} belongs to
 * one lobby room, and these helpers resolve the single match a player currently participates in.
 * One player can never be in two matches, which is what makes the lookup unambiguous.
 */
public final class DuelRuntime {
	private static final dev.flake.combat.ServerState<Map<UUID, MatchRuntime>> MATCHES =
			new dev.flake.combat.ServerState<>(java.util.LinkedHashMap::new);
	private static boolean installed;

	private DuelRuntime() {}

	public static void install() {
		if (installed) return;
		installed = true;

		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				!level.isClientSide() && protectedInventory(player.getUUID()) && !fighting(player.getUUID())
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				!level.isClientSide() && protectedInventory(player.getUUID())
						&& (!fighting(player.getUUID()) || !(entity instanceof net.minecraft.world.entity.player.Player))
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				!level.isClientSide() && protectedInventory(player.getUUID()) && !fighting(player.getUUID())
						? InteractionResult.FAIL : InteractionResult.PASS);

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && DuelEquipment.isTemporary(item.getItem())) item.discard();
			if (entity instanceof AbstractArrow arrow && DuelEquipment.isTemporary(arrow.getPickupItemStackOrigin())
					&& (arrow.getOwner() == null || !fighting(arrow.getOwner().getUUID()))) arrow.discard();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (var match : List.copyOf(MATCHES.of(server).values())) match.stop("Сервер зупиняється; речі повертаються.");
			DuelRecovery.recoverOnline(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MATCHES.remove(server);
			DuelRecovery.clear(server);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var match : List.copyOf(MATCHES.of(server).values())) {
				try { match.tick(); }
				catch (RuntimeException failure) { dev.flake.Flake.LOGGER.error("Match tick failed for room {}", match.roomId(), failure); }
			}
			if (server.getTickCount() % 20 == 0) DuelRecovery.recoverOnline(server);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DuelRecovery.resume(server, handler.player, handler.player.isAlive()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			var match = of(handler.player.getUUID());
			if (match != null) match.stop("Гравець відключився; дуель скасовано.");
			DuelRecovery.recover(server, handler.player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> DuelRecovery.resume(
				player.level().getServer(), player, alive));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer victim) {
				var match = of(victim.getUUID());
				if (match != null && match.inCountdown(victim.getUUID())) return false;
			}
			boolean victimProtected = entity instanceof ServerPlayer p && protectedInventory(p.getUUID());
			boolean attackerProtected = source.getEntity() instanceof ServerPlayer p && protectedInventory(p.getUUID());
			if (victimProtected) {
				if (!fighting(entity.getUUID())) return false;
				return source.getEntity() == null || source.getEntity() instanceof ServerPlayer p && opponents(p.getUUID(), entity.getUUID());
			}
			return !attackerProtected;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			var match = of(entity.getUUID());
			if (match != null) match.finishRound(entity.getUUID());
		});
	}

	// ---- Match registry --------------------------------------------------------------------------

	/** Creates a match bound to one room. The caller (the room) keeps ownership of the instance. */
	public static MatchRuntime create(MinecraftServer server, UUID roomId, DuelLobby lobby,
			ArenaRepository arenas, java.util.function.Supplier<String> arenaName) {
		var match = new MatchRuntime(server, roomId, lobby, arenas, arenaName);
		MATCHES.of(server).put(roomId, match);
		return match;
	}

	public static void remove(UUID roomId) { for (var matches : MATCHES.values()) matches.remove(roomId); }
	public static void removeAll(MinecraftServer server) { MATCHES.remove(server); }
	public static MatchRuntime byRoom(UUID roomId) {
		for (var matches : MATCHES.values()) if (matches.containsKey(roomId)) return matches.get(roomId);
		return null;
	}
	public static List<MatchRuntime> all() {
		var result = new ArrayList<MatchRuntime>();
		for (var matches : MATCHES.values()) result.addAll(matches.values());
		return result;
	}

	public static MatchRuntime of(UUID playerId) {
		if (playerId == null) return null;
		for (var matches : MATCHES.values()) for (var match : matches.values()) if (match.owns(playerId)) return match;
		return null;
	}

	// ---- Per-player queries used across combat code ----------------------------------------------

	public static boolean protectedInventory(UUID id) { return DuelRecovery.isProtected(id); }

	public static boolean fighting(UUID id) {
		var match = of(id);
		return match != null && match.fighting(id);
	}

	public static boolean locked(UUID id) {
		if (protectedInventory(id)) return true;
		var match = of(id);
		return match != null && match.match() != null && match.match().state() != DuelMatch.State.COUNTDOWN && match.owns(id);
	}

	public static boolean blocksAbility(UUID id) {
		if (Stuns.active(id)) return true;
		if (protectedInventory(id) && !fighting(id)) return true;
		var match = of(id);
		return match != null && match.inCountdown(id);
	}

	public static boolean opponents(UUID first, UUID second) {
		if (first == null || second == null || first.equals(second)) return false;
		var match = of(first);
		return match != null && match.opponents(first, second);
	}

	public static dev.flake.features.loadout.Loadout.PassiveItem passiveOf(UUID id) {
		var match = of(id);
		return match == null ? dev.flake.features.loadout.Loadout.PassiveItem.NONE : match.passiveOf(id);
	}

	public static String phase(UUID id) {
		var match = of(id);
		return match == null ? "PREPARING" : match.phase();
	}

	public static DuelMatch match(UUID id) {
		var match = of(id);
		return match == null ? null : match.match();
	}

	public static MatchRuntime runtime(UUID id) { return of(id); }

	public static List<UUID> participants(UUID id) {
		var match = of(id);
		return match == null ? List.of() : match.participants();
	}

	public static String status(UUID id) {
		var match = of(id);
		return match == null ? "" : match.status();
	}

	public static int timerTicks(UUID id) {
		var match = of(id);
		return match == null ? 0 : match.timerTicks();
	}

	public static int timerTotalTicks(UUID id) {
		var match = of(id);
		return match == null ? 0 : match.timerTotalTicks();
	}

	public static boolean stopFor(ServerPlayer player) {
		var match = of(player.getUUID());
		if (match == null) return false;
		match.stop(player.getName().getString() + " зупинив дуель.");
		return true;
	}

	public static boolean retryRecovery(ServerPlayer player) {
		var server = player.level().getServer();
		if (!DuelRecovery.journal(server).exists(player.getUUID())) return false;
		if (of(player.getUUID()) != null) {
			stopFor(player);
			return true;
		}
		DuelRecovery.protect(server, player.getUUID());
		DuelRecovery.recover(server, player);
		return true;
	}
}
