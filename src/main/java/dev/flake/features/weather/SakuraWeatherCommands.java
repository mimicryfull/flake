package dev.flake.features.weather;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;

/**
 * Region-scoped sakura weather authoring:
 * {@code /flake weather sphere <name> <radius> [intensity]}
 * {@code /flake weather box <name> <x1> <y1> <z1> <x2> <y2> <z2> [intensity]}
 * {@code /flake weather pos1 [name]} / {@code pos2 [name]}
 * {@code /flake weather intensity <name> <intensity>}
 * {@code /flake weather remove <name>} / {@code clear} / {@code list}
 */
public final class SakuraWeatherCommands {
	private static final class BoxDraft {
		String dimension;
		BlockPos corner1;
		BlockPos corner2;
	}

	private SakuraWeatherCommands() {}

	public static void register() {
		Map<MinecraftServer, Map<String, BoxDraft>> drafts = new IdentityHashMap<>();
		ServerLifecycleEvents.SERVER_STOPPED.register(drafts::remove);

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			var weather = Commands.literal("weather")
					.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.executes(ctx -> list(ctx.getSource()))
					.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
					.then(Commands.literal("status").executes(ctx -> list(ctx.getSource())))
					.then(Commands.literal("sphere")
							.then(regionNameArg(true)
									.then(Commands.argument("radius", FloatArgumentType.floatArg(SakuraWeather.MIN_RADIUS, SakuraWeather.MAX_RADIUS))
											.executes(ctx -> setSphere(
													ctx.getSource(),
													StringArgumentType.getString(ctx, "name"),
													FloatArgumentType.getFloat(ctx, "radius"),
													SakuraWeather.DEFAULT_INTENSITY))
											.then(Commands.argument("intensity", FloatArgumentType.floatArg(SakuraWeather.MIN_INTENSITY, SakuraWeather.MAX_INTENSITY))
													.executes(ctx -> setSphere(
															ctx.getSource(),
															StringArgumentType.getString(ctx, "name"),
															FloatArgumentType.getFloat(ctx, "radius"),
															FloatArgumentType.getFloat(ctx, "intensity")))))))
					.then(Commands.literal("box")
							.then(regionNameArg(true)
									.then(Commands.argument("x1", DoubleArgumentType.doubleArg())
											.then(Commands.argument("y1", DoubleArgumentType.doubleArg())
													.then(Commands.argument("z1", DoubleArgumentType.doubleArg())
															.then(Commands.argument("x2", DoubleArgumentType.doubleArg())
																	.then(Commands.argument("y2", DoubleArgumentType.doubleArg())
																			.then(Commands.argument("z2", DoubleArgumentType.doubleArg())
																					.executes(ctx -> setBox(
																							ctx.getSource(),
																							StringArgumentType.getString(ctx, "name"),
																							DoubleArgumentType.getDouble(ctx, "x1"),
																							DoubleArgumentType.getDouble(ctx, "y1"),
																							DoubleArgumentType.getDouble(ctx, "z1"),
																							DoubleArgumentType.getDouble(ctx, "x2"),
																							DoubleArgumentType.getDouble(ctx, "y2"),
																							DoubleArgumentType.getDouble(ctx, "z2"),
																							SakuraWeather.DEFAULT_INTENSITY))
																					.then(Commands.argument("intensity", FloatArgumentType.floatArg(SakuraWeather.MIN_INTENSITY, SakuraWeather.MAX_INTENSITY))
																							.executes(ctx -> setBox(
																									ctx.getSource(),
																									StringArgumentType.getString(ctx, "name"),
																									DoubleArgumentType.getDouble(ctx, "x1"),
																									DoubleArgumentType.getDouble(ctx, "y1"),
																									DoubleArgumentType.getDouble(ctx, "z1"),
																									DoubleArgumentType.getDouble(ctx, "x2"),
																									DoubleArgumentType.getDouble(ctx, "y2"),
																									DoubleArgumentType.getDouble(ctx, "z2"),
																									FloatArgumentType.getFloat(ctx, "intensity"))))))))))))
					.then(Commands.literal("pos1")
							.executes(ctx -> setCorner(ctx.getSource(), drafts, SakuraWeather.DEFAULT_REGION_NAME, true))
							.then(regionNameArg(true)
									.executes(ctx -> setCorner(ctx.getSource(), drafts, StringArgumentType.getString(ctx, "name"), true))))
					.then(Commands.literal("pos2")
							.executes(ctx -> setCorner(ctx.getSource(), drafts, SakuraWeather.DEFAULT_REGION_NAME, false))
							.then(regionNameArg(true)
									.executes(ctx -> setCorner(ctx.getSource(), drafts, StringArgumentType.getString(ctx, "name"), false))))
					.then(Commands.literal("intensity")
							.then(regionNameArg(false)
									.then(Commands.argument("intensity", FloatArgumentType.floatArg(SakuraWeather.MIN_INTENSITY, SakuraWeather.MAX_INTENSITY))
											.executes(ctx -> setIntensity(
													ctx.getSource(),
													StringArgumentType.getString(ctx, "name"),
													FloatArgumentType.getFloat(ctx, "intensity"))))))
					.then(Commands.literal("remove")
							.then(regionNameArg(false)
									.executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
					.then(Commands.literal("delete")
							.then(regionNameArg(false)
									.executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
					.then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
					.then(Commands.literal("off").executes(ctx -> clear(ctx.getSource())));

			dispatcher.register(Commands.literal("flake").then(weather));
		});
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> regionNameArg(boolean includeDefaultSuggestion) {
		return Commands.argument("name", StringArgumentType.word()).suggests((ctx, builder) -> {
			if (includeDefaultSuggestion) builder.suggest(SakuraWeather.DEFAULT_REGION_NAME);
			for (SakuraWeather.Region r : SakuraWeather.regions(ctx.getSource().getServer())) {
				builder.suggest(r.name());
			}
			return builder.buildFuture();
		});
	}

	private static int setSphere(CommandSourceStack source, String name, float radius, float intensity) {
		Vec3 pos = source.getPosition();
		String dim = source.getLevel().dimension().identifier().toString();
		SakuraWeather.Region region = SakuraWeather.Region.sphere(
				name, dim, pos.x, pos.y, pos.z, radius, intensity);
		SakuraWeather.upsert(source.getServer(), region);
		String coords = formatVec(region.x1(), region.y1(), region.z1());
		String radStr = Integer.toString(Math.round(region.radius()));
		String pct = Math.round(region.intensity() * 100.0f) + "%";
		source.sendSuccess(() -> Component.translatable("flake.weather.region.sphere_set", region.name(), coords, radStr, pct), true);
		return 1;
	}

	private static int setBox(CommandSourceStack source, String name,
			double x1, double y1, double z1, double x2, double y2, double z2, float intensity) {
		String dim = source.getLevel().dimension().identifier().toString();
		SakuraWeather.Region region = SakuraWeather.Region.box(name, dim, x1, y1, z1, x2, y2, z2, intensity);
		SakuraWeather.upsert(source.getServer(), region);
		String minStr = formatVec(region.x1(), region.y1(), region.z1());
		String maxStr = formatVec(region.x2(), region.y2(), region.z2());
		String pct = Math.round(region.intensity() * 100.0f) + "%";
		source.sendSuccess(() -> Component.translatable("flake.weather.region.box_set", region.name(), minStr, maxStr, pct), true);
		return 1;
	}

	private static int setCorner(CommandSourceStack source, Map<MinecraftServer, Map<String, BoxDraft>> drafts,
			String rawName, boolean first) {
		String name = SakuraWeather.normalizeName(rawName);
		if (name.isEmpty()) name = SakuraWeather.DEFAULT_REGION_NAME;
		String dim = source.getLevel().dimension().identifier().toString();
		BlockPos bp = BlockPos.containing(source.getPosition());

		BoxDraft draft = drafts.computeIfAbsent(source.getServer(), ignored -> new LinkedHashMap<>())
				.computeIfAbsent(name, ignored -> new BoxDraft());
		if (draft.dimension != null && !draft.dimension.equals(dim)) {
			draft.corner1 = null;
			draft.corner2 = null;
		}
		draft.dimension = dim;
		if (first) draft.corner1 = bp;
		else draft.corner2 = bp;

		String finalName = name;
		String posStr = bp.getX() + " " + bp.getY() + " " + bp.getZ();
		if (draft.corner1 == null || draft.corner2 == null) {
			String key = first ? "flake.weather.region.pos1_set" : "flake.weather.region.pos2_set";
			source.sendSuccess(() -> Component.translatable(key, finalName, posStr), false);
			return 1;
		}

		SakuraWeather.Region existing = SakuraWeather.get(source.getServer(), finalName);
		float intensity = existing != null ? existing.intensity() : SakuraWeather.DEFAULT_INTENSITY;
		double minX = Math.min(draft.corner1.getX(), draft.corner2.getX());
		double minY = Math.min(draft.corner1.getY(), draft.corner2.getY());
		double minZ = Math.min(draft.corner1.getZ(), draft.corner2.getZ());
		double maxX = Math.max(draft.corner1.getX(), draft.corner2.getX()) + 1.0;
		double maxY = Math.max(draft.corner1.getY(), draft.corner2.getY()) + 1.0;
		double maxZ = Math.max(draft.corner1.getZ(), draft.corner2.getZ()) + 1.0;
		SakuraWeather.Region region = SakuraWeather.Region.box(finalName, dim, minX, minY, minZ, maxX, maxY, maxZ, intensity);
		SakuraWeather.upsert(source.getServer(), region);

		String minStr = formatVec(region.x1(), region.y1(), region.z1());
		String maxStr = formatVec(region.x2(), region.y2(), region.z2());
		String pct = Math.round(region.intensity() * 100.0f) + "%";
		source.sendSuccess(() -> Component.translatable("flake.weather.region.box_set", region.name(), minStr, maxStr, pct), true);
		return 1;
	}

	private static int setIntensity(CommandSourceStack source, String rawName, float intensity) {
		SakuraWeather.Region existing = SakuraWeather.get(source.getServer(), rawName);
		if (existing == null) {
			source.sendFailure(Component.translatable("flake.weather.region.missing", SakuraWeather.normalizeName(rawName)));
			return 0;
		}
		SakuraWeather.Region updated = existing.withIntensity(intensity);
		SakuraWeather.upsert(source.getServer(), updated);
		String pct = Math.round(updated.intensity() * 100.0f) + "%";
		source.sendSuccess(() -> Component.translatable("flake.weather.region.intensity_set", updated.name(), pct), true);
		return 1;
	}

	private static int remove(CommandSourceStack source, String rawName) {
		String name = SakuraWeather.normalizeName(rawName);
		if (!SakuraWeather.remove(source.getServer(), name)) {
			source.sendFailure(Component.translatable("flake.weather.region.missing", name));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("flake.weather.region.removed", name), true);
		return 1;
	}

	private static int clear(CommandSourceStack source) {
		SakuraWeather.clear(source.getServer());
		source.sendSuccess(() -> Component.translatable("flake.weather.cleared"), true);
		return 1;
	}

	private static int list(CommandSourceStack source) {
		var regions = SakuraWeather.regions(source.getServer());
		if (regions.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("flake.weather.status.off"), false);
			return 1;
		}
		source.sendSuccess(() -> Component.translatable("flake.weather.status.header", regions.size()), false);
		for (SakuraWeather.Region r : regions) {
			String pct = Math.round(r.intensity() * 100.0f) + "%";
			if (r.shape() == SakuraWeather.Shape.SPHERE) {
				source.sendSuccess(() -> Component.translatable(
						"flake.weather.status.entry_sphere",
						r.name(),
						formatVec(r.x1(), r.y1(), r.z1()),
						Integer.toString(Math.round(r.radius())),
						pct,
						r.dimension()), false);
			} else {
				source.sendSuccess(() -> Component.translatable(
						"flake.weather.status.entry_box",
						r.name(),
						formatVec(r.x1(), r.y1(), r.z1()),
						formatVec(r.x2(), r.y2(), r.z2()),
						pct,
						r.dimension()), false);
			}
		}
		return 1;
	}

	private static String formatVec(double x, double y, double z) {
		return Math.round(x) + " " + Math.round(y) + " " + Math.round(z);
	}
}
