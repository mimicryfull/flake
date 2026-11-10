package dev.flake.duel;

import dev.flake.ExampleMod;
import dev.flake.arena.Arena;
import dev.flake.arena.ArenaRepository;
import dev.flake.build.Loadout;
import dev.flake.build.LoadoutCommands;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * One independent duel match bound to exactly one lobby room. Every instance owns its own arena,
 * participants, round timer and snapshot journal hand-off; several instances can run at once.
 * No player is modified before both write-ahead snapshots exist.
 */
public final class MatchRuntime {
	static final int COUNTDOWN_TICKS = 60;

	@FunctionalInterface
	public interface ArenaSource { Arena get() throws IOException; }

	private final MinecraftServer server;
	private final UUID roomId;
	private final DuelLobby lobby;
	private final ArenaRepository arenas;
	private final Supplier<String> arenaName;
	private DuelMatch match;
	private List<UUID> participants = List.of();
	private Arena arena;
	private int startsAt;
	private int roundStartedAt;
	private int lastRoundTicks;
	private boolean forceStart;
	private String notice = "Підтвердьте готовність для запуску дуелі.";

	public MatchRuntime(MinecraftServer server, UUID roomId, DuelLobby lobby, ArenaRepository arenas, Supplier<String> arenaName) {
		this.server = server;
		this.roomId = roomId;
		this.lobby = lobby;
		this.arenas = arenas;
		this.arenaName = arenaName;
	}

	public UUID roomId() { return roomId; }
	public DuelMatch match() { return match; }
	public List<UUID> participants() { return participants; }
	public String notice() { return notice; }
	public boolean busy() { return match != null; }
	public String phase() { return match == null ? "PREPARING" : match.state().name(); }

	public boolean inCountdown(UUID id) {
		return match != null && match.state() == DuelMatch.State.COUNTDOWN && participants.contains(id);
	}

	public boolean owns(UUID id) { return participants.contains(id); }

	/** Owner-initiated start: skips "everyone ready" but keeps every safety check. */
	public String requestStart() {
		if (match != null) return dev.flake.shared.FlakeI18n.tr("flake.lobby.err.busy");
		if (lobby.players().size() < 2) return dev.flake.shared.FlakeI18n.tr("flake.lobby.err.need_two");
		forceStart = true;
		return "";
	}

	public void cancelForceStart() { forceStart = false; }

	public void tick() {
		if (match != null && (match.state() == DuelMatch.State.CANCELLED || match.state() == DuelMatch.State.RESTORING)) {
			if (participants.stream().noneMatch(id -> player(id) != null && DuelRecovery.isProtected(id))) finishReturn();
			return;
		}
		if (match == null) {
			if (lobby.players().size() >= 2 && (lobby.allReady() || forceStart)) beginCountdown();
			return;
		}
		if (match.state() == DuelMatch.State.COUNTDOWN) {
			boolean rosterChanged = !lobby.players().keySet().equals(match.loadouts().keySet());
			boolean invalid = participants.stream().anyMatch(id -> player(id) == null || !player(id).isAlive()
					|| !LoadoutCommands.loadout(id).equals(match.loadouts().get(id)));
			if (rosterChanged || invalid) {
				stop("Відлік скасовано: склад, гравці або білди змінилися.");
				return;
			}
			if (server.getTickCount() >= startsAt) launch();
			else if ((startsAt - server.getTickCount()) % 20 == 0) tell(status(), true);
			return;
		}
		if (match.state() == DuelMatch.State.FIGHTING) {
			int now = server.getTickCount();
			for (var id : participants) {
				var player = player(id);
				if (player == null || !player.isAlive() || !player.level().dimension().identifier().toString().equals(arena.dimension())
						|| player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE
						|| !arena.bounds().contains(new Arena.Position(player.getX(), player.getY(), player.getZ(), 0, 0))) {
					stop("Дуель скасовано: учасник залишив бойову зону або змінив стан.");
					return;
				}
				// Вимикаємо ванільний реген від ситості/їжі.
				player.getFoodData().setFoodLevel(14);
				player.getFoodData().setSaturation(0.0f);
				var passive = dev.flake.shared.SliceRegistry.passive(passiveOf(id));
				if (passive != null) passive.handler().onFightTick(player, now);
			}
		}
	}

	public String status() {
		if (match != null && match.state() == DuelMatch.State.COUNTDOWN)
			return "Старт через " + Math.max(1, (startsAt - server.getTickCount() + 19) / 20) + " с · білди фіксуються на старті";
		if (match != null && match.state() == DuelMatch.State.FIGHTING) return "Бій · вийти або /flake duel stop — завершити";
		return notice;
	}

	public int timerTicks() {
		if (match == null) return 0;
		int now = server.getTickCount();
		return switch (match.state()) {
			case COUNTDOWN -> Math.max(0, startsAt - now);
			case FIGHTING -> roundStartedAt > 0 ? Math.max(0, now - roundStartedAt) : 0;
			case RESTORING, FINISHED, CANCELLED -> lastRoundTicks;
		};
	}

	public int timerTotalTicks() {
		return match != null && match.state() == DuelMatch.State.COUNTDOWN ? COUNTDOWN_TICKS : 0;
	}

	/** The passive item an actively fighting player entered the round with. */
	public Loadout.PassiveItem passiveOf(UUID id) {
		if (match == null) return Loadout.PassiveItem.NONE;
		var build = match.loadouts().get(id);
		return build == null ? Loadout.PassiveItem.NONE : build.passiveItem();
	}

	public void finishRound(UUID loser) {
		if (match == null || match.state() != DuelMatch.State.FIGHTING) return;
		UUID winner = participants.stream().filter(id -> !id.equals(loser)).findFirst().orElse(null);
		if (winner == null) { stop("Раунд завершено."); return; }
		var player = player(winner);
		match.finishRound(winner.equals(participants.getFirst()) ? DuelMatch.Result.FIRST : DuelMatch.Result.SECOND);
		forceStart = false;
		returnPlayers("Раунд завершено. Переможець: " + (player == null ? winner : player.getName().getString()) + ".");
	}

	public void stop(String reason) {
		if (match == null) return;
		forceStart = false;
		if (match.state() != DuelMatch.State.CANCELLED && match.state() != DuelMatch.State.RESTORING) match.cancel();
		returnPlayers(reason);
	}

	private void beginCountdown() {
		participants = lobby.players().keySet().stream().sorted().toList();
		try {
			arena = arenas.load(arenaName.get()).orElseThrow(() -> new IOException("Арену не налаштовано: " + arenaName.get()));
			preflight();
			match = new DuelMatch(participants.get(0), LoadoutCommands.loadout(participants.get(0)),
					participants.get(1), LoadoutCommands.loadout(participants.get(1)), 1);
			startsAt = server.getTickCount() + COUNTDOWN_TICKS;
			roundStartedAt = 0;
			lastRoundTicks = 0;
			forceStart = false;
			tell("Початок через 3 с. Скасувати готовність або змінити білд — зупинити відлік.", false);
		} catch (Exception exception) {
			notice = "Старт неможливий: " + exception.getMessage();
			tell(notice, false);
			lobby.resetReady();
			participants = List.of();
			forceStart = false;
		}
	}

	private void preflight() throws IOException {
		if (participants.size() != 2) throw new IOException("Потрібно двоє гравців");
		ServerLevel level = DuelRecovery.level(server, arena.dimension());
		for (int index = 0; index < 2; index++) {
			var id = participants.get(index);
			var player = player(id);
			if (player == null || !player.isAlive() || player.isSpectator() || player.isSleeping()
					|| player.isPassenger() || player.isVehicle()) throw new IOException("Обидва гравці мають бути живі, без транспорту й сну");
			if (DuelRecovery.journal(server).exists(id) || DuelRecovery.isProtected(id)) throw new IOException("Спочатку завершіть повернення особистих речей");
			if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty())
				throw new IOException("Закрийте контейнери й звільніть курсор інвентарю");
			for (int slot = 1; slot <= 4; slot++) if (!player.inventoryMenu.getSlot(slot).getItem().isEmpty())
				throw new IOException("Приберіть речі з сітки крафту");
			var build = LoadoutCommands.loadout(id);
			if (!build.errors().isEmpty()) throw new IOException("Білд недопустимий");
			if (!DuelRecovery.safe(level, index == 0 ? arena.firstSpawn() : arena.secondSpawn(), player)) throw new IOException("Бойова точка " + (index + 1) + " небезпечна або заблокована");
			if (!DuelRecovery.safe(level, arena.lobby(), player)) throw new IOException("Точка повернення арени небезпечна");
		}
		var first = arena.firstSpawn(); var second = arena.secondSpawn();
		if (new Vec3(first.x(), first.y(), first.z()).distanceToSqr(new Vec3(second.x(), second.y(), second.z())) < 4)
			throw new IOException("Бойові точки мають бути хоча б за 2 блоки одна від одної");
	}

	private void launch() {
		try {
			if (!arena.equals(arenas.load(arenaName.get()).orElseThrow())) throw new IOException("Конфігурація арени змінилася під час відліку");
			preflight();
			DuelPreparation.run(participants, id -> {
				var player = player(id);
				DuelRecovery.journal(server).create(id, DuelPlayerState.capture(player, arena));
				DuelRecovery.protect(id);
			}, id -> {
				lobby.lock();
				var player = player(id);
				prepareKit(player);
				DuelRecovery.teleport(player, DuelRecovery.level(server, arena.dimension()),
						id.equals(participants.getFirst()) ? arena.firstSpawn() : arena.secondSpawn());
			}, id -> DuelRecovery.recover(server, player(id)));
			match.startRound();
			roundStartedAt = server.getTickCount();
			lastRoundTicks = 0;
			tell("Бій! Тестовий раунд — без зміни блоків і доступу до контейнерів.", false);
		} catch (Exception exception) {
			ExampleMod.LOGGER.error("Duel start aborted; restoring personal state", exception);
			stop("Старт скасовано: " + exception.getMessage());
		}
	}

	private void prepareKit(ServerPlayer player) {
		player.stopUsingItem();
		player.closeContainer();
		player.getInventory().clearContent();
		player.removeAllEffects();
		PvpVitals.apply(player);
		player.setGameMode(GameType.ADVENTURE);
		var food = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess());
		new net.minecraft.world.food.FoodData().addAdditionalSaveData(food);
		player.getFoodData().readAdditionalSaveData(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), food.buildResult()));
		player.getFoodData().setFoodLevel(14);
		player.getFoodData().setSaturation(0.0f);
		player.experienceLevel = 0; player.totalExperience = 0; player.experienceProgress = 0;
		player.setAbsorptionAmount(0);
		player.setRemainingFireTicks(0); player.setTicksFrozen(0); player.setAirSupply(player.getMaxAirSupply());
		var build = match.loadouts().get(player.getUUID());
		player.setItemSlot(EquipmentSlot.HEAD, armorPieceStack(build.helmet(), Loadout.ArmorSlot.HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, armorPieceStack(build.chestplate(), Loadout.ArmorSlot.CHESTPLATE));
		player.setItemSlot(EquipmentSlot.LEGS, armorPieceStack(build.leggings(), Loadout.ArmorSlot.LEGGINGS));
		player.setItemSlot(EquipmentSlot.FEET, armorPieceStack(build.boots(), Loadout.ArmorSlot.BOOTS));

		int nextHotbarSlot = 0;
		dev.flake.shared.SliceRegistry.resetPlayer(player);
		PvpVitals.apply(player);

		var weaponSpec = dev.flake.shared.SliceRegistry.weapon(build.weapon());
		player.getInventory().setItem(nextHotbarSlot++, weaponSpec.createStack());

		for (var item : List.of(build.activeItem1(), build.activeItem2())) {
			var activeSpec = dev.flake.shared.SliceRegistry.active(item);
			if (activeSpec == null || item == Loadout.ActiveItem.NONE) continue;
			if (activeSpec.offhand()) {
				player.setItemSlot(EquipmentSlot.OFFHAND, DuelEquipment.temporary(activeSpec.createStack()));
			} else {
				player.getInventory().setItem(nextHotbarSlot++, activeSpec.createStack());
			}
		}

		var passiveSpec = dev.flake.shared.SliceRegistry.passive(build.passiveItem());
		if (passiveSpec != null) passiveSpec.handler().onApplyKit(player);

		if (weaponSpec.arrowCount() > 0) {
			player.getInventory().setItem(nextHotbarSlot++, new ItemStack(Items.ARROW, weaponSpec.arrowCount()));
		}

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			var item = player.getInventory().getItem(slot);
			if (!item.isEmpty()) DuelEquipment.temporary(item);
		}
		player.getInventory().setSelectedSlot(0);
		player.setHealth(player.getMaxHealth());
		player.inventoryMenu.broadcastFullState();
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0, 0, 0));
	}

	private static ItemStack armorPieceStack(Loadout.ArmorMaterial mat, Loadout.ArmorSlot slot) {
		var item = switch (mat) {
			case NONE -> null;
			case CHAINMAIL -> switch (slot) {
				case HELMET -> Items.CHAINMAIL_HELMET;
				case CHESTPLATE -> Items.CHAINMAIL_CHESTPLATE;
				case LEGGINGS -> Items.CHAINMAIL_LEGGINGS;
				case BOOTS -> Items.CHAINMAIL_BOOTS;
			};
			case GOLD -> switch (slot) {
				case HELMET -> Items.GOLDEN_HELMET;
				case CHESTPLATE -> Items.GOLDEN_CHESTPLATE;
				case LEGGINGS -> Items.GOLDEN_LEGGINGS;
				case BOOTS -> Items.GOLDEN_BOOTS;
			};
			case IRON -> switch (slot) {
				case HELMET -> Items.IRON_HELMET;
				case CHESTPLATE -> Items.IRON_CHESTPLATE;
				case LEGGINGS -> Items.IRON_LEGGINGS;
				case BOOTS -> Items.IRON_BOOTS;
			};
			case DIAMOND -> switch (slot) {
				case HELMET -> Items.DIAMOND_HELMET;
				case CHESTPLATE -> Items.DIAMOND_CHESTPLATE;
				case LEGGINGS -> Items.DIAMOND_LEGGINGS;
				case BOOTS -> Items.DIAMOND_BOOTS;
			};
		};
		return item == null ? ItemStack.EMPTY : DuelEquipment.temporary(new ItemStack(item));
	}

	private void returnPlayers(String reason) {
		if (roundStartedAt > 0) lastRoundTicks = Math.max(0, server.getTickCount() - roundStartedAt);
		notice = reason + " Повертаємо особисті речі; загиблому потрібно відродитися.";
		tell(notice, false);
		for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
			if (entity instanceof AbstractArrow arrow && DuelEquipment.isTemporary(arrow.getPickupItemStackOrigin())) arrow.discard();
			if (entity instanceof ItemEntity item && DuelEquipment.isTemporary(item.getItem())) item.discard();
		}
		for (var id : participants) {
			var player = player(id);
			if (player != null) { ShockwaveVisuals.cancel(player); dev.flake.shared.SliceRegistry.resetPlayer(player); DuelRecovery.recover(server, player); }
		}
		if (participants.stream().noneMatch(id -> player(id) != null && DuelRecovery.isProtected(id))) finishReturn();
	}

	private void finishReturn() {
		if (match != null && match.state() == DuelMatch.State.RESTORING) match.restorationComplete();
		lobby.unlock();
		lobby.resetReady();
		match = null;
		participants = List.of();
		forceStart = false;
		notice = "Речі повернено. Підтвердьте готовність для нового раунду.";
	}

	private ServerPlayer player(UUID id) { return server.getPlayerList().getPlayer(id); }

	private void tell(String text, boolean overlay) {
		for (var id : participants) {
			var player = player(id);
			if (player == null) continue;
			if (overlay && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.flake.ui.HudStatePayload.TYPE)) continue;
			player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.AQUA), overlay);
		}
	}

	/** True when the player is a live participant of a fight in this match. */
	public boolean fighting(UUID id) {
		return match != null && match.state() == DuelMatch.State.FIGHTING && participants.contains(id);
	}

	public boolean opponents(UUID first, UUID second) {
		return !first.equals(second) && fighting(first) && fighting(second);
	}
}