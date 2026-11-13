package dev.flake.features.chronotrident;

import dev.flake.combat.Cooldowns;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * «Хроно-тризуб» (Chrono Trident): активний артефакт (ПКМ).
 * Створює фіолетову Хроносферу в точці прицілювання (до 7.5 бл.), плавно сповільнюючи ворогів
 * майже до повної зупинки (міліметровий рух без ривків телепортації), поки кастер вільно рухається всередині.
 */
public final class ChronoTridentRuntime {
	public static final int COOLDOWN_TICKS = 20;
	public static final int DURATION_TICKS = (int) Math.ceil(ChronosphereAnimation.DURATION_SECONDS * 20);
	public static final double RADIUS = ChronosphereAnimation.MAX_RADIUS;
	public static final double CAST_RANGE = 7.5;
	private static final double MOVE_SLOW_MULTIPLIER = -0.94;
	private static final double JUMP_SLOW_MULTIPLIER = -0.90;
	private static final double ATTACK_SLOW_MULTIPLIER = -0.70;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "chrono_trident");
	private static final Identifier CHRONO_MOVE_MOD = Identifier.fromNamespaceAndPath("flake", "chrono_time_slow");
	private static final Identifier CHRONO_JUMP_MOD = Identifier.fromNamespaceAndPath("flake", "chrono_jump_slow");
	private static final Identifier CHRONO_ATTACK_MOD = Identifier.fromNamespaceAndPath("flake", "chrono_attack_slow");

	private static final class ChronosphereZone {
		final UUID ownerId;
		final ServerLevel level;
		final Vec3 center;
		final int endTick;
		final Set<UUID> slowedIds = new HashSet<>();

		ChronosphereZone(UUID ownerId, ServerLevel level, Vec3 center, int endTick) {
			this.ownerId = ownerId;
			this.level = level;
			this.center = center;
			this.endTick = endTick;
		}
	}

	private static final class State {
		final List<ChronosphereZone> zones = new ArrayList<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);

	private ChronoTridentRuntime() {}

	public static Item ITEM;

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isTrident(ItemStack stack) { return ITEM != null && stack.is(ITEM); }

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ChronoTridentRuntime::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			clearTimeDilation(handler.player);
			removeOwnerState(server, handler.player.getUUID());
		});
	}

	public static boolean castChronosphere(ServerPlayer player) {
		UUID id = player.getUUID();
		if (DuelRuntime.blocksAbility(id) || !(player.level() instanceof ServerLevel level)) return false;

		MinecraftServer server = level.getServer();
		int now = server.getTickCount();
		State state = STATES.of(server);
		if (now < state.ready.getOrDefault(id, 0)) return false;

		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		state.ready.put(id, now + cooldown);
		player.getCooldowns().addCooldown(ID, cooldown);

		Vec3 center = resolveCastCenter(player, level);
		removeOwnerZones(server, state, id);
		state.zones.add(new ChronosphereZone(id, level, center, now + DURATION_TICKS));

		ChronoTridentVisuals.chronosphere(player, center);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.72f, 0.68f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.85f, 0.58f);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.65f, 0.52f);
		return true;
	}

	private static Vec3 resolveCastCenter(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		Vec3 maxEnd = eye.add(look.scale(CAST_RANGE));

		var blockClip = level.clip(new ClipContext(eye, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 rayEnd = blockClip.getType() != HitResult.Type.MISS ? blockClip.getLocation() : maxEnd;

		// Prefer locking onto an enemy along the look ray so Chronosphere catches them cleanly.
		double maxDist = eye.distanceTo(rayEnd);
		LivingEntity bestTarget = null;
		double bestDist = Double.MAX_VALUE;
		AABB rayBox = player.getBoundingBox().expandTowards(look.scale(maxDist)).inflate(1.25);
		for (var e : level.getEntities(player, rayBox, entity -> entity instanceof LivingEntity l && l.isAlive() && isAffectedTarget(player.getUUID(), l))) {
			var box = e.getBoundingBox().inflate(0.55);
			var hit = box.clip(eye, rayEnd);
			if (hit.isPresent()) {
				double d = eye.distanceToSqr(hit.get());
				if (d < bestDist) {
					bestDist = d;
					bestTarget = (LivingEntity) e;
				}
			}
		}

		if (bestTarget != null) {
			return bestTarget.position().add(0.0, 0.85, 0.0);
		}
		if (blockClip.getType() != HitResult.Type.MISS) {
			return blockClip.getLocation().add(0.0, 0.9, 0.0);
		}
		// Default forward placement on open air, snapped to ground below if nearby
		Vec3 forward = player.position().add(look.x * 4.2, 0.0, look.z * 4.2);
		Vec3 probeTop = forward.add(0.0, 1.5, 0.0);
		Vec3 probeBottom = forward.add(0.0, -3.5, 0.0);
		var groundClip = level.clip(new ClipContext(probeTop, probeBottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 floor = groundClip.getType() != HitResult.Type.MISS ? groundClip.getLocation() : forward;
		return floor.add(0.0, 0.9, 0.0);
	}

	private static boolean isAffectedTarget(UUID ownerId, LivingEntity entity) {
		if (entity.getUUID().equals(ownerId) || !entity.isAlive()) return false;
		if (DuelRuntime.fighting(ownerId)) {
			return entity instanceof ServerPlayer sp && DuelRuntime.opponents(ownerId, sp.getUUID());
		}
		return entity instanceof ServerPlayer || entity instanceof Mob;
	}

	private static void tick(MinecraftServer server) {
		State state = STATES.of(server);
		int now = server.getTickCount();
		state.ready.entrySet().removeIf(entry -> entry.getValue() <= now);
		if (state.zones.isEmpty()) return;

		Set<UUID> stillSlowedAnywhere = new HashSet<>();
		Set<UUID> candidatesForClear = new HashSet<>();

		var it = state.zones.iterator();
		while (it.hasNext()) {
			ChronosphereZone zone = it.next();
			int remainingTicks = zone.endTick - now;
			if (remainingTicks <= 0) {
				candidatesForClear.addAll(zone.slowedIds);
				it.remove();
				continue;
			}

			ServerPlayer owner = server.getPlayerList().getPlayer(zone.ownerId);
			if (owner != null && owner.level() == zone.level && owner.isAlive()
					&& owner.position().add(0.0, 0.9, 0.0).distanceToSqr(zone.center) <= RADIUS * RADIUS) {
				// Caster moves swiftly inside their own Chronosphere
				owner.addEffect(new MobEffectInstance(MobEffects.SPEED, 5, 1, false, false, false));
			}

			Set<UUID> insideNow = new HashSet<>();
			AABB box = new AABB(
					zone.center.x - RADIUS, zone.center.y - RADIUS, zone.center.z - RADIUS,
					zone.center.x + RADIUS, zone.center.y + RADIUS, zone.center.z + RADIUS
			);
			for (var e : zone.level.getEntities((Entity) null, box,
					entity -> entity instanceof LivingEntity l && isAffectedTarget(zone.ownerId, l))) {
				LivingEntity target = (LivingEntity) e;
				Vec3 targetCenter = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
				if (targetCenter.distanceToSqr(zone.center) > RADIUS * RADIUS) continue;

				UUID tid = target.getUUID();
				insideNow.add(tid);
				stillSlowedAnywhere.add(tid);
				if (zone.slowedIds.add(tid)) {
					applyTimeDilation(target);
				}
			}

			for (UUID prevId : zone.slowedIds) {
				if (!insideNow.contains(prevId)) candidatesForClear.add(prevId);
			}
			zone.slowedIds.retainAll(insideNow);
		}

		for (UUID tid : candidatesForClear) {
			if (stillSlowedAnywhere.contains(tid)) continue;
			Entity entity = server.overworld().getEntityInAnyDimension(tid);
			if (entity instanceof LivingEntity living) {
				clearTimeDilation(living);
			}
		}
	}

	private static void applyTimeDilation(LivingEntity target) {
		setModifier(target, Attributes.MOVEMENT_SPEED, CHRONO_MOVE_MOD, MOVE_SLOW_MULTIPLIER);
		setModifier(target, Attributes.JUMP_STRENGTH, CHRONO_JUMP_MOD, JUMP_SLOW_MULTIPLIER);
		setModifier(target, Attributes.ATTACK_SPEED, CHRONO_ATTACK_MOD, ATTACK_SLOW_MULTIPLIER);
	}

	private static void clearTimeDilation(LivingEntity target) {
		removeModifier(target, Attributes.MOVEMENT_SPEED, CHRONO_MOVE_MOD);
		removeModifier(target, Attributes.JUMP_STRENGTH, CHRONO_JUMP_MOD);
		removeModifier(target, Attributes.ATTACK_SPEED, CHRONO_ATTACK_MOD);
	}

	private static void setModifier(LivingEntity target, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attrHolder,
			Identifier id, double amount) {
		var attr = target.getAttribute(attrHolder);
		if (attr == null) return;
		attr.removeModifier(id);
		attr.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void removeModifier(LivingEntity target, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attrHolder,
			Identifier id) {
		var attr = target.getAttribute(attrHolder);
		if (attr != null) attr.removeModifier(id);
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
		clearTimeDilation(player);
		MinecraftServer server = player.level().getServer();
		if (server != null) removeOwnerState(server, player.getUUID());
	}

	private static void removeOwnerState(MinecraftServer server, UUID ownerId) {
		State state = STATES.of(server);
		state.ready.remove(ownerId);
		removeOwnerZones(server, state, ownerId);
	}

	private static void removeOwnerZones(MinecraftServer server, State state, UUID ownerId) {
		Set<UUID> cleared = new HashSet<>();
		var it = state.zones.iterator();
		while (it.hasNext()) {
			ChronosphereZone zone = it.next();
			if (!zone.ownerId.equals(ownerId)) continue;
			cleared.addAll(zone.slowedIds);
			it.remove();
		}
		if (cleared.isEmpty()) return;
		for (UUID tid : cleared) {
			boolean stillInAnother = state.zones.stream().anyMatch(z -> z.slowedIds.contains(tid));
			if (stillInAnother) continue;
			Entity entity = server.overworld().getEntityInAnyDimension(tid);
			if (entity instanceof LivingEntity living) {
				clearTimeDilation(living);
			}
		}
	}
}
