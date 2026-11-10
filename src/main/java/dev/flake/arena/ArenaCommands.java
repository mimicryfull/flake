package dev.flake.arena;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

/**
 * Arena authoring: {@code /flake arena [point] [name]}. Drafts are per server and per arena name,
 * so several arenas can be prepared without overwriting each other.
 */
public final class ArenaCommands {
	private static final String ARG = "arena";
	private ArenaCommands() {}

	public static void register() {
		Map<MinecraftServer, Map<String, ArenaDraft>> drafts = new IdentityHashMap<>();
		ServerLifecycleEvents.SERVER_STOPPED.register(drafts::remove);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			var arena = Commands.literal("arena")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
					.then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource(), drafts))
							.then(arenaArg(name -> ctx -> reset(ctx.getSource(), drafts, name))))
					.then(Commands.literal("corner1").executes(ctx -> corner(ctx.getSource(), drafts, true))
							.then(arenaArg(name -> ctx -> corner(ctx.getSource(), drafts, true, name))))
					.then(Commands.literal("corner2").executes(ctx -> corner(ctx.getSource(), drafts, false))
							.then(arenaArg(name -> ctx -> corner(ctx.getSource(), drafts, false, name))))
					.then(Commands.literal("spawn1").executes(ctx -> position(ctx.getSource(), drafts, ArenaDraft.Point.FIRST))
							.then(arenaArg(name -> ctx -> position(ctx.getSource(), drafts, ArenaDraft.Point.FIRST, name))))
					.then(Commands.literal("spawn2").executes(ctx -> position(ctx.getSource(), drafts, ArenaDraft.Point.SECOND))
							.then(arenaArg(name -> ctx -> position(ctx.getSource(), drafts, ArenaDraft.Point.SECOND, name))))
					.then(Commands.literal("lobby").executes(ctx -> position(ctx.getSource(), drafts, ArenaDraft.Point.LOBBY))
							.then(arenaArg(name -> ctx -> position(ctx.getSource(), drafts, ArenaDraft.Point.LOBBY, name))))
					.then(Commands.literal("save").executes(ctx -> save(ctx.getSource(), drafts, ArenaRepository.DEFAULT))
							.then(arenaArg(name -> ctx -> save(ctx.getSource(), drafts, name))))
					.then(Commands.literal("status").executes(ctx -> status(ctx.getSource(), ArenaRepository.DEFAULT))
							.then(arenaArg(name -> ctx -> status(ctx.getSource(), name))))
					.then(Commands.literal("delete")
							.then(arenaArg(name -> ctx -> delete(ctx.getSource(), name))));
			dispatcher.register(Commands.literal("flake").then(arena));
		});
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> arenaArg(
			java.util.function.Function<String, com.mojang.brigadier.Command<CommandSourceStack>> body) {
		return Commands.argument(ARG, StringArgumentType.word()).suggests((ctx, builder) -> {
			for (var name : repository(ctx.getSource()).names()) builder.suggest(name);
			return builder.buildFuture();
		}).executes(ctx -> body.apply(StringArgumentType.getString(ctx, ARG)).run(ctx));
	}

	private static ArenaRepository repository(CommandSourceStack source) {
		return ArenaRepository.forServer(source.getServer());
	}

	private static ArenaDraft draft(CommandSourceStack source, Map<MinecraftServer, Map<String, ArenaDraft>> drafts, String name) {
		return drafts.computeIfAbsent(source.getServer(), ignored -> new LinkedHashMap<>())
				.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), ignored -> new ArenaDraft());
	}

	private static String resolve(String name) {
		String lowered = name == null ? ArenaRepository.DEFAULT : name.toLowerCase(java.util.Locale.ROOT);
		if (!ArenaRepository.validName(lowered)) throw new IllegalArgumentException("Некоректна назва арени: лише a-z, 0-9, _ та -");
		return lowered;
	}

	private static int corner(CommandSourceStack source, Map<MinecraftServer, Map<String, ArenaDraft>> drafts, boolean first)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return corner(source, drafts, first, ArenaRepository.DEFAULT);
	}

	private static int corner(CommandSourceStack source, Map<MinecraftServer, Map<String, ArenaDraft>> drafts, boolean first, String name)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var block = player.blockPosition();
		try {
			String arena = resolve(name);
			draft(source, drafts, arena).setCorner(player.level().dimension().identifier().toString(),
					new ArenaDraft.BlockPosition(block.getX(), block.getY(), block.getZ()), first);
			return message(source, "Арена «" + arena + "» · кут " + (first ? "1" : "2") + ": "
					+ block.getX() + ", " + block.getY() + ", " + block.getZ());
		} catch (IllegalArgumentException exception) {
			return failure(source, exception.getMessage());
		}
	}

	private static int position(CommandSourceStack source, Map<MinecraftServer, Map<String, ArenaDraft>> drafts, ArenaDraft.Point point)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return position(source, drafts, point, ArenaRepository.DEFAULT);
	}

	private static int position(CommandSourceStack source, Map<MinecraftServer, Map<String, ArenaDraft>> drafts, ArenaDraft.Point point, String name)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var location = player.position();
		try {
			String arena = resolve(name);
			draft(source, drafts, arena).setPosition(player.level().dimension().identifier().toString(),
					new Arena.Position(location.x, location.y, location.z, player.getYRot(), player.getXRot()), point);
			return message(source, "Арена «" + arena + "» · точку " + point + " встановлено.");
		} catch (IllegalArgumentException exception) {
			return failure(source, exception.getMessage());
		}
	}

	private static int reset(CommandSourceStack source, Map<MinecraftServer, Map<String, ArenaDraft>> drafts) {
		return reset(source, drafts, ArenaRepository.DEFAULT);
	}

	private static int reset(CommandSourceStack source, Map<MinecraftServer, Map<String, ArenaDraft>> drafts, String name) {
		try {
			String arena = resolve(name);
			var serverDrafts = drafts.get(source.getServer());
			if (serverDrafts == null || serverDrafts.remove(arena) == null) return message(source, "Чернетки «" + arena + "» не було.");
			return message(source, "Чернетку «" + arena + "» очищено; збережену арену не змінено.");
		} catch (IllegalArgumentException exception) {
			return failure(source, exception.getMessage());
		}
	}

	private static int save(CommandSourceStack source, Map<MinecraftServer, Map<String, ArenaDraft>> drafts, String name) {
		try {
			String arena = resolve(name);
			repository(source).save(arena, draft(source, drafts, arena).build());
			return message(source, "Арену «" + arena + "» збережено."
					+ (ArenaRepository.DEFAULT.equals(arena) ? " Попередню конфігурацію замінено." : ""));
		} catch (IllegalStateException | IllegalArgumentException | IOException exception) {
			return failure(source, "Не вдалося зберегти арену: " + exception.getMessage());
		}
	}

	private static int status(CommandSourceStack source, String name) {
		try {
			String arena = resolve(name);
			var loaded = repository(source).load(arena);
			return message(source, loaded.map(value -> "Арена «" + arena + "»: " + value)
					.orElse("Арена «" + arena + "» не налаштована."));
		} catch (IllegalArgumentException | IOException exception) {
			return failure(source, "Не вдалося прочитати арену: " + exception.getMessage());
		}
	}

	private static int list(CommandSourceStack source) {
		try {
			var names = repository(source).names();
			if (names.isEmpty()) return message(source, "Збережених арен немає. Створи: /flake arena corner1 … /flake arena save <назва>.");
			var ready = new java.util.ArrayList<String>();
			for (var name : names) if (repository(source).load(name).isPresent()) ready.add(name);
			return message(source, "Арени: " + String.join(", ", ready));
		} catch (IOException exception) {
			return failure(source, "Не вдалося прочитати список арен: " + exception.getMessage());
		}
	}

	private static int delete(CommandSourceStack source, String name) {
		try {
			String arena = resolve(name);
			if (ArenaRepository.DEFAULT.equals(arena)) return failure(source, "Стандартну арену видалити не можна.");
			return repository(source).delete(arena)
					? message(source, "Арену «" + arena + "» видалено.")
					: failure(source, "Арени «" + arena + "» не існує.");
		} catch (IllegalArgumentException | IOException exception) {
			return failure(source, exception.getMessage());
		}
	}

	private static int message(CommandSourceStack source, String text) {
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int failure(CommandSourceStack source, String text) {
		source.sendFailure(Component.literal(text));
		return 0;
	}
}