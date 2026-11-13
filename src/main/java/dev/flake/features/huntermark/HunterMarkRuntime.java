package dev.flake.features.huntermark;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** «Мітка мисливця»: позначає ворога на 6 с (+15% шкоди, блок невидимості та диму). */
public final class HunterMarkRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 280;
	public static final int COOLDOWN_TICKS = 20;
	public static final int MARK_TICKS = 120;
	public static final int HIGHLIGHT_INTERVAL_TICKS = 8;
	public static final double RANGE = 16.0;
	public static final double AIM_COS = 0.85;
	public static final double SMOKE_IMMUNITY_RANGE = 24.0;
	public static final float DAMAGE_MULTIPLIER = 1.15f;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "hunter_mark");

	private static final class Mark {
		final UUID ownerId;
		final UUID targetId;
		final ServerLevel level;
		final int expiresAt;
		int nextHighlightAt;

		Mark(UUID ownerId, UUID targetId, ServerLevel level, int expiresAt, int nextHighlightAt) {
			this.ownerId = ownerId;
			this.targetId = targetId;
			this.level = level;
			this.expiresAt = expiresAt;
			this.nextHighlightAt = nextHighlightAt;
		}
	}

	private static final class State {
		final Map<UUID, Mark> marks = new HashMap<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);

	public static Item ITEM;

	private HunterMarkRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isItem(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	public static int remaining(ServerPlayer player, int nowTick) {
		Mark mark = STATES.of(player).marks.get(player.getUUID());
		return mark == null ? 0 : Math.max(0, mark.expiresAt - nowTick);
	}

	public static boolean hasClientTarget(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		var box = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(2.0);
		for (var e : player.level().getEntities(player, box, entity -> entity instanceof LivingEntity l && l.isAlive())) {
			Vec3 aim = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
			Vec3 delta = aim.subtract(eye);
			double dist = delta.length();
			if (dist <= RANGE && dist >= 0.3 && delta.scale(1.0 / dist).dot(look) >= AIM_COS) return true;
		}
		return false;
	}

	public static boolean activate(ServerPlayer player, Entity directTarget) {
		UUID id = player.getUUID();
		if (DuelRuntime.blocksAbility(id) || !(player.level() instanceof ServerLevel level)) return false;

		MinecraftServer server = level.getServer();
		int now = server.getTickCount();
		State state = STATES.of(server);
		if (now < state.ready.getOrDefault(id, 0)) return false;

		LivingEntity target = resolveTarget(player, directTarget);
		if (target == null) return false;

		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		state.ready.put(id, now + cooldown);
		player.getCooldowns().addCooldown(ID, cooldown);

		state.marks.put(id, new Mark(id, target.getUUID(), level, now + MARK_TICKS, now + HIGHLIGHT_INTERVAL_TICKS));
		target.removeEffect(MobEffects.INVISIBILITY);
		if (target.distanceToSqr(player) <= SMOKE_IMMUNITY_RANGE * SMOKE_IMMUNITY_RANGE) {
			player.removeEffect(MobEffects.BLINDNESS);
		}

		HunterMarkVisuals.markBurst(player, target);
		HunterMarkVisuals.highlight(player, target);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.7f, 1.55f);
		return true;
	}

	private static LivingEntity resolveTarget(ServerPlayer player, Entity directTarget) {
		if (directTarget instanceof LivingEntity living && validTarget(player, living)) {
			return living;
		}
		return AbilityTargeting.target(player, RANGE, AIM_COS, true);
	}

	private static boolean validTarget(ServerPlayer player, LivingEntity living) {
		if (!living.isAlive() || living == player) return false;
		if (DuelRuntime.fighting(player.getUUID())) {
			return living instanceof ServerPlayer other && DuelRuntime.opponents(player.getUUID(), other.getUUID());
		}
		return living instanceof Player || (living instanceof Mob mob && mob.entityTags().contains("flake_test_dummy"));
	}

	static float scaleDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer attacker)) return amount;
		return isMarkedBy(victim, attacker) ? amount * DAMAGE_MULTIPLIER : amount;
	}

	static boolean allowEffect(LivingEntity entity, MobEffectInstance effect) {
		if (effect.is(MobEffects.INVISIBILITY) && isMarked(entity)) {
			return false;
		}
		if (effect.is(MobEffects.BLINDNESS) && entity instanceof ServerPlayer owner && hasNearbyMarkedTarget(owner)) {
			return false;
		}
		return true;
	}

	private static boolean isMarkedBy(LivingEntity victim, ServerPlayer attacker) {
		MinecraftServer server = attacker.level().getServer();
		if (server == null) return false;
		State state = STATES.existing(server);
		if (state == null) return false;
		Mark mark = state.marks.get(attacker.getUUID());
		return mark != null && mark.expiresAt > server.getTickCount() && mark.targetId.equals(victim.getUUID());
	}

	private static boolean isMarked(LivingEntity entity) {
		MinecraftServer server = entity.level().getServer();
		if (server == null) return false;
		State state = STATES.existing(server);
		if (state == null || state.marks.isEmpty()) return false;
		int now = server.getTickCount();
		UUID entityId = entity.getUUID();
		for (Mark mark : state.marks.values()) {
			if (mark.expiresAt > now && mark.targetId.equals(entityId)) return true;
		}
		return false;
	}

	private static boolean hasNearbyMarkedTarget(ServerPlayer owner) {
		MinecraftServer server = owner.level().getServer();
		if (server == null) return false;
		State state = STATES.existing(server);
		if (state == null) return false;
		Mark mark = state.marks.get(owner.getUUID());
		if (mark == null || mark.expiresAt <= server.getTickCount()) return false;
		Entity target = mark.level.getEntity(mark.targetId);
		return target instanceof LivingEntity living && living.isAlive()
				&& living.level() == owner.level()
				&& living.distanceToSqr(owner) <= SMOKE_IMMUNITY_RANGE * SMOKE_IMMUNITY_RANGE;
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
		var server = player.level().getServer();
		if (server == null) return;
		State state = STATES.existing(server);
		if (state == null) return;
		UUID id = player.getUUID();
		state.marks.remove(id);
		state.ready.remove(id);
		state.marks.values().removeIf(mark -> mark.targetId.equals(id));
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			State state = STATES.of(server);
			int now = server.getTickCount();
			state.ready.entrySet().removeIf(e -> e.getValue() <= now);
			if (state.marks.isEmpty()) return;

			var it = state.marks.entrySet().iterator();
			while (it.hasNext()) {
				Mark mark = it.next().getValue();
				ServerPlayer owner = server.getPlayerList().getPlayer(mark.ownerId);
				Entity entity = mark.level.getEntity(mark.targetId);
				if (now >= mark.expiresAt || owner == null || owner.level() != mark.level || !owner.isAlive()
						|| !(entity instanceof LivingEntity target) || !target.isAlive()
						|| invalidMatchState(mark.ownerId)) {
					it.remove();
					continue;
				}
				if (target.hasEffect(MobEffects.INVISIBILITY)) {
					target.removeEffect(MobEffects.INVISIBILITY);
				}
				if (owner.hasEffect(MobEffects.BLINDNESS)
						&& target.distanceToSqr(owner) <= SMOKE_IMMUNITY_RANGE * SMOKE_IMMUNITY_RANGE) {
					owner.removeEffect(MobEffects.BLINDNESS);
				}
				if (now >= mark.nextHighlightAt) {
					mark.nextHighlightAt = now + HIGHLIGHT_INTERVAL_TICKS;
					HunterMarkVisuals.highlight(owner, target);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}

	private static boolean invalidMatchState(UUID id) {
		if (DuelRuntime.protectedInventory(id) && !DuelRuntime.fighting(id)) return true;
		var match = DuelRuntime.of(id);
		return match != null && match.inCountdown(id);
	}
}
