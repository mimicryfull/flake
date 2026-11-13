package dev.flake.combat;

import dev.flake.runtime.ServerState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared action-blocking state. A weapon may cause a stun, but does not own the game's stun rules. */
public final class Stuns {
	private record Stun(int until, Vec3 anchor, int totalTicks, boolean anchorMovement) {}
	private static final ServerState<Map<UUID, Stun>> STATES = new ServerState<>(HashMap::new);
	private static final List<Consumer<ServerPlayer>> INTERRUPTS = new ArrayList<>();
	private static final List<java.util.function.Predicate<LivingEntity>> IMMUNITIES = new ArrayList<>();
	private static final List<java.util.function.BiConsumer<LivingEntity, Integer>> STUN_LISTENERS = new ArrayList<>();
	private static boolean installed;

	private Stuns() {}

	public static void onInterrupt(Consumer<ServerPlayer> interrupt) { INTERRUPTS.add(interrupt); }
	public static void registerImmunity(java.util.function.Predicate<LivingEntity> immunity) { IMMUNITIES.add(immunity); }
	public static void registerOnStun(java.util.function.BiConsumer<LivingEntity, Integer> listener) { STUN_LISTENERS.add(listener); }

	public static void apply(LivingEntity target, int ticks) {
		applyInternal(target, ticks, true);
	}

	/** Stuns actions, items, and skills while allowing smooth creeping movement without teleport snap-back. */
	public static void applySlowStun(LivingEntity target, int ticks) {
		applyInternal(target, ticks, false);
	}

	private static void applyInternal(LivingEntity target, int ticks, boolean anchorMovement) {
		for (var immunity : IMMUNITIES) if (immunity.test(target)) return;
		if (target instanceof ServerPlayer player) for (var interrupt : INTERRUPTS) interrupt.accept(player);
		int now = target.level().getServer().getTickCount();
		STATES.of(target.level().getServer()).put(target.getUUID(), new Stun(now + ticks, target.position(), ticks, anchorMovement));
		target.stopUsingItem();
		if (anchorMovement) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6, false, false, false));
			target.setDeltaMovement(Vec3.ZERO);
			target.syncVelocity = true;
		}
		for (var listener : STUN_LISTENERS) listener.accept(target, ticks);
	}

	public static void clearSlowStun(LivingEntity target) {
		var server = target.level().getServer();
		if (server == null) return;
		var states = STATES.of(server);
		var stun = states.get(target.getUUID());
		if (stun != null && !stun.anchorMovement()) states.remove(target.getUUID());
	}

	public static boolean blocked(Player player) {
		return player instanceof ServerPlayer serverPlayer && remaining(serverPlayer, serverPlayer.level().getServer().getTickCount()) > 0;
	}

	public static int remaining(ServerPlayer player, int now) {
		var stun = STATES.of(player).get(player.getUUID());
		return stun == null ? 0 : Math.max(0, stun.until() - now);
	}

	/** UUID-only facade for existing match/mixin queries; each state is still server-owned. */
	public static int remaining(UUID id, int now) {
		for (var states : STATES.values()) {
			var stun = states.get(id);
			if (stun != null) return Math.max(0, stun.until() - now);
		}
		return 0;
	}

	public static boolean active(UUID id) {
		for (var states : STATES.values()) if (states.containsKey(id)) return true;
		return false;
	}

	public static void reset(ServerPlayer player) {
		STATES.of(player).remove(player.getUUID());
	}

	public static void install() {
		if (installed) return;
		installed = true;
		CombatStatuses.register((player, now, statuses) -> {
			var stun = STATES.of(player).get(player.getUUID());
			int remaining = stun == null ? 0 : Math.max(0, stun.until() - now);
			if (remaining > 0) statuses.addFirst(new CombatStatuses.Status("stun", remaining, stun.totalTicks(), 0));
		});
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!blocked(player)) return net.minecraft.world.InteractionResult.PASS;
			if (player instanceof ServerPlayer sp) CombatFeedback.blocked(sp, sp.getItemInHand(hand));
			return net.minecraft.world.InteractionResult.FAIL;
		});
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				blocked(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!blocked(player)) return net.minecraft.world.InteractionResult.PASS;
			if (player instanceof ServerPlayer sp && !sp.getItemInHand(hand).isEmpty()) {
				CombatFeedback.blocked(sp, sp.getItemInHand(hand));
			}
			return net.minecraft.world.InteractionResult.FAIL;
		});
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!blocked(player)) return net.minecraft.world.InteractionResult.PASS;
			if (player instanceof ServerPlayer sp && !sp.getItemInHand(hand).isEmpty()) {
				CombatFeedback.blocked(sp, sp.getItemInHand(hand));
			}
			return net.minecraft.world.InteractionResult.FAIL;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			var states = STATES.of(server);
			int now = server.getTickCount();
			states.entrySet().removeIf(entry -> {
				if (entry.getValue().until() > now) return false;
				return true;
			});
			for (var entry : states.entrySet()) {
				if (!entry.getValue().anchorMovement()) continue;
				var entity = server.overworld().getEntityInAnyDimension(entry.getKey());
				if (entity == null) continue;
				var anchor = entry.getValue().anchor();
				if (entity.position().distanceToSqr(anchor) > 0.0001) {
					if (entity instanceof ServerPlayer player)
						player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
					else entity.setPos(anchor.x, anchor.y, anchor.z);
				}
				if (entity.getDeltaMovement().lengthSqr() > 0.0001) {
					entity.setDeltaMovement(Vec3.ZERO);
					entity.syncVelocity = true;
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
