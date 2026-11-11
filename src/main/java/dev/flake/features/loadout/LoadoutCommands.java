package dev.flake.features.loadout;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.flake.combat.ability.AbilitySlots;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.combat.ability.AbilityInputServer;
import dev.flake.combat.ability.ClassAbilityApplication;
import dev.flake.features.concussed.ConcussedAbilities;
import dev.flake.features.training.TrainingDummies;
import dev.flake.features.lobby.LobbyManager;
import dev.flake.features.lobby.LobbyRoom;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** Command presentation adapter. Build mutations and abilities belong to their feature services. */
public final class LoadoutCommands {
	private LoadoutCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			var root = Commands.literal("flake");
			root.then(Commands.literal("build").executes(ctx -> show(ctx.getSource())));
			root.then(Commands.literal("ability").executes(ctx -> activateSlot(ctx.getSource(), 1))
					.then(Commands.argument("slot", IntegerArgumentType.integer(1, AbilitySlots.COUNT))
							.executes(ctx -> activateSlot(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))));
			root.then(Commands.literal("quake").executes(ctx -> activateSlot(ctx.getSource(), 2)));
			root.then(Commands.literal("effects").executes(ctx -> {
				var player = ctx.getSource().getPlayerOrException();
				boolean enabled = dev.flake.render.vfx.CombatVisuals.toggle(player);
				dev.flake.bootstrap.FlakeNetworking.sync(ctx.getSource().getServer());
				ctx.getSource().sendSuccess(() -> Component.literal("Візуальний ефект хвилі " + (enabled ? "увімкнено" : "вимкнено") + "."), false);
				return 1;
			}));
			root.then(Commands.literal("dummy").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(Commands.literal("spawn").executes(ctx -> spawnDummy(ctx.getSource())))
					.then(Commands.literal("remove").executes(ctx -> removeDummies(ctx.getSource()))));
			var classes = Commands.literal("class");
			for (var value : Loadout.FighterClass.values()) classes.then(option(value.name(), source -> update(source, old -> old.withClass(value))));
			var armor = Commands.literal("armor");
			for (var mat : Loadout.ArmorMaterial.values()) {
				armor.then(option(mat.name(), source -> update(source, old -> new Loadout(old.fighterClass(), mat, mat, mat, mat,
						old.weapon(), old.passiveItem(), old.activeItem1(), old.activeItem2(), old.migrationConflicts()))));
			}
			var slots = Commands.literal("slot");
			for (var slot : Loadout.ArmorSlot.values()) {
				var slotBranch = Commands.literal(slot.name().toLowerCase(Locale.ROOT));
				for (var mat : Loadout.ArmorMaterial.values()) slotBranch.then(option(mat.name(), source -> update(source, old -> old.withArmorSlot(slot, mat))));
				slots.then(slotBranch);
			}
			var weapons = Commands.literal("weapon");
			for (var value : Loadout.Weapon.values()) weapons.then(option(value.name(), source -> update(source, old -> old.withWeapon(value))));
			var passives = Commands.literal("passive");
			for (var value : Loadout.PassiveItem.values()) passives.then(option(value.name(), source -> update(source, old -> old.withPassive(value))));
			var activeItems = Commands.literal("item");
			for (int index = 1; index <= Loadout.ACTIVE_ITEM_SLOTS; index++) {
				int slotIndex = index;
				var branch = Commands.literal(Integer.toString(index));
				for (var value : Loadout.ActiveItem.values()) branch.then(option(value.name(), source -> update(source, old -> old.withActiveItem(slotIndex, value))));
				activeItems.then(branch);
			}
			for (String legacy : List.of("accessory", "accslot", "utility")) {
				root.then(Commands.literal(legacy).then(Commands.argument("legacy", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
						.executes(ctx -> { ctx.getSource().sendFailure(Component.literal("Схема білда оновлена: /flake passive <предмет> або /flake item <1|2> <предмет>.")); return 0; })));
			}
			root.then(classes).then(armor).then(slots).then(weapons).then(passives).then(activeItems);
			root.then(Commands.literal("duel")
					.then(Commands.literal("stop").executes(ctx -> {
						boolean stopped = DuelRuntime.stopFor(ctx.getSource().getPlayerOrException());
						if (!stopped) ctx.getSource().sendFailure(Component.literal("Ти не береш участі в активній дуелі."));
						return stopped ? 1 : 0;
					}))
					.then(Commands.literal("recover").executes(ctx -> {
						boolean found = DuelRuntime.retryRecovery(ctx.getSource().getPlayerOrException());
						if (!found) ctx.getSource().sendFailure(Component.literal("Незавершеного повернення речей немає."));
						return found ? 1 : 0;
					}))
					.then(Commands.literal("join").executes(ctx -> join(ctx.getSource())))
					.then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
					.then(Commands.literal("ready").executes(ctx -> ready(ctx.getSource(), true)))
					.then(Commands.literal("not_ready").executes(ctx -> ready(ctx.getSource(), false)))
					.then(Commands.literal("status").executes(ctx -> status(ctx.getSource()))));
			dispatcher.register(root);
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> option(String name, SourceCommand command) {
		return Commands.literal(name.toLowerCase(Locale.ROOT)).executes(context -> command.run(context.getSource()));
	}

	@FunctionalInterface
	private interface SourceCommand {
		int run(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException;
	}

	private static int update(CommandSourceStack source, UnaryOperator<Loadout> change) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var result = LoadoutService.of(source.getServer()).update(source.getPlayerOrException(), change);
		if (result.status() == LoadoutService.Status.LOCKED) {
			source.sendFailure(Component.literal("Білд зафіксовано до завершення дуелі й повернення речей."));
			return 0;
		}
		if (result.status() == LoadoutService.Status.INVALID) {
			source.sendFailure(Component.literal(String.join(", ", result.errors())));
			return 0;
		}
		if (result.status() == LoadoutService.Status.SAVE_FAILED) {
			source.sendFailure(Component.literal("Не вдалося зберегти білд. Зміну скасовано, білд лишився попереднім."));
			dev.flake.bootstrap.FlakeNetworking.sync(source.getServer());
			return 0;
		}
		dev.flake.bootstrap.FlakeNetworking.sync(source.getServer());
		return show(source);
	}

	private static int join(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var manager = LobbyManager.of(source.getServer());
		if (manager == null) { source.sendFailure(Component.literal("Лобі ще не готове.")); return 0; }
		var room = manager.roomOf(player.getUUID());
		if (room == null) {
			var visible = manager.visibleRooms(player.getUUID()).stream().filter(r -> r.joinableBy(player.getUUID())).findFirst().orElse(null);
			if (visible == null) { source.sendFailure(Component.literal("Немає відкритих лобі. Створи своє через меню.")); return 0; }
			String error = manager.join(player, visible.id());
			if (!error.isEmpty()) { source.sendFailure(Component.literal(error)); return 0; }
			source.sendSuccess(() -> Component.literal("Ти приєднався до лобі «" + visible.title() + "»."), false);
			return 1;
		}
		source.sendSuccess(() -> Component.literal("Ти вже в лобі «" + room.title() + "»."), false);
		return 0;
	}

	private static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var manager = LobbyManager.of(source.getServer());
		if (manager == null) return 0;
		DuelRuntime.stopFor(player);
		String error = manager.leave(player);
		if (!error.isEmpty()) { source.sendFailure(Component.literal(error)); return 0; }
		source.sendSuccess(() -> Component.literal("Ти залишив лобі."), false);
		return 1;
	}

	private static int ready(CommandSourceStack source, boolean ready) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var id = player.getUUID();
		var lobbies = LobbyManager.of(source.getServer());
		var room = lobbies == null ? null : lobbies.roomOf(id);
		if (room == null) { source.sendFailure(Component.literal("Спочатку приєднайся до лобі.")); return 0; }
		String error = room.ready(id, ready, LoadoutService.of(source.getServer()).get(id).errors().isEmpty());
		if (!error.isEmpty()) { source.sendFailure(Component.literal(error)); return 0; }
		source.sendSuccess(() -> Component.literal(ready ? "Готовий." : "Більше не готовий."), false);
		return 1;
	}

	private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var lobbies = LobbyManager.of(source.getServer());
		var room = lobbies == null ? null : lobbies.roomOf(player.getUUID());
		if (room == null) { source.sendSuccess(() -> Component.literal("Ти не в лобі. Відкритих лобі: " + (lobbies == null ? 0 : lobbies.visibleRooms(player.getUUID()).size()) + "."), false); return 1; }
		String message = summary(source.getServer(), room);
		source.sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static String summary(MinecraftServer server, LobbyRoom room) {
		var names = room.lobby().players().entrySet().stream().map(entry -> {
			var player = server.getPlayerList().getPlayer(entry.getKey());
			String name = player == null ? entry.getKey().toString() : player.getName().getString();
			return name + (entry.getValue() ? ": готовий" : ": не готовий");
		}).sorted().toList();
		var match = room.match();
		String status = match == null ? room.status() : match.status();
		return "Лобі «" + room.title() + "» " + names.size() + "/" + room.maxPlayers() + ": "
				+ (names.isEmpty() ? "порожнє" : String.join(", ", names)) + ". " + status;
	}

	private static int show(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Loadout build = LoadoutService.of(source.getServer()).get(source.getPlayerOrException().getUUID());
		source.sendSuccess(() -> Component.literal("Білд: " + build.fighterClass() + ", броня: "
				+ List.of(build.helmet(), build.chestplate(), build.leggings(), build.boots())
				+ ", зброя: " + build.weapon() + ", пасив: " + build.passiveItem()
				+ ", активні предмети: " + build.activeItem1() + ", " + build.activeItem2()), false);
		return 1;
	}

	private static int activateSlot(CommandSourceStack source, int slot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return ClassAbilityApplication.activate(source.getPlayerOrException(), slot);
	}

	private static int spawnDummy(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var dummy = EntityTypes.PIG.create(player.level(), EntitySpawnReason.COMMAND);
		if (dummy == null) { source.sendFailure(Component.literal("Не вдалося створити манекен.")); return 0; }
		var direction = player.getLookAngle();
		var position = player.position();
		dummy.setPos(position.x + direction.x * 2, position.y, position.z + direction.z * 2);
		TrainingDummies.prepare(dummy);
		dummy.setPersistenceRequired();
		dummy.addTag(TrainingDummies.TAG);
		dummy.setCustomName(Component.literal("Тестовий манекен Flake"));
		dummy.setCustomNameVisible(true);
		if (!player.level().addFreshEntity(dummy)) { source.sendFailure(Component.literal("Не вдалося додати манекен у світ.")); return 0; }
		source.sendSuccess(() -> Component.literal("Манекен створено. Прибрати поруч: /flake dummy remove"), false);
		return 1;
	}

	private static int removeDummies(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var dummies = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), entity -> entity.entityTags().contains(TrainingDummies.TAG));
		dummies.forEach(Mob::discard);
		source.sendSuccess(() -> Component.literal("Прибрано манекенів поблизу: " + dummies.size()), false);
		return dummies.size();
	}
}
