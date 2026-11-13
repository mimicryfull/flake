package dev.flake.features.sparksyringe;

import dev.flake.combat.Cooldowns;
import dev.flake.combat.Stuns;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** «Іскровий шприц»: миттєво знімає негативні ефекти і дає імунітет до станів та дебафів на 3 с. */
public final class SparkSyringeRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 360;
	public static final int COOLDOWN_TICKS = 20;
	public static final int BUFF_TICKS = 60;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "spark_syringe");

	private static final class State {
		final Map<UUID, Integer> buffs = new HashMap<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);

	public static Item ITEM;

	private SparkSyringeRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isItem(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	public static boolean isImmune(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return false;
		Integer until = STATES.of(server).buffs.get(player.getUUID());
		return until != null && until > server.getTickCount();
	}

	public static int remaining(ServerPlayer player, int nowTick) {
		Integer until = STATES.of(player).buffs.get(player.getUUID());
		return until == null ? 0 : Math.max(0, until - nowTick);
	}

	public static boolean activate(ServerPlayer player) {
		UUID id = player.getUUID();
		if (DuelRuntime.protectedInventory(id) && !DuelRuntime.fighting(id)) return false;
		var match = DuelRuntime.of(id);
		if (match != null && match.inCountdown(id)) return false;

		MinecraftServer server = player.level().getServer();
		if (server == null) return false;
		int now = server.getTickCount();
		State state = STATES.of(server);
		if (now < state.ready.getOrDefault(id, 0)) return false;

		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		state.buffs.put(id, now + BUFF_TICKS);
		state.ready.put(id, now + cooldown);
		player.getCooldowns().addCooldown(ID, cooldown);

		Stuns.reset(player);
		for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				player.removeEffect(effect.getEffect());
			}
		}

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.75f, 1.55f);
		SparkSyringeVisuals.cleanse(player);
		return true;
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
		var server = player.level().getServer();
		if (server == null) return;
		State state = STATES.existing(server);
		if (state != null) {
			state.buffs.remove(player.getUUID());
			state.ready.remove(player.getUUID());
		}
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			State state = STATES.of(server);
			state.buffs.entrySet().removeIf(entry -> {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				return entry.getValue() <= now || player == null || !player.isAlive()
						|| (DuelRuntime.protectedInventory(entry.getKey()) && !DuelRuntime.fighting(entry.getKey()));
			});
			state.ready.entrySet().removeIf(e -> e.getValue() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
