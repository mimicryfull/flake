package dev.flake.features.controlhammer;
import dev.flake.combat.Cooldowns;
import dev.flake.features.duel.DuelRuntime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Молот контролю": ЛКМ — звичайна атака, ПКМ — оглушення цілі.
 * Працює стабільно при наведенні на гравця, блок під ним або повітря.
 */
public final class ControlHammerRuntime {
	public static final int STUN_TICKS = 10;
	public static final int COOLDOWN_TICKS = 20;
	public static final double REACH = 4.2;
	public static final Identifier MODEL = Identifier.fromNamespaceAndPath("flake", "control_hammer");

	private static final class State {
		final Map<UUID, Integer> ready = new HashMap<>();
		final Map<UUID, Integer> totals = new HashMap<>();
	}
	private static final dev.flake.runtime.ServerState<State> STATES = new dev.flake.runtime.ServerState<>(State::new);

	public static Item ITEM;

	static void bind(Item item) { ITEM = item; }

	private ControlHammerRuntime() {}

	public static ItemStack stack() {
		return new ItemStack(ITEM);
	}

	public static boolean isHammer(ItemStack stack) {
		return stack.is(ITEM);
	}

	public static boolean isStunned(UUID id) {
		return dev.flake.combat.Stuns.active(id);
	}

	public static boolean stunned(UUID id, int nowTick) {
		return dev.flake.combat.Stuns.remaining(id, nowTick) > 0;
	}

	/** Remaining stun ticks for HUD status; 0 when not stunned. */
	public static int stunRemaining(UUID id, int nowTick) {
		return dev.flake.combat.Stuns.remaining(id, nowTick);
	}

	/** Actual cooldown length started by the last swing; falls back to the base value. */
	public static int cooldownTotalTicks(ServerPlayer player) {
		return STATES.of(player).totals.getOrDefault(player.getUUID(), COOLDOWN_TICKS);
	}

	public static void reset(ServerPlayer player) {
		dev.flake.combat.Stuns.reset(player);
		STATES.of(player).ready.remove(player.getUUID());
		STATES.of(player).totals.remove(player.getUUID());
		player.removeEffect(MobEffects.SLOWNESS);
		player.getCooldowns().removeCooldown(MODEL);
	}

	public static boolean use(ServerPlayer player) {
		return use(player, null);
	}

	public static boolean use(ServerPlayer player, Entity directTarget) {
		var stack = player.getMainHandItem();
		if (!isHammer(stack) || DuelRuntime.blocksAbility(player.getUUID())) return false;
		int now = player.level().getServer().getTickCount();
		var state = STATES.of(player);
		if (stunned(player.getUUID(), now) || now < state.ready.getOrDefault(player.getUUID(), 0)) return false;
		LivingEntity target = null;
		if (directTarget instanceof LivingEntity living && validTarget(player, living)
				&& player.distanceToSqr(living) <= (REACH + 0.6) * (REACH + 0.6)) {
			target = living;
		}
		if (target == null) target = findTarget(player);
		if (target == null || stunned(target.getUUID(), now)) return false;
		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		state.ready.put(player.getUUID(), now + cooldown);
		state.totals.put(player.getUUID(), cooldown);
		player.getCooldowns().addCooldown(MODEL, cooldown);
		dev.flake.combat.Stuns.apply(target, STUN_TICKS);
		dev.flake.features.controlhammer.ControlHammerVisuals.hammerStun(player, target);
		effects(target);
		return true;
	}

	private static boolean validTarget(Player player, Entity candidate) {
		if (!(candidate instanceof LivingEntity living) || !living.isAlive() || candidate == player) return false;
		if (DuelRuntime.fighting(player.getUUID())) {
			return candidate instanceof ServerPlayer other && DuelRuntime.opponents(player.getUUID(), other.getUUID());
		}
		return candidate instanceof Player || candidate instanceof Mob mob && mob.entityTags().contains("flake_test_dummy");
	}

	public static boolean hasClientTarget(Player player) {
		Vec3 from = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		var box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.25);
		for (var e : player.level().getEntities(player, box, entity -> entity instanceof LivingEntity l && l.isAlive())) {
			Vec3 aim = e.position().add(0, e.getBbHeight() * 0.55, 0);
			Vec3 delta = aim.subtract(from);
			double dist = delta.length();
			if (dist <= REACH + 0.4 && dist > 0.1 && delta.scale(1.0 / dist).dot(look) >= 0.65) return true;
		}
		return false;
	}

	private static LivingEntity findTarget(ServerPlayer player) {
		Vec3 from = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 to = from.add(look.scale(REACH));
		var box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.25);
		var hit = ProjectileUtil.getEntityHitResult(player.level(), player, from, to, box,
				e -> validTarget(player, e), 0.45f);
		if (hit != null && hit.getEntity() instanceof LivingEntity other) return other;
		if (!(player.level() instanceof ServerLevel level)) return null;
		LivingEntity best = null;
		double bestCos = 0.68;
		for (var e : level.getEntities(player, box, entity -> validTarget(player, entity))) {
			if (!(e instanceof LivingEntity other)) continue;
			Vec3 aim = other.position().add(0, other.getBbHeight() * 0.55, 0);
			Vec3 delta = aim.subtract(from);
			double dist = delta.length();
			if (dist > REACH || dist < 0.2) continue;
			double cos = delta.scale(1.0 / dist).dot(look);
			if (cos < bestCos) continue;
			var clip = level.clip(new ClipContext(from, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (clip.getType() != HitResult.Type.MISS) continue;
			best = other;
			bestCos = cos;
		}
		return best;
	}

	private static void effects(LivingEntity target) {
		if (!(target.level() instanceof ServerLevel level)) return;
		double y = target.getY() + target.getBbHeight() * 0.6;
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.8f, 1.4f);
	}

	public static void register() {
		var phase = Identifier.fromNamespaceAndPath("flake", "control_hammer");
		UseEntityCallback.EVENT.addPhaseOrdering(phase, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(phase, (player, level, hand, entity, hit) -> {
			if (blocked(player)) return InteractionResult.FAIL;
			if (!isHammer(player.getItemInHand(hand))) return InteractionResult.PASS;
			if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) return InteractionResult.PASS;
			if (level.isClientSide()) return InteractionResult.PASS;
			return player instanceof ServerPlayer sp && use(sp, entity) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			STATES.of(server).ready.entrySet().removeIf(e -> e.getValue() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			dev.flake.combat.Stuns.reset(handler.player);
			STATES.of(server).ready.remove(handler.player.getUUID());
			STATES.of(server).totals.remove(handler.player.getUUID());
		});
	}

	public static boolean blocked(Player player) {
		return dev.flake.combat.Stuns.blocked(player);
	}
}
