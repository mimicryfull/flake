package dev.flake.features.chaosbroadsword;

import dev.flake.combat.Cooldowns;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * «Хаотичний палаш»: 9 базової шкоди та повільна атака. ПКМ — круговий розмах,
 * що завдає 10 шкоди й на 3 секунди зменшує вихідну шкоду цілей на 30%.
 */
public final class ChaosBroadswordRuntime {
	public static final int COOLDOWN_TICKS = 260;
	public static final int WEAKNESS_TICKS = 60;
	public static final float SWEEP_DAMAGE = 10.0f;
	public static final double SWEEP_RADIUS = 4.5;
	public static final float WEAKNESS_MULTIPLIER = 0.7f;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "chaos_broadsword");
	public static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath("flake", "chaos_broadsword_sweep");
	private static final Identifier WEAKNESS_MODIFIER = Identifier.fromNamespaceAndPath("flake", "broadsword_weakness");

	private static final class State {
		final Map<UUID, Integer> weaknessUntil = new HashMap<>();
	}
	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static SoundEvent sound;

	private ChaosBroadswordRuntime() {}

	public static Item ITEM;

	static void bind(Item item) { ITEM = item; }
	static void bindSound(SoundEvent event) { sound = event; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isBroadsword(ItemStack stack) { return ITEM != null && stack.is(ITEM); }

	public static boolean sweep(ServerPlayer player) {
		if (DuelRuntime.blocksAbility(player.getUUID()) || !(player.level() instanceof ServerLevel level)) return false;
		int now = level.getServer().getTickCount();
		player.getCooldowns().addCooldown(ID, Cooldowns.ticks(player, COOLDOWN_TICKS));
		for (LivingEntity target : AbilityTargeting.candidates(player, level, player.position(), SWEEP_RADIUS)) {
			if (target.distanceToSqr(player) > SWEEP_RADIUS * SWEEP_RADIUS) continue;
			target.hurtServer(level, player.damageSources().playerAttack(player), SWEEP_DAMAGE);
			weaken(target, now);
		}
		ChaosBroadswordVisuals.sweep(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 0.9f);
		return true;
	}

	public static boolean weakened(ServerPlayer player) {
		return remaining(player, player.level().getServer().getTickCount()) > 0;
	}

	public static float scaleOutgoing(ServerPlayer attacker, float damage) {
		return weakened(attacker) ? damage * WEAKNESS_MULTIPLIER : damage;
	}

	public static int remaining(ServerPlayer player, int now) {
		Integer until = STATES.of(player).weaknessUntil.get(player.getUUID());
		return until == null ? 0 : Math.max(0, until - now);
	}

	private static void weaken(LivingEntity target, int now) {
		if (!(target instanceof ServerPlayer player)) return;
		STATES.of(player).weaknessUntil.put(player.getUUID(), now + WEAKNESS_TICKS);
		var attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attribute == null) return;
		attribute.removeModifier(WEAKNESS_MODIFIER);
		attribute.addTransientModifier(new AttributeModifier(WEAKNESS_MODIFIER, -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	public static void reset(ServerPlayer player) {
		var state = STATES.of(player);
		state.weaknessUntil.remove(player.getUUID());
		var attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attribute != null) attribute.removeModifier(WEAKNESS_MODIFIER);
		player.getCooldowns().removeCooldown(ID);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			for (var state : STATES.values()) {
				state.weaknessUntil.entrySet().removeIf(entry -> {
					if (entry.getValue() > now) return false;
					var player = server.getPlayerList().getPlayer(entry.getKey());
					if (player != null) {
						var attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
						if (attribute != null) attribute.removeModifier(WEAKNESS_MODIFIER);
					}
					return true;
				});
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
