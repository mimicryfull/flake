package dev.flake.features.loadout;

import dev.flake.features.duel.DuelRuntime;
import dev.flake.combat.ability.ClassAbilityRuntimes;
import dev.flake.features.lobby.LobbyManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Authoritative loadout application boundary. All mutations run on the owning server thread. */
public final class LoadoutService {
	private static final Map<MinecraftServer, LoadoutService> INSTANCES = new HashMap<>();
	private static boolean installed;
	private final MinecraftServer server;
	private final LoadoutManager builds = new LoadoutManager();

	private LoadoutService(MinecraftServer server) {
		this.server = server;
		builds.bind(server.getWorldPath(LevelResource.ROOT).resolve("flake/builds"));
	}

	public static void install() {
		if (installed) return;
		installed = true;
		ServerLifecycleEvents.SERVER_STARTED.register(LoadoutService::of);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			var service = INSTANCES.remove(server);
			if (service != null) service.builds.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			var service = INSTANCES.get(server);
			if (service != null) service.builds.remove(handler.player.getUUID());
		});
	}

	public static LoadoutService of(MinecraftServer server) {
		return INSTANCES.computeIfAbsent(server, LoadoutService::new);
	}

	public Loadout get(UUID id) { return builds.get(id); }
	public long revision(UUID id) { return builds.revision(id); }
	public LoadoutStore.State state(UUID id) { return builds.state(id); }
	public boolean locked(UUID id) { return DuelRuntime.locked(id); }

	public enum Status { OK, INVALID, STALE, SAVE_FAILED, LOCKED }

	public record Result(Status status, long revision, Loadout build, List<String> errors) {
		public boolean ok() { return status == Status.OK; }
	}

	public Result update(ServerPlayer player, UnaryOperator<Loadout> change) {
		return mutate(player, -1, change, false);
	}

	public Result apply(ServerPlayer player, long expectedRevision, Loadout desired) {
		return mutate(player, expectedRevision, previous -> desired, true);
	}

	private Result mutate(ServerPlayer player, long expectedRevision, UnaryOperator<Loadout> change, boolean replacement) {
		if (player.level().getServer() != server) throw new IllegalArgumentException("Wrong loadout server");
		var id = player.getUUID();
		if (locked(id)) return new Result(Status.LOCKED, revision(id), get(id), List.of());
		Loadout previous = get(id);
		Result result;
		if (replacement) {
			var applied = builds.apply(id, expectedRevision, change.apply(previous));
			result = new Result(Status.valueOf(applied.status().name()), applied.revision(), applied.build(), applied.errors());
		} else {
			var updated = builds.updateChecked(id, change);
			result = new Result(Status.valueOf(updated.status().name()), revision(id), updated.build(), updated.errors());
		}
		if (result.ok()) {
			if (result.build().fighterClass() != previous.fighterClass()) ClassAbilityRuntimes.classChanged(player);
			// Whole-build application historically resets readiness even when the build is unchanged.
			if (replacement || !result.build().equals(previous)) {
				var lobbies = LobbyManager.of(server);
				var room = lobbies == null ? null : lobbies.roomOf(id);
				if (room != null) room.buildChanged(id);
			}
		}
		return result;
	}

}
