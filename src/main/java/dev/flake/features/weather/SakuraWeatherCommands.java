package dev.flake.features.weather;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/** {@code /flake weather <sakura [intensity] | off | clear | status>} */
public final class SakuraWeatherCommands {
	private SakuraWeatherCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			var weather = Commands.literal("weather")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.executes(ctx -> status(ctx.getSource()))
					.then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
					.then(Commands.literal("sakura")
							.executes(ctx -> setSakura(ctx.getSource(), SakuraWeather.DEFAULT_INTENSITY))
							.then(Commands.argument("intensity", FloatArgumentType.floatArg(SakuraWeather.MIN_INTENSITY, SakuraWeather.MAX_INTENSITY))
									.executes(ctx -> setSakura(ctx.getSource(), FloatArgumentType.getFloat(ctx, "intensity")))))
					.then(Commands.literal("off").executes(ctx -> clear(ctx.getSource())))
					.then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())));
			dispatcher.register(Commands.literal("flake").then(weather));
		});
	}

	private static int setSakura(CommandSourceStack source, float intensity) {
		SakuraWeather.set(source.getServer(), SakuraWeather.Mode.SAKURA, intensity);
		String pct = Math.round(SakuraWeather.clampIntensity(intensity) * 100.0f) + "%";
		source.sendSuccess(() -> Component.translatable("flake.weather.sakura.enabled", pct), true);
		return 1;
	}

	private static int clear(CommandSourceStack source) {
		SakuraWeather.set(source.getServer(), SakuraWeather.Mode.NONE, SakuraWeather.DEFAULT_INTENSITY);
		source.sendSuccess(() -> Component.translatable("flake.weather.cleared"), true);
		return 1;
	}

	private static int status(CommandSourceStack source) {
		var state = SakuraWeather.current(source.getServer());
		if (state.mode() == SakuraWeather.Mode.SAKURA) {
			String pct = Math.round(state.intensity() * 100.0f) + "%";
			source.sendSuccess(() -> Component.translatable("flake.weather.status.sakura", pct), false);
		} else {
			source.sendSuccess(() -> Component.translatable("flake.weather.status.off"), false);
		}
		return 1;
	}
}
