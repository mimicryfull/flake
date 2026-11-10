package dev.flake.duel;

import dev.flake.ExampleMod;
import dev.flake.arena.Arena;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Crash-safe personal-state recovery. Protection is global per player (one player can only ever be
 * in one match), while the write-ahead journal is bound to the world of the server that created it.
 */
public final class DuelRecovery {
	private static final Map<MinecraftServer, RecoveryJournal> JOURNALS = new HashMap<>();
	private static final Set<UUID> PROTECTED = new HashSet<>();
	private static final Set<UUID> REPORTED_FAILURES = new HashSet<>();

	private DuelRecovery() {}

	public static RecoveryJournal journal(MinecraftServer server) {
		return JOURNALS.computeIfAbsent(server, s -> new RecoveryJournal(
				s.getWorldPath(LevelResource.ROOT).resolve("flake/recovery")));
	}

	public static boolean isProtected(UUID id) { return PROTECTED.contains(id); }
	public static void protect(UUID id) { PROTECTED.add(id); }
	public static void release(UUID id) { PROTECTED.remove(id); REPORTED_FAILURES.remove(id); }
	public static boolean hasPending(UUID id) { return PROTECTED.contains(id); }

	public static void clear(MinecraftServer server) {
		JOURNALS.remove(server);
		PROTECTED.clear();
		REPORTED_FAILURES.clear();
	}

	/** Runs for a player who just joined or respawned: any pending journal is restored immediately. */
	public static void resume(MinecraftServer server, ServerPlayer player, boolean alive) {
		if (player == null) return;
		if (!journal(server).exists(player.getUUID())) return;
		PROTECTED.add(player.getUUID());
		recover(server, player);
	}

	public static void recoverOnline(MinecraftServer server) {
		for (var id : Set.copyOf(PROTECTED)) {
			if (DuelRuntime.fighting(id)) continue;
			var player = server.getPlayerList().getPlayer(id);
			if (player != null) recover(server, player);
		}
	}

	public static void recover(MinecraftServer server, ServerPlayer player) {
		if (player == null) return;
		UUID id = player.getUUID();
		if (!PROTECTED.contains(id) || !player.isAlive()) return;
		var journal = journal(server);
		try {
			var state = DuelPlayerState.read(id, journal.read(id), server.registryAccess().createSerializationContext(NbtOps.INSTANCE));
			if (!player.entityTags().contains(state.receipt())) {
				ServerLevel destination = level(server, state.dimension());
				Arena.Position position = state.position();
				if (!safe(destination, position, player)) {
					destination = level(server, state.fallbackDimension()); position = state.fallback();
					if (!safe(destination, position, player)) throw new IOException("Обидві точки повернення заблоковані; резервну копію збережено");
				}
				// Validate the destination before replacing any live slots.
				state.restore(player);
				teleport(player, destination, position);
				player.entityTags().removeIf(tag -> tag.startsWith("flake_restored_"));
				player.addTag(state.receipt());
			}
			server.getPlayerList().saveAll();
			var saved = server.getPlayerList().loadPlayerData(new NameAndId(player.getUUID(), player.getGameProfile().name()))
					.orElseThrow(() -> new IOException("Не вдалося перевірити збереження гравця"));
			var tags = saved.getList("Tags").orElseThrow(() -> new IOException("Відсутнє підтвердження повернення речей"));
			boolean receipt = false;
			for (int index = 0; index < tags.size(); index++) if (tags.getStringOr(index, "").equals(state.receipt())) receipt = true;
			if (!receipt) throw new IOException("Збереження особистих речей на диск ще не підтверджено");
			journal.complete(id, state.token());
			PROTECTED.remove(id); REPORTED_FAILURES.remove(id);
			player.sendSystemMessage(Component.literal("Особисті речі, досвід і стан повернено.").withStyle(ChatFormatting.AQUA));
		} catch (Exception exception) {
			if (REPORTED_FAILURES.add(id)) {
				ExampleMod.LOGGER.error("Personal state recovery pending for {}. Original journal retained.", id, exception);
				player.sendSystemMessage(Component.literal("Повернення речей призупинено: " + exception.getMessage()
						+ ". Резервна копія збережена; /flake duel recover — повторити.").withStyle(ChatFormatting.RED));
			}
		}
	}

	static ServerLevel level(MinecraftServer server, String name) throws IOException {
		var result = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(name)));
		if (result == null) throw new IOException("Вимір недоступний: " + name);
		return result;
	}

	static boolean safe(ServerLevel level, Arena.Position point, ServerPlayer player) {
		var feet = BlockPos.containing(point.x(), point.y(), point.z());
		if (!level.isInWorldBounds(feet) || !level.isInWorldBounds(feet.above(2))) return false;
		level.getChunkAt(feet);
		var box = player.getDimensions(Pose.STANDING).makeBoundingBox(new Vec3(point.x(), point.y(), point.z()));
		return level.getWorldBorder().isWithinBounds(box) && level.noCollision(player, box)
				&& level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
				&& level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
	}

	static void teleport(ServerPlayer player, ServerLevel level, Arena.Position point) throws IOException {
		if (!player.teleportTo(level, point.x(), point.y(), point.z(), Set.of(), point.yaw(), point.pitch(), true))
			throw new IOException("Телепортація не вдалася");
		player.fallDistance = 0; player.setDeltaMovement(Vec3.ZERO); player.syncVelocity = true;
	}

	static void teleportToLobby(ServerPlayer player, ServerLevel level, Arena.Position point) {
		if (level == null) return;
		try { teleport(player, level, point); }
		catch (IOException ignored) { }
	}

	static ServerLevel dimension(MinecraftServer server, String name) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(name)));
	}
}