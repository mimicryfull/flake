package dev.flake.features.combatdrone;

import dev.flake.combat.CombatFeedback;
import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * «Бойовий дрон»: активний артефакт, що на 4 с випускає маленького воксельного дрона,
 * який переслідує ворогів та завдає періодичної шкоди (2.0 HP щосекунди) у ближньому бою.
 */
public final class CombatDroneRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 320;
	public static final int COOLDOWN_TICKS = 20;
	public static final int DURATION_TICKS = 80;
	public static final int ATTACK_INTERVAL_TICKS = 20;
	public static final float DAMAGE = 2.0f;
	public static final double SEARCH_RADIUS = 12.0;
	public static final double MELEE_RANGE = 2.15;
	public static final double STANDOFF_RANGE = 1.15;
	public static final double MOVE_SPEED = 0.34;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "combat_drone");

	private static final class Drone {
		final UUID ownerId;
		ServerLevel level;
		Vec3 pos;
		float yaw;
		final int expiresAtTick;
		int nextAttackTick;
		int targetId = -1;

		Drone(UUID ownerId, ServerLevel level, Vec3 pos, float yaw, int expiresAtTick, int nextAttackTick) {
			this.ownerId = ownerId;
			this.level = level;
			this.pos = pos;
			this.yaw = yaw;
			this.expiresAtTick = expiresAtTick;
			this.nextAttackTick = nextAttackTick;
		}
	}

	private static final class State {
		final Map<UUID, Drone> active = new HashMap<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	public static Item ITEM;

	private CombatDroneRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isItem(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	public static int remaining(ServerPlayer player, int nowTick) {
		Drone drone = STATES.of(player).active.get(player.getUUID());
		return drone == null ? 0 : Math.max(0, drone.expiresAtTick - nowTick);
	}

	public static boolean activate(ServerPlayer player) {
		UUID id = player.getUUID();
		if (DuelRuntime.protectedInventory(id) && !DuelRuntime.fighting(id)) return false;
		var match = DuelRuntime.of(id);
		if (match != null && match.inCountdown(id)) return false;

		MinecraftServer server = player.level().getServer();
		if (server == null) return false;
		int now = server.getTickCount();
		State state = STATES.of(server);
		int readyAt = state.ready.getOrDefault(id, 0);
		if (now < readyAt) {
			CombatFeedback.onCooldown(player, stack(), readyAt - now);
			return false;
		}

		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		state.ready.put(id, now + cooldown);
		player.getCooldowns().addCooldown(ID, cooldown);

		Drone previous = state.active.remove(id);
		if (previous != null) CombatDroneVisuals.despawn(player, previous.pos);

		ServerLevel level = player.level();
		Vec3 flatForward = Vec3.directionFromRotation(0.0f, player.getYRot()).normalize();
		Vec3 spawnPos = snapToGround(level, player.position().add(flatForward.scale(1.35)), player.getY());
		LivingEntity initialTarget = findNearestTarget(player, level, spawnPos);
		int initialTargetId = initialTarget == null ? -1 : initialTarget.getId();
		float yaw = initialTarget != null ? yawTowards(spawnPos, initialTarget.position()) : player.getYRot();

		Drone drone = new Drone(id, level, spawnPos, yaw, now + DURATION_TICKS, now);
		drone.targetId = initialTargetId;
		state.active.put(id, drone);

		level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
				SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 0.72f, 1.55f);
		level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.55f, 1.85f);
		CombatDroneVisuals.spawn(player, spawnPos, yaw, initialTargetId);
		return true;
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CombatDroneRuntime::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}

	public static void reset(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		State state = STATES.of(server);
		state.ready.remove(player.getUUID());
		Drone removed = state.active.remove(player.getUUID());
		if (removed != null) CombatDroneVisuals.despawn(player, removed.pos);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		State state = STATES.of(server);
		if (state.active.isEmpty()) return;

		Iterator<Map.Entry<UUID, Drone>> it = state.active.entrySet().iterator();
		while (it.hasNext()) {
			Drone drone = it.next().getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(drone.ownerId);
			if (owner == null || !owner.isAlive() || owner.isSpectator()
					|| now >= drone.expiresAtTick || invalidMatchState(owner)) {
				if (owner != null) CombatDroneVisuals.despawn(owner, drone.pos);
				it.remove();
				continue;
			}
			if (owner.level() != drone.level) {
				CombatDroneVisuals.despawn(owner, drone.pos);
				it.remove();
				continue;
			}

			LivingEntity target = findNearestTarget(owner, drone.level, drone.pos);
			Vec3 destination;
			if (target != null) {
				drone.targetId = target.getId();
				Vec3 tPos = target.position();
				double dist = horizontalDistance(drone.pos, tPos);
				if (dist > STANDOFF_RANGE) {
					Vec3 stepPos = stepTowards(drone.pos, tPos, Math.min(MOVE_SPEED, dist - STANDOFF_RANGE * 0.85));
					drone.pos = snapToGround(drone.level, stepPos, Math.max(drone.pos.y, tPos.y));
				} else {
					drone.pos = snapToGround(drone.level, drone.pos, drone.pos.y);
				}
				drone.yaw = yawTowards(drone.pos, tPos);

				double meleeDist = drone.pos.distanceTo(tPos);
				if (meleeDist <= MELEE_RANGE && now >= drone.nextAttackTick) {
					drone.nextAttackTick = now + ATTACK_INTERVAL_TICKS;
					float damage = CombatModifiers.skillDamage(owner, target, DAMAGE);
					target.hurtServer(drone.level, owner.damageSources().playerAttack(owner), damage);
					drone.level.playSound(null, drone.pos.x, drone.pos.y, drone.pos.z,
							SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.85f, 1.45f);
					drone.level.playSound(null, drone.pos.x, drone.pos.y, drone.pos.z,
							SoundEvents.IRON_GOLEM_HURT, SoundSource.PLAYERS, 0.45f, 1.90f);
					CombatDroneVisuals.strike(owner, drone.pos, drone.yaw, target.getId(),
							target.position().add(0.0, target.getBbHeight() * 0.55, 0.0));
				}
			} else {
				drone.targetId = -1;
				destination = owner.position().add(Vec3.directionFromRotation(0.0f, owner.getYRot()).scale(1.4));
				double dist = horizontalDistance(drone.pos, destination);
				if (dist > 0.35) {
					Vec3 stepPos = stepTowards(drone.pos, destination, Math.min(MOVE_SPEED, dist));
					drone.pos = snapToGround(drone.level, stepPos, Math.max(drone.pos.y, owner.getY()));
					drone.yaw = yawTowards(drone.pos, destination);
				}
			}
		}
	}

	private static LivingEntity findNearestTarget(ServerPlayer owner, ServerLevel level, Vec3 from) {
		List<LivingEntity> candidates = AbilityTargeting.candidates(owner, level, from, SEARCH_RADIUS);
		return candidates.stream()
				.min(Comparator.comparingDouble(e -> e.position().distanceToSqr(from)))
				.orElse(null);
	}

	private static Vec3 snapToGround(ServerLevel level, Vec3 pos, double referenceY) {
		Vec3 top = new Vec3(pos.x, referenceY + 1.5, pos.z);
		Vec3 bottom = new Vec3(pos.x, referenceY - 3.5, pos.z);
		var hit = level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (hit.getType() == HitResult.Type.BLOCK) return hit.getLocation();
		return new Vec3(pos.x, referenceY, pos.z);
	}

	static Vec3 stepTowards(Vec3 from, Vec3 to, double maxStep) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double horiz = Math.hypot(dx, dz);
		if (horiz < 1.0e-5 || maxStep <= 0.0) return from;
		double scale = Math.min(1.0, maxStep / horiz);
		return new Vec3(from.x + dx * scale, from.y, from.z + dz * scale);
	}

	static float yawTowards(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		if (dx * dx + dz * dz < 1.0e-6) return 0.0f;
		return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private static boolean invalidMatchState(ServerPlayer player) {
		if (DuelRuntime.protectedInventory(player.getUUID()) && !DuelRuntime.fighting(player.getUUID())) return true;
		var match = DuelRuntime.of(player.getUUID());
		return match != null && match.inCountdown(player.getUUID());
	}
}
