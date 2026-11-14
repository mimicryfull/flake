package dev.flake.features.kolobok;

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
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * «Колобок-стрілець»: на 4 с випускає гладкого колобка з двома АК-47,
 * що стріляє чергами по ворогах на дистанції (1.0 HP кожні 0.6 с).
 */
public final class KolobokRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 360;
	public static final int COOLDOWN_TICKS = 20;
	public static final int DURATION_TICKS = 80;
	public static final int FIRE_INTERVAL_TICKS = 3;
	public static final int DAMAGE_EVERY_N_SHOTS = 4;
	public static final float SHOT_DAMAGE = 0.9f;
	public static final double SEARCH_RADIUS = 12.0;
	public static final double PREFERRED_RANGE = 5.2;
	public static final double MOVE_SPEED = 0.24;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "kolobok");

	private static final class Runner {
		final UUID ownerId;
		final int ownerEntityId;
		final ServerLevel level;
		Vec3 pos;
		float yaw;
		final int expiresAtTick;
		int nextShotTick;
		int shotCount;
		int targetId = -1;

		Runner(UUID ownerId, int ownerEntityId, ServerLevel level, Vec3 pos, float yaw, int expiresAtTick, int nextShotTick) {
			this.ownerId = ownerId;
			this.ownerEntityId = ownerEntityId;
			this.level = level;
			this.pos = pos;
			this.yaw = yaw;
			this.expiresAtTick = expiresAtTick;
			this.nextShotTick = nextShotTick;
		}
	}

	private static final class State {
		final Map<UUID, Runner> active = new HashMap<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	public static Item ITEM;

	private KolobokRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isItem(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	public static int remaining(ServerPlayer player, int nowTick) {
		Runner r = STATES.of(player).active.get(player.getUUID());
		return r == null ? 0 : Math.max(0, r.expiresAtTick - nowTick);
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

		Runner prev = state.active.remove(id);
		if (prev != null) KolobokVisuals.despawn(prev.level, player, prev.ownerEntityId, prev.pos);

		ServerLevel level = player.level();
		Vec3 flatForward = Vec3.directionFromRotation(0.0f, player.getYRot()).normalize();
		Vec3 spawnPos = snapToGround(level, player.position().add(flatForward.scale(1.35)), player.getY());
		LivingEntity target = findNearestTarget(player, level, spawnPos);
		int targetId = target == null ? -1 : target.getId();
		float yaw = target != null ? yawTowards(spawnPos, target.position()) : player.getYRot();

		Runner runner = new Runner(id, player.getId(), level, spawnPos, yaw, now + DURATION_TICKS, now + 4);
		runner.targetId = targetId;
		state.active.put(id, runner);

		level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75f, 1.45f);
		level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
				SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.70f, 1.25f);
		KolobokVisuals.spawn(player, spawnPos, yaw, targetId);
		return true;
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(KolobokRuntime::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}

	public static void reset(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		State state = STATES.of(server);
		state.ready.remove(player.getUUID());
		Runner removed = state.active.remove(player.getUUID());
		if (removed != null) KolobokVisuals.despawn(removed.level, player, removed.ownerEntityId, removed.pos);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		State state = STATES.of(server);
		if (state.active.isEmpty()) return;

		Iterator<Map.Entry<UUID, Runner>> it = state.active.entrySet().iterator();
		while (it.hasNext()) {
			Runner runner = it.next().getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(runner.ownerId);
			if (owner == null || !owner.isAlive() || owner.isSpectator()
					|| now >= runner.expiresAtTick || invalidMatchState(owner) || owner.level() != runner.level) {
				KolobokVisuals.despawn(runner.level, owner, runner.ownerEntityId, runner.pos);
				it.remove();
				continue;
			}

			LivingEntity target = findNearestTarget(owner, runner.level, runner.pos);
			if (target != null) {
				runner.targetId = target.getId();
				Vec3 tPos = target.position();
				double horiz = horizontalDistance(runner.pos, tPos);
				if (horiz > PREFERRED_RANGE) {
					Vec3 next = stepTowards(runner.pos, tPos, Math.min(MOVE_SPEED, horiz - PREFERRED_RANGE * 0.85));
					runner.pos = snapToGround(runner.level, next, Math.max(runner.pos.y, tPos.y));
				}
				runner.yaw = yawTowards(runner.pos, tPos);

				Vec3 targetCenter = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
				if (now >= runner.nextShotTick && hasLineOfSight(runner.level, runner.pos.add(0.0, 0.55, 0.0), targetCenter)) {
					runner.nextShotTick = now + FIRE_INTERVAL_TICKS;
					int shotIndex = runner.shotCount++;
					boolean leftHand = (shotIndex % 2) == 0;
					if (shotIndex % DAMAGE_EVERY_N_SHOTS == 0) {
						float damage = CombatModifiers.skillDamage(owner, target, SHOT_DAMAGE);
						target.hurtServer(runner.level, owner.damageSources().playerAttack(owner), damage);
					}

					runner.level.playSound(null, runner.pos.x, runner.pos.y, runner.pos.z,
							SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.62f, leftHand ? 1.65f : 1.85f);
					runner.level.playSound(null, runner.pos.x, runner.pos.y, runner.pos.z,
							SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.35f, 1.50f);
					KolobokVisuals.shoot(owner, runner.pos, runner.yaw, leftHand, target.getId(), targetCenter);
				}
			} else {
				runner.targetId = -1;
				Vec3 follow = owner.position().add(Vec3.directionFromRotation(0.0f, owner.getYRot()).scale(1.6));
				double dist = horizontalDistance(runner.pos, follow);
				if (dist > 0.45) {
					Vec3 next = stepTowards(runner.pos, follow, Math.min(MOVE_SPEED, dist));
					runner.pos = snapToGround(runner.level, next, Math.max(runner.pos.y, owner.getY()));
					runner.yaw = yawTowards(runner.pos, follow);
				}
			}
		}
	}

	private static boolean hasLineOfSight(ServerLevel level, Vec3 from, Vec3 to) {
		var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
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
		var hit = level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : new Vec3(pos.x, referenceY, pos.z);
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
