package dev.flake.features.chainblade;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * «Ланцюгове лезо» (Chain Blade): 6.5 базової шкоди.
 * ПКМ — притягує ціль у прицілі (до 9 м) приблизно на 3 блоки до власника
 * й накладає мітку на 2.5 с (50 тіків): наступний ближній удар по цій цілі завдає +30% шкоди.
 */
public final class ChainBladeRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 160;
	public static final int COOLDOWN_TICKS = 20;
	public static final int MARK_TICKS = 50;
	public static final float FOLLOWUP_MULTIPLIER = 1.30f;
	public static final double PULL_RANGE = 9.0;
	public static final double PULL_BLOCKS = 3.0;
	public static final double AIM_COS = 0.6;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "chain_blade");

	private record PullMark(UUID victimId, int untilTick) {}

	private static final class State {
		final Map<UUID, PullMark> marks = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static boolean registered;

	public static Item ITEM;

	private ChainBladeRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isChainBlade(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	public static boolean pull(ServerPlayer player, Entity directTarget) {
		if (DuelRuntime.blocksAbility(player.getUUID()) || !(player.level() instanceof ServerLevel level)) return false;
		if (!isChainBlade(player.getMainHandItem())) return false;

		LivingEntity target = null;
		if (directTarget instanceof LivingEntity living && validTarget(player, living)
				&& player.distanceToSqr(living) <= PULL_RANGE * PULL_RANGE) {
			Vec3 eye = player.getEyePosition();
			Vec3 aim = living.position().add(0, living.getBbHeight() * 0.5, 0);
			var clip = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (clip.getType() == HitResult.Type.MISS) target = living;
		}
		if (target == null) target = AbilityTargeting.target(player, PULL_RANGE, AIM_COS, true);
		if (target == null) return false;

		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.getCooldowns().addCooldown(ID, Cooldowns.ticks(player, COOLDOWN_TICKS));

		Vec3 pullDir = AbilityMovement.horizontalDir(target.position(), player.position(), player);
		double horizDist = Math.hypot(player.getX() - target.getX(), player.getZ() - target.getZ());
		Vec3 velocity = computePullVelocity(pullDir, horizDist, player.getY() - target.getY());
		AbilityMovement.launch(target, velocity);

		int now = level.getServer().getTickCount();
		STATES.of(level.getServer()).marks.put(player.getUUID(), new PullMark(target.getUUID(), now + MARK_TICKS));

		ChainBladeVisuals.pull(player, target);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 0.75f, 1.35f);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.85f, 0.8f);
		return true;
	}

	/** Computes pull launch velocity so the victim slides ~3 blocks toward the wielder without overshooting behind them. */
	public static Vec3 computePullVelocity(Vec3 pullDir, double horizontalDistance, double deltaY) {
		double desiredPull = Math.min(PULL_BLOCKS, Math.max(0.6, horizontalDistance - 1.0));
		double speed = 0.25 * desiredPull;
		double vy = Math.clamp(0.24 + deltaY * 0.05, 0.20, 0.36);
		return pullDir.scale(speed).add(0.0, vy, 0.0);
	}

	/** Applies +30% damage on the wielder's next melee hit against the pulled target and consumes the mark. */
	public static float scaleMeleeDamage(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0f) return amount;
		if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker) return amount;
		if (!isChainBlade(attacker.getMainHandItem())) return amount;
		var server = attacker.level().getServer();
		if (server == null) return amount;

		var marks = STATES.of(server).marks;
		PullMark mark = marks.get(attacker.getUUID());
		if (mark == null) return amount;

		int now = server.getTickCount();
		if (now > mark.untilTick()) {
			marks.remove(attacker.getUUID());
			return amount;
		}
		if (!mark.victimId().equals(victim.getUUID())) return amount;

		marks.remove(attacker.getUUID());
		if (victim.level() instanceof ServerLevel level) {
			level.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
					SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9f, 1.15f);
		}
		return amount * FOLLOWUP_MULTIPLIER;
	}

	public static boolean hasClientTarget(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		var box = player.getBoundingBox().expandTowards(look.scale(PULL_RANGE)).inflate(1.0);
		for (var entity : player.level().getEntities(player, box, candidate -> candidate instanceof LivingEntity living && living.isAlive())) {
			Vec3 aim = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
			Vec3 delta = aim.subtract(eye);
			double distance = delta.length();
			if (distance <= PULL_RANGE && distance >= 0.3 && delta.scale(1.0 / distance).dot(look) >= AIM_COS) return true;
		}
		return false;
	}

	private static boolean validTarget(ServerPlayer player, LivingEntity living) {
		if (!living.isAlive() || living == player) return false;
		if (DuelRuntime.fighting(player.getUUID())) {
			return living instanceof ServerPlayer other && DuelRuntime.opponents(player.getUUID(), other.getUUID());
		}
		return living instanceof Player || (living instanceof Mob mob && mob.entityTags().contains("flake_test_dummy"));
	}

	public static void register() {
		if (registered) return;
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			STATES.of(server).marks.entrySet().removeIf(entry -> entry.getValue().untilTick() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
		var server = player.level().getServer();
		if (server == null) return;
		UUID id = player.getUUID();
		var marks = STATES.of(server).marks;
		marks.remove(id);
		marks.values().removeIf(mark -> mark.victimId().equals(id));
	}
}
