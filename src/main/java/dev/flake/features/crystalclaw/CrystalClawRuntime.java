package dev.flake.features.crystalclaw;
import dev.flake.features.duel.DuelRuntime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * «Кристалевий кіготь»: активний предмет (ПКМ). Тимчасово підсилює шкоду класових
 * здібностей на 20%. Баф не змінює атрибути гравця — множник застосовується
 * безпосередньо в точках нанесення шкоди здібностями на сервері.
 */
public final class CrystalClawRuntime {
	public static final int BUFF_TICKS = 100;
	public static final int COOLDOWN_TICKS = 20;
	public static final float SKILL_DAMAGE_MULTIPLIER = 1.20f;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "crystal_claw");

	private static final class State {
		final Map<UUID, Integer> buffs = new HashMap<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}
	private static final dev.flake.runtime.ServerState<State> STATES = new dev.flake.runtime.ServerState<>(State::new);

	private CrystalClawRuntime() {}

	public static Item ITEM;

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return new ItemStack(ITEM); }

	public static boolean active(ServerPlayer player) {
		Integer until = STATES.of(player).buffs.get(player.getUUID());
		return until != null && until > player.level().getServer().getTickCount();
	}

	/** Remaining buff ticks for HUD status; 0 when inactive. */
	public static int remaining(ServerPlayer player, int nowTick) {
		Integer until = STATES.of(player).buffs.get(player.getUUID());
		return until == null ? 0 : Math.max(0, until - nowTick);
	}

	/** Server-side multiplier for damage dealt by class abilities while the buff is active. */
	public static float skillDamage(ServerPlayer player, float damage) {
		return active(player) ? damage * SKILL_DAMAGE_MULTIPLIER : damage;
	}

	public static boolean activate(ServerPlayer player) {
		var id = player.getUUID();
		if (DuelRuntime.blocksAbility(id)) return false;
		if (active(player)) {
			dev.flake.combat.CombatFeedback.alreadyActive(player, stack());
			return false;
		}
		int now = player.level().getServer().getTickCount();
		var state = STATES.of(player);
		int readyAt = state.ready.getOrDefault(id, 0);
		if (now < readyAt) {
			dev.flake.combat.CombatFeedback.onCooldown(player, stack(), readyAt - now);
			return false;
		}
		state.buffs.put(id, now + BUFF_TICKS);
		state.ready.put(id, now + COOLDOWN_TICKS);
		player.getCooldowns().addCooldown(ID, COOLDOWN_TICKS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.9f, 1.35f);
		dev.flake.features.crystalclaw.CrystalClawVisuals.crystal(player, true);
		return true;
	}

	public static void reset(ServerPlayer player) {
		boolean wasActive = STATES.of(player).buffs.remove(player.getUUID()) != null;
		STATES.of(player).ready.remove(player.getUUID());
		player.getCooldowns().removeCooldown(ID);
		if (wasActive) dev.flake.features.crystalclaw.CrystalClawVisuals.crystal(player, false);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			var state = STATES.of(server);
			for (var entry : Map.copyOf(state.buffs).entrySet()) {
				var player = server.getPlayerList().getPlayer(entry.getKey());
				if (entry.getValue() > now && player != null && player.isAlive() && !DuelRuntime.blocksAbility(entry.getKey())) continue;
				state.buffs.remove(entry.getKey());
				if (player != null) dev.flake.features.crystalclaw.CrystalClawVisuals.crystal(player, false);
			}
			state.ready.entrySet().removeIf(e -> e.getValue() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
