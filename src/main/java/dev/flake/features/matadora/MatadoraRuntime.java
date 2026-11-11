package dev.flake.features.matadora;
import dev.flake.features.duel.DuelRuntime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * «Матадора»: активний предмет (ПКМ). Тимчасово дає +10% шкоди та +20% швидкості,
 * шейдерний оверлей для власника і об'ємну фіолетову ауру з кубічними частинками.
 */
public final class MatadoraRuntime {
	public static final int BUFF_TICKS = 100;
	public static final int COOLDOWN_TICKS = 300;
	public static final double DAMAGE_BONUS = 0.10;
	public static final double SPEED_BONUS = 0.20;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "matadora");
	public static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath("flake", "matadora_activate");

	private static SoundEvent sound;

	private static final Identifier DAMAGE_MOD = Identifier.fromNamespaceAndPath("flake", "matadora_damage");
	private static final Identifier SPEED_MOD = Identifier.fromNamespaceAndPath("flake", "matadora_speed");
	private static final class State {
		final Map<UUID, Integer> buffs = new HashMap<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}
	private static final dev.flake.combat.ServerState<State> STATES = new dev.flake.combat.ServerState<>(State::new);

	private MatadoraRuntime() {}

	public static Item ITEM;

	static void bind(Item item) { ITEM = item; }
	static void bindSound(SoundEvent event) { sound = event; }

	public static ItemStack stack() { return new ItemStack(ITEM); }

	public static boolean active(ServerPlayer player) { return STATES.of(player).buffs.containsKey(player.getUUID()); }

	/** Remaining buff ticks for HUD status; 0 when inactive. */
	public static int remaining(ServerPlayer player, int nowTick) {
		Integer until = STATES.of(player).buffs.get(player.getUUID());
		return until == null ? 0 : Math.max(0, until - nowTick);
	}

	public static boolean activate(ServerPlayer player) {
		var id = player.getUUID();
		if (DuelRuntime.blocksAbility(id)) return false;
		int now = player.level().getServer().getTickCount();
		var state = STATES.of(player);
		if (active(player) || now < state.ready.getOrDefault(id, 0)) return false;
		state.buffs.put(id, now + BUFF_TICKS);
		state.ready.put(id, now + COOLDOWN_TICKS);
		player.getCooldowns().addCooldown(ID, COOLDOWN_TICKS);
		apply(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
		return true;
	}

	private static void apply(ServerPlayer player) {
		remove(player, false);
		var damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (damage != null) damage.addTransientModifier(new AttributeModifier(DAMAGE_MOD, DAMAGE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		if (speed != null) speed.addTransientModifier(new AttributeModifier(SPEED_MOD, SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		MatadoraVisuals.mask(player, true);
	}

	private static void remove(ServerPlayer player, boolean notify) {
		var damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (damage != null) damage.removeModifier(DAMAGE_MOD);
		if (speed != null) speed.removeModifier(SPEED_MOD);
		if (notify) MatadoraVisuals.mask(player, false);
	}

	public static void reset(ServerPlayer player) {
		STATES.of(player).buffs.remove(player.getUUID());
		STATES.of(player).ready.remove(player.getUUID());
		remove(player, true);
		player.getCooldowns().removeCooldown(ID);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			var state = STATES.of(server);
			for (var entry : Map.copyOf(state.buffs).entrySet()) {
				var player = server.getPlayerList().getPlayer(entry.getKey());
				boolean over = entry.getValue() <= now || player == null || !player.isAlive() || DuelRuntime.blocksAbility(entry.getKey());
				if (!over) continue;
				state.buffs.remove(entry.getKey());
				if (player != null) remove(player, true);
			}
			state.ready.entrySet().removeIf(e -> e.getValue() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
