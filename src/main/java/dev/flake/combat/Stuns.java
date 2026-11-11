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
	private record Stun(int until, Vec3 anchor, int totalTicks) {}
	private static final ServerState<Map<UUID, Stun>> STATES = new ServerState<>(HashMap::new);
	private static final List<Consumer<ServerPlayer>> INTERRUPTS = new ArrayList<>();
	private static boolean installed;

	private Stuns() {}

	public static void onInterrupt(Consumer<ServerPlayer> interrupt) { INTERRUPTS.add(interrupt); }

	public static void apply(LivingEntity target, int ticks) {
		if (target instanceof ServerPlayer player) for (var interrupt : INTERRUPTS) interrupt.accept(player);
		int now = target.level().getServer().getTickCount();
		STATES.of(target.level().getServer()).put(target.getUUID(), new Stun(now + ticks, target.position(), ticks));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6, false, false, false));
		target.stopUsingItem();
		target.setDeltaMovement(Vec3.ZERO);
		target.syncVelocity = true;
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
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				blocked(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				blocked(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
				blocked(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				blocked(player) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			var states = STATES.of(server);
			int now = server.getTickCount();
			states.entrySet().removeIf(entry -> {
				if (entry.getValue().until() > now) return false;
				return true;
			});
			for (var entry : states.entrySet()) {
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
