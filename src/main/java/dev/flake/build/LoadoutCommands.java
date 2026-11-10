package dev.flake.build;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.flake.ability.AbilitySlots;
import dev.flake.ability.AbilitySlotPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import dev.flake.duel.Cooldowns;
import dev.flake.duel.CrystalClaw;
import dev.flake.duel.DuelRuntime;
import dev.flake.ui.LobbyManager;
import dev.flake.ui.LobbyRoom;
import dev.flake.duel.ElectricQuake;
import dev.flake.duel.Shockwave;
import dev.flake.duel.ShockwaveVisuals;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class LoadoutCommands {
	private static final String TEST_DUMMY_TAG = "flake_test_dummy";
	private static final LoadoutManager LOADOUTS = new LoadoutManager();
	private static final HashMap<UUID, Integer> COOLDOWNS = new HashMap<>();
	private static final HashMap<UUID, PendingShockwave> CHARGES = new HashMap<>();
	private static final HashMap<UUID, Integer> QUAKE_COOLDOWNS = new HashMap<>();
	private static final HashMap<UUID, Integer> QUAKE_TOTALS = new HashMap<>();
	private static final HashMap<UUID, Integer> SHOCKWAVE_TOTALS = new HashMap<>();
	private static final HashMap<UUID, ActiveQuake> QUAKES = new HashMap<>();
	private LoadoutCommands() {}

	public static Loadout loadout(UUID playerId) {
		return LOADOUTS.get(playerId);
	}

	/** Monotonic build revision for optimistic concurrency in the editor UI. */
	public static long revision(UUID playerId) {
		return LOADOUTS.revision(playerId);
	}

	/** Persistence truth (missing/loaded/corrupt) for honest editor status text. */
	public static LoadoutStore.State state(UUID playerId) {
		return LOADOUTS.state(playerId);
	}

	/** Atomic whole-build replacement used by the editor; rejects stale revisions. */
	public static LoadoutManager.ApplyResult apply(UUID playerId, long expectedRevision, Loadout desired) {
		return LOADOUTS.apply(playerId, expectedRevision, desired);
	}

	public static int cooldownRemainingTicks(UUID playerId, int nowTick) {
		Integer start = COOLDOWNS.get(playerId);
		if (start == null) return 0;
		return Math.max(0, Shockwave.COOLDOWN_TICKS - (nowTick - start));
	}

	public static int chargeElapsedTicks(UUID playerId, int nowTick) {
		PendingShockwave pending = CHARGES.get(playerId);
		if (pending == null) return -1;
		int start = pending.releaseTick() - Shockwave.CHARGE_TICKS;
		return Math.clamp(nowTick - start, 0, Shockwave.CHARGE_TICKS);
	}

	/** Cancel on the class-change event itself, even if the player switches back within one tick. */
	public static void onClassChanged(ServerPlayer player) {
		var pending = CHARGES.remove(player.getUUID());
		if (pending != null) ShockwaveVisuals.cancel(player, pending.sourceId());
		QUAKES.remove(player.getUUID());
		// Keep cooldowns: switching classes must not reset either ability's recharge.
	}

	public static int quakeCooldownRemainingTicks(UUID playerId, int nowTick) {
		Integer start = QUAKE_COOLDOWNS.get(playerId);
		if (start == null) return 0;
		return Math.max(0, ElectricQuake.COOLDOWN_TICKS - (nowTick - start));
	}

	public static int quakeActiveElapsedTicks(UUID playerId, int nowTick) {
		ActiveQuake active = QUAKES.get(playerId);
		if (active == null) return -1;
		return Math.clamp(nowTick - active.startTick(), 0, ElectricQuake.PROPAGATION_TICKS);
	}

	/** Actual Gravitational Dissonance cooldown length started by the last cast. */
	public static int shockwaveCooldownTotalTicks(UUID playerId) {
		return SHOCKWAVE_TOTALS.getOrDefault(playerId, Shockwave.COOLDOWN_TICKS);
	}

	/** Actual Electric Quake cooldown length started by the last cast. */
	public static int quakeCooldownTotalTicks(UUID playerId) {
		return QUAKE_TOTALS.getOrDefault(playerId, ElectricQuake.COOLDOWN_TICKS);
	}

	public static void register() {
		LoadoutManager manager = LOADOUTS;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> LOADOUTS.bind(
				server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("flake/builds")));
		DuelRuntime.install();
		LobbyManager.install();
		PayloadTypeRegistry.serverboundPlay().register(AbilitySlotPayload.TYPE, AbilitySlotPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AbilitySlotPayload.TYPE, (payload, context) ->
				context.server().execute(() -> {
					try { activateSlot(context.player().createCommandSourceStack(), payload.slot()); }
					catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) { /* Player-only source. */ }
				}));
		var cooldowns = COOLDOWNS;
		var charges = CHARGES;
		var quakeCooldowns = QUAKE_COOLDOWNS;
		var quakes = QUAKES;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			quakes.entrySet().removeIf(entry -> {
				var player = server.getPlayerList().getPlayer(entry.getKey());
				var active = entry.getValue();
				if (player == null || !player.isAlive() || player.isSpectator()
						|| DuelRuntime.blocksAbility(entry.getKey())
						|| active.inDuel() && !DuelRuntime.fighting(entry.getKey())
						|| player.level() != active.level()
						|| manager.get(entry.getKey()).fighterClass() != Loadout.FighterClass.CONCUSSED) {
					return true;
				}
				int step = now - active.startTick();
				if (step < 0) return false;
				if (step > ElectricQuake.PROPAGATION_TICKS) return true;
				stepElectricQuake(player, active, step);
				return step >= ElectricQuake.PROPAGATION_TICKS;
			});
			quakeCooldowns.entrySet().removeIf(entry -> {
				var player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null) return true;
				int remaining = ElectricQuake.COOLDOWN_TICKS - (now - entry.getValue());
				return remaining <= 0;
			});
			charges.entrySet().removeIf(entry -> {
				var player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null) return true;
				if (!player.isAlive() || player.isSpectator() || player.getId() != entry.getValue().sourceId()
						|| DuelRuntime.blocksAbility(entry.getKey())
						|| entry.getValue().inDuel() && !DuelRuntime.fighting(entry.getKey())
						|| player.level() != entry.getValue().level()
						|| manager.get(entry.getKey()).fighterClass() != Loadout.FighterClass.CONCUSSED) {
					ShockwaveVisuals.cancel(player, entry.getValue().sourceId());
					return true;
				}
				if (now < entry.getValue().releaseTick()) return false;
				detonate(player);
				return true;
			});
			cooldowns.entrySet().removeIf(entry -> {
				var player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null) return true;
				boolean hasHud = net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.flake.ui.HudStatePayload.TYPE);
				int remaining = Shockwave.COOLDOWN_TICKS - (now - entry.getValue());
				if (remaining <= 0) {
					if (!hasHud) player.sendSystemMessage(Component.literal("Гравітаційний дисонанс готовий").withStyle(net.minecraft.ChatFormatting.AQUA), true);
					return true;
				}
				if (!hasHud && now % 4 == 0) {
					if (charges.containsKey(entry.getKey())) {
						int elapsed = now - entry.getValue();
						int filled = Math.clamp(elapsed * 10 / Shockwave.CHARGE_TICKS, 0, 10);
						player.sendSystemMessage(Component.literal("Накопичення  " + "▰".repeat(filled) + "▱".repeat(10 - filled))
								.withStyle(net.minecraft.ChatFormatting.AQUA), true);
						return false;
					}
					int filled = Math.clamp((Shockwave.COOLDOWN_TICKS - remaining) * 10 / Shockwave.COOLDOWN_TICKS, 0, 10);
					String bar = "▰".repeat(filled) + "▱".repeat(10 - filled);
					String seconds = String.format(Locale.ROOT, "%.1f", remaining / 20.0);
					player.sendSystemMessage(Component.literal("Ударна хвиля  " + bar + "  " + seconds + " с").withStyle(net.minecraft.ChatFormatting.AQUA), true);
				}
				return false;
			});
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			manager.clear();
			cooldowns.clear();
			charges.clear();
			quakeCooldowns.clear();
			quakes.clear();
			SHOCKWAVE_TOTALS.clear();
			QUAKE_TOTALS.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			manager.remove(handler.player.getUUID());
			if (charges.remove(handler.player.getUUID()) != null) ShockwaveVisuals.cancel(handler.player);
			cooldowns.remove(handler.player.getUUID());
			quakeCooldowns.remove(handler.player.getUUID());
			quakes.remove(handler.player.getUUID());
			SHOCKWAVE_TOTALS.remove(handler.player.getUUID());
			QUAKE_TOTALS.remove(handler.player.getUUID());
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			var root = Commands.literal("flake");
			root.then(Commands.literal("build").executes(ctx -> show(ctx.getSource(), manager)));
			root.then(Commands.literal("ability").executes(ctx -> activateSlot(ctx.getSource(), 1))
					.then(Commands.argument("slot", IntegerArgumentType.integer(1, AbilitySlots.COUNT))
							.executes(ctx -> activateSlot(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))));
			// Compatibility alias uses the same server-side class/slot resolution.
			root.then(Commands.literal("quake").executes(ctx -> activateSlot(ctx.getSource(), 2)));
			root.then(Commands.literal("effects").executes(ctx -> {
				var player = ctx.getSource().getPlayerOrException();
				boolean enabled = ShockwaveVisuals.toggle(player);
				dev.flake.ui.FlakeUiServer.sync(ctx.getSource().getServer());
				ctx.getSource().sendSuccess(() -> Component.literal("Візуальний ефект хвилі " + (enabled ? "увімкнено" : "вимкнено") + "."), false);
				return 1;
			}));
			root.then(Commands.literal("dummy").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
					.then(Commands.literal("spawn").executes(ctx -> spawnDummy(ctx.getSource())))
					.then(Commands.literal("remove").executes(ctx -> removeDummies(ctx.getSource()))));
			var classes = Commands.literal("class");
			for (var value : Loadout.FighterClass.values()) {
				classes.then(option(value.name(), source -> update(source, manager, old -> old.withClass(value))));
			}
			var armor = Commands.literal("armor");
			for (var mat : Loadout.ArmorMaterial.values()) {
				armor.then(option(mat.name(), source -> update(source, manager, old ->
						new Loadout(old.fighterClass(), mat, mat, mat, mat,
								old.weapon(), old.passiveItem(), old.activeItem1(), old.activeItem2(), old.migrationConflicts()))));
			}
			var slots = Commands.literal("slot");
			for (var slot : Loadout.ArmorSlot.values()) {
				var slotBranch = Commands.literal(slot.name().toLowerCase(Locale.ROOT));
				for (var mat : Loadout.ArmorMaterial.values()) {
					slotBranch.then(option(mat.name(), source -> update(source, manager, old -> old.withArmorSlot(slot, mat))));
				}
				slots.then(slotBranch);
			}
			var weapons = Commands.literal("weapon");
			for (var value : Loadout.Weapon.values()) {
				weapons.then(option(value.name(), source -> update(source, manager, old -> old.withWeapon(value))));
			}
			var passives = Commands.literal("passive");
			for (var value : Loadout.PassiveItem.values()) {
				passives.then(option(value.name(), source -> update(source, manager, old -> old.withPassive(value))));
			}
			var activeItems = Commands.literal("item");
			for (int index = 1; index <= Loadout.ACTIVE_ITEM_SLOTS; index++) {
				int slotIndex = index;
				var branch = Commands.literal(Integer.toString(index));
				for (var value : Loadout.ActiveItem.values()) {
					branch.then(option(value.name(), source -> update(source, manager, old -> old.withActiveItem(slotIndex, value))));
				}
				activeItems.then(branch);
			}
			// Old mutation commands must not reinterpret a slot and overwrite unrelated content.
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
					.then(Commands.literal("ready").executes(ctx -> ready(ctx.getSource(), manager, true)))
					.then(Commands.literal("not_ready").executes(ctx -> ready(ctx.getSource(), manager, false)))
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

	private static int update(CommandSourceStack source, LoadoutManager manager, UnaryOperator<Loadout> change) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var id = source.getPlayerOrException().getUUID();
		if (DuelRuntime.locked(id)) {
			source.sendFailure(Component.literal("Білд зафіксовано до завершення дуелі й повернення речей."));
			return 0;
		}
		Loadout previous = manager.get(id);
		var result = manager.updateChecked(id, change);
		if (result.status() == LoadoutManager.ApplyStatus.INVALID) {
			source.sendFailure(Component.literal(String.join(", ", result.errors())));
			return 0;
		}
		if (result.status() == LoadoutManager.ApplyStatus.SAVE_FAILED) {
			// Memory and disk both still hold the previous build; say so instead of faking success.
			source.sendFailure(Component.literal("Не вдалося зберегти білд. Зміну скасовано, білд лишився попереднім."));
			dev.flake.ui.FlakeUiServer.sync(source.getServer());
			return 0;
		}
		Loadout candidate = result.build();
		if (!candidate.equals(previous)) {
			if (candidate.fighterClass() != previous.fighterClass()) onClassChanged(source.getPlayerOrException());
			var room = LobbyManager.of(source.getServer()) == null ? null : LobbyManager.of(source.getServer()).roomOf(id);
			if (room != null) room.buildChanged(id);
		}
		dev.flake.ui.FlakeUiServer.sync(source.getServer());
		return show(source, manager);
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

	private static int ready(CommandSourceStack source, LoadoutManager manager, boolean ready) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var id = player.getUUID();
		var lobbies = LobbyManager.of(source.getServer());
		var room = lobbies == null ? null : lobbies.roomOf(id);
		if (room == null) { source.sendFailure(Component.literal("Спочатку приєднайся до лобі.")); return 0; }
		String error = room.ready(id, ready, manager.get(id).errors().isEmpty());
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

	private static int show(CommandSourceStack source, LoadoutManager manager) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Loadout build = manager.get(source.getPlayerOrException().getUUID());
		source.sendSuccess(() -> Component.literal("Білд: " + build.fighterClass() + ", броня: "
				+ List.of(build.helmet(), build.chestplate(), build.leggings(), build.boots())
				+ ", зброя: " + build.weapon() + ", пасив: " + build.passiveItem()
				+ ", активні предмети: " + build.activeItem1() + ", " + build.activeItem2()), false);
		return 1;
	}

	private static int shockwave(CommandSourceStack source, LoadoutManager manager, HashMap<UUID, Integer> cooldowns,
			HashMap<UUID, PendingShockwave> charges) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var id = player.getUUID();
		if (DuelRuntime.blocksAbility(id)) {
			source.sendFailure(Component.literal("Дочекайся початку бою або завершення повернення речей."));
			return 0;
		}
		if (manager.get(id).fighterClass() != Loadout.FighterClass.CONCUSSED) {
			source.sendFailure(Component.literal("Здібність доступна класу Контужений."));
			return 0;
		}
		if (!player.isAlive() || player.isSpectator()) {
			source.sendFailure(Component.literal("Здібність доступна лише живому гравцю."));
			return 0;
		}
		int now = source.getServer().getTickCount();
		if (charges.containsKey(id)) return 0;
		Integer lastUse = cooldowns.get(id);
		if (lastUse != null && now - lastUse < Shockwave.COOLDOWN_TICKS) {
			return 0;
		}
		int cooldown = Cooldowns.ticks(player, Shockwave.COOLDOWN_TICKS);
		cooldowns.put(id, now - (Shockwave.COOLDOWN_TICKS - cooldown));
		SHOCKWAVE_TOTALS.put(id, cooldown);
		charges.put(id, new PendingShockwave(now + Shockwave.CHARGE_TICKS, player.level(), player.getId(), DuelRuntime.fighting(id)));
		if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.flake.ui.HudStatePayload.TYPE)) {
			player.sendSystemMessage(Component.literal("Накопичення  ▱▱▱▱▱▱▱▱▱▱").withStyle(net.minecraft.ChatFormatting.AQUA), true);
		}
		ShockwaveVisuals.charge(player);
		dev.flake.ui.FlakeUiServer.sync(source.getServer());
		return 1;
	}

	private static int activateSlot(CommandSourceStack source, int slot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		if (!AbilitySlots.valid(slot)) return 0;
		var player = source.getPlayerOrException();
		if (!player.isAlive() || player.isSpectator() || DuelRuntime.blocksAbility(player.getUUID())) return 0;
		return switch (AbilitySlots.resolve(LOADOUTS.get(player.getUUID()).fighterClass(), slot)) {
			case GRAVITATIONAL_DISSONANCE -> shockwave(source, LOADOUTS, COOLDOWNS, CHARGES);
			case ELECTRIC_QUAKE -> quake(source, LOADOUTS, QUAKE_COOLDOWNS, QUAKES);
			case UNIMPLEMENTED -> 0;
			case SHIELD_RAM, BULWARK, BLOOD_LUNGE, BLOODLUST, PROXIMITY_MINE, BLAST_JUMP ->
					dev.flake.ability.ClassSkills.cast(player, AbilitySlots.resolve(LOADOUTS.get(player.getUUID()).fighterClass(), slot), slot);
		};
	}

	private static void detonate(ServerPlayer player) {
		var id = player.getUUID();
		var origin = player.position();
		for (UUID targetId : DuelRuntime.participants(id)) {
			if (id.equals(targetId)) continue;
			var target = player.level().getServer().getPlayerList().getPlayer(targetId);
			if (target == null || !target.isAlive() || target.level() != player.level()) continue;
			var offset = target.position().subtract(origin);
			Shockwave.impulse(offset.x, offset.y, offset.z).ifPresent(impulse -> {
				target.push(impulse.x(), impulse.y(), impulse.z());
				target.connection.send(new ClientboundSetEntityMotionPacket(target));
				ShockwaveVisuals.impact(target, player);
			});
		}
		var box = new AABB(origin.x - Shockwave.RADIUS, origin.y - Shockwave.RADIUS, origin.z - Shockwave.RADIUS,
				origin.x + Shockwave.RADIUS, origin.y + Shockwave.RADIUS, origin.z + Shockwave.RADIUS);
		for (Mob mob : DuelRuntime.fighting(id) ? java.util.List.<Mob>of() : player.level().getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
			var offset = mob.position().subtract(origin);
			Shockwave.impulse(offset.x, offset.y, offset.z).ifPresent(impulse -> {
				if (mob.entityTags().contains(TEST_DUMMY_TAG) && mob.isNoAi()) prepareDummy(mob);
				mob.push(impulse.x(), impulse.y(), impulse.z());
				mob.syncVelocity = true;
			});
		}
		ShockwaveVisuals.play(player);
	}

	private record PendingShockwave(int releaseTick, ServerLevel level, int sourceId, boolean inDuel) {}

	private record ActiveQuake(
			int startTick,
			ServerLevel level,
			Vec3 origin,
			double dirX,
			double dirZ,
			boolean inDuel,
			Set<UUID> hitTargets
	) {}

	private static int quake(CommandSourceStack source, LoadoutManager manager, HashMap<UUID, Integer> quakeCooldowns,
			HashMap<UUID, ActiveQuake> quakes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var id = player.getUUID();
		if (DuelRuntime.blocksAbility(id)) {
			source.sendFailure(Component.literal("Дочекайся початку бою або завершення повернення речей."));
			return 0;
		}
		if (manager.get(id).fighterClass() != Loadout.FighterClass.CONCUSSED) {
			source.sendFailure(Component.literal("Електро-землетрус доступний класу Контужений."));
			return 0;
		}
		if (!player.isAlive() || player.isSpectator()) {
			source.sendFailure(Component.literal("Здібність доступна лише живому гравцю."));
			return 0;
		}
		int now = source.getServer().getTickCount();
		if (quakes.containsKey(id)) return 0;
		Integer lastUse = quakeCooldowns.get(id);
		if (lastUse != null && now - lastUse < ElectricQuake.COOLDOWN_TICKS) {
			return 0;
		}
		var look = player.getLookAngle();
		double len = Math.hypot(look.x, look.z);
		double fx = len < 1.0e-5 ? -Math.sin(Math.toRadians(player.getYRot())) : look.x / len;
		double fz = len < 1.0e-5 ? Math.cos(Math.toRadians(player.getYRot())) : look.z / len;

		int cooldown = Cooldowns.ticks(player, ElectricQuake.COOLDOWN_TICKS);
		quakeCooldowns.put(id, now - (ElectricQuake.COOLDOWN_TICKS - cooldown));
		QUAKE_TOTALS.put(id, cooldown);
		var active = new ActiveQuake(now, player.level(), player.position(), fx, fz, DuelRuntime.fighting(id), new HashSet<>());
		quakes.put(id, active);

		ShockwaveVisuals.playQuake(player, fx, fz);
		stepElectricQuake(player, active, 0);
		dev.flake.ui.FlakeUiServer.sync(source.getServer());
		return 1;
	}

	private static void stepElectricQuake(ServerPlayer caster, ActiveQuake active, int step) {
		ServerLevel level = active.level();
		Vec3 origin = active.origin();
		double fx = active.dirX();
		double fz = active.dirZ();
		double rx = -fz;
		double rz = fx;

		// The same nonlinear front is used by server hits and the existing VFX shader.
		double toDist = ElectricQuake.wavefrontDistance(step / 20.0f);
		// VFX is client-only: no redundant vanilla particles or per-observer particle packets.
		double frontX = origin.x + fx * toDist;
		double frontZ = origin.z + fz * toDist;
		double frontY = snapToGround(level, frontX, origin.y, frontZ);
		if (step == 3 || step == 6 || step == 9) {
			level.playSound(null, frontX, frontY, frontZ,
					SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, step == 9 ? 0.34f : 0.17f, 1.8f - step * 0.035f);
			level.playSound(null, frontX, frontY, frontZ,
					SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, step == 9 ? 0.30f : 0.12f, 0.85f);
		}

		// Evaluate hits up to the current wavefront (each target can only be hit once per cast)
		var casterId = caster.getUUID();
		double reachLimit = toDist + 0.65;

		for (UUID targetId : DuelRuntime.participants(casterId)) {
			if (casterId.equals(targetId) || active.hitTargets().contains(targetId)) continue;
			var target = level.getServer().getPlayerList().getPlayer(targetId);
			if (target == null || !target.isAlive() || target.level() != level) continue;
			var offset = target.position().subtract(origin);
			ElectricQuake.evaluate(fx, fz, offset.x, offset.y, offset.z).ifPresent(hit -> {
				if (hit.forwardDistance() <= reachLimit) {
					active.hitTargets().add(targetId);
					applyQuakeHit(caster, target, hit);
				}
			});
		}

		if (!DuelRuntime.fighting(casterId)) {
			var box = new AABB(
					origin.x - ElectricQuake.RANGE - 2, origin.y - 3, origin.z - ElectricQuake.RANGE - 2,
					origin.x + ElectricQuake.RANGE + 2, origin.y + 3, origin.z + ElectricQuake.RANGE + 2
			);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
				if (active.hitTargets().contains(mob.getUUID())) continue;
				var offset = mob.position().subtract(origin);
				ElectricQuake.evaluate(fx, fz, offset.x, offset.y, offset.z).ifPresent(hit -> {
					if (hit.forwardDistance() <= reachLimit) {
						active.hitTargets().add(mob.getUUID());
						if (mob.entityTags().contains(TEST_DUMMY_TAG) && mob.isNoAi()) prepareDummy(mob);
						applyQuakeHit(caster, mob, hit);
					}
				});
			}
		}
	}

	private static void applyQuakeHit(ServerPlayer caster, LivingEntity target, ElectricQuake.Hit hit) {
		ServerLevel level = caster.level();
		target.hurtServer(level, caster.damageSources().playerAttack(caster), CrystalClaw.skillDamage(caster, target, hit.damage()));
		target.push(hit.impulseX(), hit.impulseY(), hit.impulseZ());
		if (target instanceof ServerPlayer targetPlayer) {
			targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
		} else {
			target.syncVelocity = true;
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 18, 1, false, true, true));

		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.42f, 1.65f);
	}

	private static double snapToGround(ServerLevel level, double x, double refY, double z) {
		BlockPos base = BlockPos.containing(x, refY, z);
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos pos = base.offset(0, dy, 0);
			if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
					&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return pos.getY();
			}
		}
		return refY;
	}

	private static int spawnDummy(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var dummy = EntityTypes.PIG.create(player.level(), EntitySpawnReason.COMMAND);
		if (dummy == null) {
			source.sendFailure(Component.literal("Не вдалося створити манекен."));
			return 0;
		}
		var direction = player.getLookAngle();
		var position = player.position();
		dummy.setPos(position.x + direction.x * 2, position.y, position.z + direction.z * 2);
		prepareDummy(dummy);
		dummy.setPersistenceRequired();
		dummy.addTag(TEST_DUMMY_TAG);
		dummy.setCustomName(Component.literal("Тестовий манекен Flake"));
		dummy.setCustomNameVisible(true);
		if (!player.level().addFreshEntity(dummy)) {
			source.sendFailure(Component.literal("Не вдалося додати манекен у світ."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Манекен створено. Прибрати поруч: /flake dummy remove"), false);
		return 1;
	}

	private static void prepareDummy(Mob dummy) {
		// NoAI also disables movement physics in 26.3, swallowing knockback.
		// Slowness stops wandering while leaving velocity / gravity functional.
		dummy.setNoAi(false);
		dummy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, -1, 10, false, false, false));
	}

	private static int removeDummies(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		var player = source.getPlayerOrException();
		var dummies = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), entity -> entity.entityTags().contains(TEST_DUMMY_TAG));
		dummies.forEach(Mob::discard);
		source.sendSuccess(() -> Component.literal("Прибрано манекенів поблизу: " + dummies.size()), false);
		return dummies.size();
	}
}
