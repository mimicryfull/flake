package dev.flake.features.stormharpoon;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.ability.AbilityMovement;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** «Грім-гарпун»: блискавичний кидок на 14 м, завдає 3.0 HP і сильно притягує ціль до гравця. */
public final class StormHarpoonRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 200;
	public static final int COOLDOWN_TICKS = 20;
	public static final double RANGE = 14.0;
	public static final float DAMAGE = 3.0f;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "storm_harpoon");

	private static final ServerState<Map<UUID, Integer>> READY = new ServerState<>(HashMap::new);

	public static Item ITEM;

	private StormHarpoonRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isItem(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	public static boolean activate(ServerPlayer player, Entity directTarget) {
		UUID id = player.getUUID();
		if (DuelRuntime.blocksAbility(id) || !(player.level() instanceof ServerLevel level)) return false;

		MinecraftServer server = level.getServer();
		int now = server.getTickCount();
		Map<UUID, Integer> ready = READY.of(server);
		if (now < ready.getOrDefault(id, 0)) return false;

		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		ready.put(id, now + cooldown);
		player.getCooldowns().addCooldown(ID, cooldown);

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		Vec3 maxEnd = eye.add(look.scale(RANGE));
		BlockHitResult blockClip = level.clip(new ClipContext(eye, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 rayEnd = blockClip.getType() != HitResult.Type.MISS ? blockClip.getLocation() : maxEnd;

		LivingEntity bestTarget = null;
		Vec3 bestHitPos = null;
		double bestDistSq = Double.MAX_VALUE;
		for (LivingEntity candidate : AbilityTargeting.candidates(player, level, player.position(), RANGE + 1.0)) {
			var hit = candidate.getBoundingBox().inflate(0.35).clip(eye, rayEnd);
			if (hit.isPresent()) {
				double dSq = eye.distanceToSqr(hit.get());
				if (dSq < bestDistSq) {
					bestDistSq = dSq;
					bestTarget = candidate;
					bestHitPos = hit.get();
				}
			}
		}

		if (bestTarget == null && directTarget instanceof LivingEntity living) {
			var candidates = AbilityTargeting.candidates(player, level, player.position(), RANGE + 1.0);
			if (candidates.contains(living) && eye.distanceToSqr(living.getEyePosition()) <= RANGE * RANGE) {
				Vec3 center = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0);
				if (level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
					bestTarget = living;
					bestHitPos = center;
				}
			}
		}

		if (bestTarget != null) {
			float damage = CombatModifiers.skillDamage(player, bestTarget, DAMAGE);
			bestTarget.hurtServer(level, player.damageSources().playerAttack(player), damage);

			Vec3 delta = player.position().subtract(bestTarget.position());
			Vec3 horiz = new Vec3(delta.x, 0.0, delta.z);
			double hDist = horiz.length();
			Vec3 pullDir = hDist > 1e-4 ? horiz.scale(1.0 / hDist) : look.multiply(-1.0, 0.0, -1.0).normalize();
			double pullSpeed = Math.min(1.65, 0.78 + hDist * 0.085);
			AbilityMovement.launch(bestTarget, pullDir.scale(pullSpeed).add(0.0, 0.32, 0.0));

			StormHarpoonVisuals.strike(player, eye, bestHitPos, bestTarget.getId());
			level.playSound(null, bestHitPos.x, bestHitPos.y, bestHitPos.z,
					SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.7f, 1.35f);
		} else {
			StormHarpoonVisuals.strike(player, eye, rayEnd, -1);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.85f, 1.25f);
		}
		return true;
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
		var server = player.level().getServer();
		if (server != null) READY.of(server).remove(player.getUUID());
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			READY.of(server).entrySet().removeIf(e -> e.getValue() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
