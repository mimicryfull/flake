package dev.flake.features.dispatcher;

import dev.flake.Flake;
import dev.flake.combat.CombatModifiers;
import dev.flake.combat.ability.AbilityEffects;
import dev.flake.combat.ability.AbilityMovement;
import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.combat.ability.ClassAbilityRuntime;
import dev.flake.combat.ability.ClassAbilityRuntimes;
import dev.flake.combat.ability.RuntimeClassSpec;
import dev.flake.content.ContentCatalog;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.Loadout.FighterClass;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Dispatcher class runtime:
 * 1) Dispatch (8-block ground slash + dash): grants complete damage/knockback immunity while dashing 8 blocks forward,
 *    piercing enemies along the slash for 50% of their current HP with blue/dark-blue VFX and echoed slash reverb.
 * 2) Desert Squad (5s / 100 ticks): summons 4 AK-47 soldiers in brown patterned wraps & headwraps showing only their eyes.
 */
public final class DispatcherRuntime implements ClassAbilityRuntime {
	public static final int DISPATCH_TICKS = 8;
	public static final double DISPATCH_LENGTH = 8.0;
	public static final double DISPATCH_HALF_WIDTH = 1.45;
	public static final float DISPATCH_HP_FRACTION = 0.50f;

	public static final int SQUAD_TICKS = 100;
	public static final int SQUAD_COUNT = 4;
	public static final int SQUAD_WARMUP_TICKS = 8;
	public static final int SQUAD_FIRE_INTERVAL_TICKS = 2;
	public static final float SQUAD_SHOT_DAMAGE = 0.60f;
	public static final double SQUAD_RANGE = 18.0;

	private static final Identifier DISPATCH_KB_MOD = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "dispatcher_dash_kb");
	private static final double[] FORMATION_ANGLES = {
			Math.toRadians(-68.0), Math.toRadians(-24.0), Math.toRadians(24.0), Math.toRadians(68.0)
	};
	private static final double[] FORMATION_RADII = { 2.55, 2.25, 2.25, 2.55 };

	private static final Map<MinecraftServer, DispatcherRuntime> INSTANCES = new IdentityHashMap<>();
	private static boolean installed;

	private final MinecraftServer server;
	private final Map<UUID, DispatchSession> dispatches = new HashMap<>();
	private final Map<UUID, SquadSession> squads = new HashMap<>();

	private static final class DispatchSession {
		final ServerLevel level;
		final Vec3 startPos;
		final Vec3 endPos;
		final Vec3 dir;
		final double actualLength;
		final int startedAt;
		final int expiresAt;
		boolean reverb1Done;
		boolean reverb2Done;
		final Set<UUID> hitIds = new HashSet<>();

		DispatchSession(ServerLevel level, Vec3 startPos, Vec3 endPos, Vec3 dir, int startedAt, int expiresAt) {
			this.level = level;
			this.startPos = startPos;
			this.endPos = endPos;
			this.dir = dir;
			this.actualLength = startPos.distanceTo(endPos);
			this.startedAt = startedAt;
			this.expiresAt = expiresAt;
		}
	}

	private static final class Soldier {
		Vec3 pos;
		float yaw;
		int nextShotTick;

		Soldier(Vec3 pos, float yaw, int nextShotTick) {
			this.pos = pos;
			this.yaw = yaw;
			this.nextShotTick = nextShotTick;
		}
	}

	private static final class SquadSession {
		final ServerLevel level;
		final int ownerEntityId;
		final Vec3 anchorPos;
		final int startedAt;
		final int expiresAt;
		final Soldier[] soldiers;
		int fireCursor;

		SquadSession(ServerLevel level, int ownerEntityId, Vec3 anchorPos, int startedAt, int expiresAt, Soldier[] soldiers) {
			this.level = level;
			this.ownerEntityId = ownerEntityId;
			this.anchorPos = anchorPos;
			this.startedAt = startedAt;
			this.expiresAt = expiresAt;
			this.soldiers = soldiers;
		}
	}

	public DispatcherRuntime(MinecraftServer server) {
		this.server = server;
		INSTANCES.put(server, this);
	}

	public static void install() {
		if (installed) return;
		installed = true;
		ClassAbilityRuntimes.registerRuntime(FighterClass.DISPATCHER, DispatcherRuntime::new);
		ContentCatalog.registerClass(new RuntimeClassSpec(
				FighterClass.DISPATCHER,
				"flake.class.dispatcher.role",
				"flake.class.dispatcher.summary",
				Ability.DISPATCH,
				Ability.DESERT_SQUAD));

		// Complete immunity to all attacks, damage, and knockback while Dispatch is active
		CombatModifiers.damageGate((victim, source) -> {
			if (!(victim instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return true;
			MinecraftServer srv = level.getServer();
			DispatcherRuntime rt = INSTANCES.get(srv);
			return rt == null || !rt.isDashing(player.getUUID(), srv.getTickCount());
		});
	}

	@Override public List<Ability> abilities() {
		return List.of(Ability.DISPATCH, Ability.DESERT_SQUAD);
	}

	@Override public boolean cast(ServerPlayer player, Ability ability, int now) {
		ServerLevel level = (ServerLevel) player.level();
		return switch (ability) {
			case DISPATCH -> castDispatch(player, level, now);
			case DESERT_SQUAD -> castSquad(player, level, now);
			default -> false;
		};
	}

	public boolean isDashing(UUID playerId, int now) {
		DispatchSession session = dispatches.get(playerId);
		return session != null && now < session.expiresAt;
	}

	private boolean castDispatch(ServerPlayer player, ServerLevel level, int now) {
		Vec3 dir = AbilityMovement.flat(player);
		Vec3 startPos = player.position();
		Vec3 endPos = resolveSlashEndpoint(player, level, startPos, dir);
		float yawRad = (float) Math.atan2(-dir.x, dir.z);

		DispatchSession session = new DispatchSession(level, startPos, endPos, dir, now, now + DISPATCH_TICKS);
		dispatches.put(player.getUUID(), session);
		grantKnockbackImmunity(player);

		AbilityMovement.launch(player, dir.scale(1.85).add(0.0, 0.04, 0.0));
		pierceTargetsAlongSlash(player, session, 3.0);

		DispatcherVisuals.dispatchSlash(player, startPos, endPos, yawRad);
		// Primary razor-sharp slash layer
		AbilityEffects.sound(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.20f, 1.45f);
		level.playSound(null, startPos.x, startPos.y, startPos.z,
				SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 0.95f, 1.25f);
		AbilityEffects.sound(level, player, SoundEvents.MACE_SMASH_AIR, 0.80f, 0.70f);
		return true;
	}

	private boolean castSquad(ServerPlayer player, ServerLevel level, int now) {
		removeSquad(player.getUUID(), false);

		Vec3 forward = AbilityMovement.flat(player);
		float baseYaw = (float) Math.atan2(-forward.x, forward.z);
		Soldier[] soldiers = new Soldier[SQUAD_COUNT];
		Vec3 base = player.position();
		for (int i = 0; i < SQUAD_COUNT; i++) {
			double angle = baseYaw + FORMATION_ANGLES[i];
			Vec3 offsetDir = new Vec3(-Math.sin(angle), 0.0, Math.cos(angle));
			Vec3 pos = resolveFormationPos(player, level, base, offsetDir, FORMATION_RADII[i]);
			soldiers[i] = new Soldier(pos, (float) Math.atan2(-forward.x, forward.z), now + SQUAD_WARMUP_TICKS + i * 2);
		}

		SquadSession session = new SquadSession(level, player.getId(), base, now, now + SQUAD_TICKS, soldiers);
		squads.put(player.getUUID(), session);

		DispatcherVisuals.squadOn(player, baseYaw);
		for (int i = 0; i < SQUAD_COUNT; i++) {
			DispatcherVisuals.squadSync(player, i, soldiers[i].pos, soldiers[i].yaw);
		}

		AbilityEffects.sound(level, player, SoundEvents.BEACON_POWER_SELECT, 0.70f, 1.25f);
		AbilityEffects.sound(level, player, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.85f, 0.85f);
		return true;
	}

	@Override public int activeElapsed(UUID player, int slot, int now) {
		if (slot == 1) {
			DispatchSession session = dispatches.get(player);
			if (session == null || now >= session.expiresAt) return -1;
			return Math.clamp(now - session.startedAt, 0, DISPATCH_TICKS);
		}
		if (slot == 2) {
			SquadSession session = squads.get(player);
			if (session == null || now >= session.expiresAt) return -1;
			return Math.clamp(now - session.startedAt, 0, SQUAD_TICKS);
		}
		return -1;
	}

	@Override public void cancelBuffs(ServerPlayer player, int now) {
		clearDispatch(player);
		removeSquad(player.getUUID(), true);
	}

	@Override public void tick(int now) {
		tickDispatches(now);
		tickSquads(now);
	}

	private void tickDispatches(int now) {
		if (dispatches.isEmpty()) return;
		for (var entry : new ArrayList<>(dispatches.entrySet())) {
			UUID ownerId = entry.getKey();
			DispatchSession session = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			if (owner == null || !owner.isAlive() || owner.level() != session.level || DuelRuntime.blocksAbility(ownerId)) {
				if (owner != null) removeKnockbackImmunity(owner);
				dispatches.remove(ownerId);
				continue;
			}

			int elapsed = now - session.startedAt;
			if (!session.reverb1Done && elapsed >= 2) {
				session.reverb1Done = true;
				Vec3 mid = session.startPos.lerp(session.endPos, 0.5);
				session.level.playSound(null, mid.x, mid.y, mid.z,
						SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.60f, 0.92f);
				session.level.playSound(null, mid.x, mid.y, mid.z,
						SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.48f, 0.72f);
			}
			if (!session.reverb2Done && elapsed >= 5) {
				session.reverb2Done = true;
				session.level.playSound(null, session.endPos.x, session.endPos.y, session.endPos.z,
						SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.30f, 0.62f);
				session.level.playSound(null, session.endPos.x, session.endPos.y, session.endPos.z,
						SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.32f, 0.58f);
			}

			if (elapsed == 2 || elapsed == 4) {
				double progress = owner.position().subtract(session.startPos).dot(session.dir);
				if (progress < session.actualLength - 1.2) {
					AbilityMovement.launch(owner, session.dir.scale(1.55).add(0.0, 0.03, 0.0));
				}
			}

			double reachProgress = Math.min(session.actualLength, 3.0 + elapsed * 1.25);
			pierceTargetsAlongSlash(owner, session, reachProgress);

			if (now >= session.expiresAt) {
				removeKnockbackImmunity(owner);
				dispatches.remove(ownerId);
			}
		}
	}

	private void pierceTargetsAlongSlash(ServerPlayer owner, DispatchSession session, double maxDist) {
		Vec3 centre = session.startPos.lerp(session.endPos, 0.5);
		double radius = session.actualLength * 0.5 + 2.5;
		for (LivingEntity candidate : AbilityTargeting.candidates(owner, session.level, centre, radius)) {
			if (!candidate.isAlive() || session.hitIds.contains(candidate.getUUID())) continue;
			Vec3 rel = candidate.position().subtract(session.startPos);
			if (Math.abs(rel.y) > 2.4) continue;
			double proj = rel.x * session.dir.x + rel.z * session.dir.z;
			if (proj < -0.6 || proj > maxDist + 0.8) continue;
			double perpSq = Math.max(0.0, (rel.x * rel.x + rel.z * rel.z) - proj * proj);
			if (perpSq > DISPATCH_HALF_WIDTH * DISPATCH_HALF_WIDTH) continue;

			session.hitIds.add(candidate.getUUID());
			float dmg = Math.max(1.0f, candidate.getHealth() * DISPATCH_HP_FRACTION);
			candidate.hurtServer(session.level, owner.damageSources().indirectMagic(null, owner), dmg);
			session.level.playSound(null, candidate.getX(), candidate.getY(), candidate.getZ(),
					SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.90f, 1.30f);
		}
	}

	private void tickSquads(int now) {
		if (squads.isEmpty()) return;
		for (var entry : new ArrayList<>(squads.entrySet())) {
			UUID ownerId = entry.getKey();
			SquadSession session = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			if (owner == null || !owner.isAlive() || owner.level() != session.level
					|| DuelRuntime.blocksAbility(ownerId) || now >= session.expiresAt) {
				removeSquad(ownerId, true);
				continue;
			}

			if (now < session.startedAt + SQUAD_WARMUP_TICKS) continue;
			if ((now - session.startedAt) % SQUAD_FIRE_INTERVAL_TICKS != 0) continue;

			int idx = session.fireCursor++ % SQUAD_COUNT;
			Soldier soldier = session.soldiers[idx];
			if (now < soldier.nextShotTick) continue;

			LivingEntity target = findTargetWithLos(owner, session.level, soldier.pos);
			if (target == null) continue;

			soldier.nextShotTick = now + 7;
			Vec3 targetChest = target.position().add(0.0, target.getBbHeight() * 0.56, 0.0);
			Vec3 diff = targetChest.subtract(soldier.pos);
			soldier.yaw = (float) Math.atan2(-diff.x, diff.z);

			float dmg = CombatModifiers.skillDamage(owner, target, SQUAD_SHOT_DAMAGE);
			target.hurtServer(session.level, owner.damageSources().indirectMagic(null, owner), dmg);

			session.level.playSound(null, soldier.pos.x, soldier.pos.y, soldier.pos.z,
					SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.65f, 1.65f + (idx % 3) * 0.12f);
			session.level.playSound(null, soldier.pos.x, soldier.pos.y, soldier.pos.z,
					SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.38f, 1.48f);
			DispatcherVisuals.squadShot(owner, soldier.pos, soldier.yaw, target.getId(), targetChest);
		}
	}

	private static LivingEntity findTargetWithLos(ServerPlayer owner, ServerLevel level, Vec3 from) {
		Vec3 eye = from.add(0.0, 1.45, 0.0);
		return AbilityTargeting.candidates(owner, level, from, SQUAD_RANGE).stream()
				.filter(e -> e.position().distanceToSqr(from) <= SQUAD_RANGE * SQUAD_RANGE)
				.filter(e -> hasLineOfSight(level, eye, e.position().add(0.0, e.getBbHeight() * 0.55, 0.0)))
				.min(Comparator.comparingDouble(e -> e.position().distanceToSqr(from)))
				.orElse(null);
	}

	private static boolean hasLineOfSight(ServerLevel level, Vec3 from, Vec3 to) {
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}

	private static Vec3 resolveSlashEndpoint(ServerPlayer player, ServerLevel level, Vec3 start, Vec3 dir) {
		Vec3 eye = start.add(0.0, 0.9, 0.0);
		Vec3 target = eye.add(dir.scale(DISPATCH_LENGTH));
		BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 flatEnd = hit.getType() == HitResult.Type.BLOCK
				? hit.getLocation().subtract(dir.scale(0.45))
				: target;
		return new Vec3(flatEnd.x, start.y, flatEnd.z);
	}

	private static Vec3 resolveFormationPos(ServerPlayer owner, ServerLevel level, Vec3 base, Vec3 dir, double radius) {
		Vec3 start = base.add(0.0, 0.9, 0.0);
		Vec3 end = start.add(dir.scale(radius));
		BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Vec3 flat = hit.getType() == HitResult.Type.BLOCK
				? hit.getLocation().subtract(dir.scale(0.55))
				: end;
		BlockHitResult ground = level.clip(new ClipContext(
				flat.add(0.0, 1.0, 0.0), flat.subtract(0.0, 2.5, 0.0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		double y = ground.getType() == HitResult.Type.BLOCK ? ground.getLocation().y : base.y;
		return new Vec3(flat.x, y, flat.z);
	}

	private void clearDispatch(ServerPlayer player) {
		if (dispatches.remove(player.getUUID()) != null) {
			removeKnockbackImmunity(player);
		}
	}

	private void removeSquad(UUID ownerId, boolean sendOffVfx) {
		SquadSession session = squads.remove(ownerId);
		if (session == null || !sendOffVfx) return;
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
		DispatcherVisuals.squadOff(session.level, owner, session.ownerEntityId, session.anchorPos);
	}

	private static void grantKnockbackImmunity(ServerPlayer player) {
		AttributeInstance inst = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (inst == null) return;
		inst.removeModifier(DISPATCH_KB_MOD);
		inst.addTransientModifier(new AttributeModifier(DISPATCH_KB_MOD, 1.0, AttributeModifier.Operation.ADD_VALUE));
	}

	private static void removeKnockbackImmunity(ServerPlayer player) {
		AttributeInstance inst = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (inst != null) inst.removeModifier(DISPATCH_KB_MOD);
	}

	@Override public void reset(ServerPlayer player) {
		clearDispatch(player);
		removeSquad(player.getUUID(), true);
	}

	@Override public void clear() {
		for (UUID id : List.copyOf(dispatches.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) removeKnockbackImmunity(player);
		}
		dispatches.clear();
		squads.clear();
		INSTANCES.remove(server);
	}
}
