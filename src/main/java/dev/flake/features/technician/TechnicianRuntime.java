package dev.flake.features.technician;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Stuns;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Technician («Технік») owns two signature aerial munitions:
 * Slot 1: «Орєшнік» — homing missile with plasma trail & multi-ring concussion explosion.
 * Slot 2: Strike UAV («Ударний БПЛА») — tactical overhead sweep dropping 2 precision bombs before a kamikaze dive.
 */
public final class TechnicianRuntime implements ClassAbilityRuntime {
	public static final int MISSILE_FLIGHT_TICKS = 45;
	public static final int MISSILE_BOOST_TICKS = 4;
	public static final double MISSILE_SPEED = 0.72;
	public static final double MISSILE_SEEK_RADIUS = 22.0;
	public static final double MISSILE_TURN_BLEND = 0.26;
	public static final double MISSILE_TRIGGER_RADIUS = 1.40;
	public static final double MISSILE_BLAST_RADIUS = 3.8;
	public static final float MISSILE_MAX_DAMAGE = 6.5f;
	public static final float MISSILE_MIN_DAMAGE = 3.2f;
	public static final int MISSILE_STUN_TICKS = 10;

	public static final int UAV_FLIGHT_TICKS = 75;
	public static final int UAV_STRIKE_1_AGE = 22;
	public static final int UAV_STRIKE_2_AGE = 44;
	public static final int UAV_DIVE_AGE = 58;
	public static final double UAV_ALTITUDE = 3.6;
	public static final double UAV_ORBIT_RADIUS = 3.3;
	public static final double UAV_SCAN_RADIUS = 13.0;
	public static final double UAV_DIVE_SPEED = 0.70;
	public static final double UAV_BOMB_RADIUS = 2.6;
	public static final float UAV_BOMB_DAMAGE = 2.5f;
	public static final double UAV_DIVE_RADIUS = 3.6;
	public static final float UAV_DIVE_DAMAGE = 4.5f;
	public static final int UAV_DIVE_STUN_TICKS = 8;

	private static boolean installed;
	private final MinecraftServer server;
	private final Map<UUID, Missile> missiles = new HashMap<>();
	private final Map<UUID, Uav> uavs = new HashMap<>();

	private static final class Missile {
		final ServerLevel level;
		final int ownerId;
		final int launchedAt;
		final int expiresAt;
		Vec3 pos;
		Vec3 dir;
		int targetId;

		Missile(ServerLevel level, int ownerId, Vec3 pos, Vec3 dir, int launchedAt, int expiresAt, int targetId) {
			this.level = level;
			this.ownerId = ownerId;
			this.pos = pos;
			this.dir = dir;
			this.launchedAt = launchedAt;
			this.expiresAt = expiresAt;
			this.targetId = targetId;
		}
	}

	private static final class Uav {
		final ServerLevel level;
		final int ownerId;
		final int launchedAt;
		final int expiresAt;
		Vec3 pos;
		Vec3 orbitCenter;
		Vec3 lockPos;
		double angleRad;
		float yawDeg;
		int strikesFired;
		int targetId;

		Uav(ServerLevel level, int ownerId, Vec3 pos, Vec3 orbitCenter, Vec3 lockPos,
				int launchedAt, int expiresAt, double angleRad, float yawDeg, int targetId) {
			this.level = level;
			this.ownerId = ownerId;
			this.pos = pos;
			this.orbitCenter = orbitCenter;
			this.lockPos = lockPos;
			this.launchedAt = launchedAt;
			this.expiresAt = expiresAt;
			this.angleRad = angleRad;
			this.yawDeg = yawDeg;
			this.targetId = targetId;
		}
	}

	public TechnicianRuntime(MinecraftServer server) { this.server = server; }

	public static void install() {
		if (installed) return;
		installed = true;
		ClassAbilityRuntimes.registerRuntime(FighterClass.TECHNICIAN, TechnicianRuntime::new);
		ContentCatalog.registerClass(new RuntimeClassSpec(FighterClass.TECHNICIAN,
				"flake.class.technician.role", "flake.class.technician.summary",
				Ability.HOMING_MISSILE, Ability.ATTACK_UAV));
	}

	@Override public List<Ability> abilities() { return List.of(Ability.HOMING_MISSILE, Ability.ATTACK_UAV); }

	@Override
	public boolean cast(ServerPlayer player, Ability ability, int now) {
		ServerLevel level = (ServerLevel) player.level();
		return switch (ability) {
			case HOMING_MISSILE -> launchMissile(player, level, now);
			case ATTACK_UAV -> launchUav(player, level, now);
			default -> false;
		};
	}

	@Override
	public int activeElapsed(UUID player, int slot, int now) {
		if (slot == 1) {
			Missile m = missiles.get(player);
			if (m == null || now >= m.expiresAt) return -1;
			return Math.clamp(now - m.launchedAt, 0, MISSILE_FLIGHT_TICKS);
		}
		if (slot == 2) {
			Uav u = uavs.get(player);
			if (u == null || now >= u.expiresAt) return -1;
			return Math.clamp(now - u.launchedAt, 0, UAV_FLIGHT_TICKS);
		}
		return -1;
	}

	private boolean launchMissile(ServerPlayer player, ServerLevel level, int now) {
		UUID ownerId = player.getUUID();
		Missile prev = missiles.remove(ownerId);
		if (prev != null) TechnicianVisuals.missileOff(prev.level, player, prev.ownerId, prev.pos);

		Vec3 look = player.getLookAngle().normalize();
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1e-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 spawnPos = player.getEyePosition().add(look.scale(0.95)).add(right.scale(0.24)).subtract(0.0, 0.12, 0.0);
		Vec3 launchDir = look.add(0.0, 0.16, 0.0).normalize();

		LivingEntity initial = findMissileTarget(player, level, spawnPos, launchDir);
		int targetId = initial == null ? -1 : initial.getId();

		Missile missile = new Missile(level, player.getId(), spawnPos, launchDir, now, now + MISSILE_FLIGHT_TICKS, targetId);
		missiles.put(ownerId, missile);

		TechnicianVisuals.launchMissile(player, spawnPos, launchDir, targetId);
		AbilityEffects.sound(level, player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.85f, 0.90f);
		AbilityEffects.sound(level, player, SoundEvents.BLAZE_SHOOT, 0.55f, 1.35f);
		return true;
	}

	private boolean launchUav(ServerPlayer player, ServerLevel level, int now) {
		UUID ownerId = player.getUUID();
		Uav prev = uavs.remove(ownerId);
		if (prev != null) TechnicianVisuals.uavOff(prev.level, player, prev.ownerId, prev.pos);

		Vec3 flatForward = AbilityMovement.flat(player);
		if (flatForward.lengthSqr() < 1e-4) flatForward = new Vec3(0.0, 0.0, 1.0);
		else flatForward = flatForward.normalize();

		LivingEntity initial = findUavTarget(player, level, player.position().add(flatForward.scale(4.5)));
		Vec3 orbitCenter = initial != null ? initial.position() : player.position().add(flatForward.scale(6.0));
		Vec3 uavPos = player.position().add(flatForward.scale(1.2)).add(0.0, 2.2, 0.0);
		double angle = Math.atan2(uavPos.z - orbitCenter.z, uavPos.x - orbitCenter.x);
		int targetId = initial == null ? -1 : initial.getId();
		Vec3 lockPos = snapToGround(level, orbitCenter);

		Uav uav = new Uav(level, player.getId(), uavPos, orbitCenter, lockPos,
				now, now + UAV_FLIGHT_TICKS, angle, player.getYRot(), targetId);
		uavs.put(ownerId, uav);

		TechnicianVisuals.launchUav(player, uavPos, lockPos, targetId);
		AbilityEffects.sound(level, player, SoundEvents.BEACON_ACTIVATE, 0.65f, 1.60f);
		AbilityEffects.sound(level, player, SoundEvents.BREEZE_SHOOT, 0.70f, 1.40f);
		return true;
	}

	@Override
	public void tick(int now) {
		tickMissiles(now);
		tickUavs(now);
	}

	private void tickMissiles(int now) {
		if (missiles.isEmpty()) return;
		for (var entry : new ArrayList<>(missiles.entrySet())) {
			UUID ownerId = entry.getKey();
			Missile m = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			if (owner == null || !owner.isAlive() || owner.isSpectator()
					|| owner.level() != m.level || DuelRuntime.blocksAbility(ownerId)) {
				TechnicianVisuals.missileOff(m.level, owner, m.ownerId, m.pos);
				missiles.remove(ownerId);
				continue;
			}
			if (now >= m.expiresAt) {
				detonateMissile(owner, m.level, m.pos);
				missiles.remove(ownerId);
				continue;
			}

			int age = now - m.launchedAt;
			LivingEntity target = findMissileTarget(owner, m.level, m.pos, m.dir);
			m.targetId = target == null ? -1 : target.getId();
			if (target != null && age >= MISSILE_BOOST_TICKS) {
				Vec3 aimPoint = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
				m.dir = steerDirection(m.dir, aimPoint.subtract(m.pos), MISSILE_TURN_BLEND);
			}

			Vec3 nextPos = m.pos.add(m.dir.scale(MISSILE_SPEED));
			var blockHit = m.level.clip(new ClipContext(m.pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			if (blockHit.getType() == HitResult.Type.BLOCK) {
				detonateMissile(owner, m.level, blockHit.getLocation());
				missiles.remove(ownerId);
				continue;
			}

			LivingEntity directVictim = findImpactVictim(owner, m.level, nextPos);
			if (directVictim != null) {
				Vec3 boomPos = directVictim.position().add(0.0, directVictim.getBbHeight() * 0.5, 0.0).lerp(nextPos, 0.35);
				detonateMissile(owner, m.level, boomPos);
				missiles.remove(ownerId);
				continue;
			}

			m.pos = nextPos;
			if (age % 2 == 0) {
				TechnicianVisuals.syncMissile(owner, m.pos, m.dir, m.targetId, m.expiresAt - now);
			}
		}
	}

	private void detonateMissile(ServerPlayer owner, ServerLevel level, Vec3 boomPos) {
		for (var victim : AbilityTargeting.candidates(owner, level, boomPos, MISSILE_BLAST_RADIUS + 0.5)) {
			Vec3 center = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
			double dist = Math.min(boomPos.distanceTo(center), boomPos.distanceTo(victim.position()));
			if (dist > MISSILE_BLAST_RADIUS) continue;

			float rawDmg = computeFalloffDamage(dist, MISSILE_BLAST_RADIUS, MISSILE_MAX_DAMAGE, MISSILE_MIN_DAMAGE);
			float dmg = CombatModifiers.skillDamage(owner, victim, rawDmg);
			victim.hurtServer(level, owner.damageSources().explosion(owner, owner), dmg);

			Vec3 pushDir = AbilityMovement.horizontalDir(boomPos, victim.position(), owner);
			double strength = 0.85 * (1.0 - (dist / (MISSILE_BLAST_RADIUS + 1.0)));
			AbilityMovement.launch(victim, pushDir.scale(strength).add(0.0, 0.52, 0.0));
			if (dist <= 2.2) Stuns.applySlowStun(victim, MISSILE_STUN_TICKS);
		}
		TechnicianVisuals.missileBoom(owner, boomPos, (float) MISSILE_BLAST_RADIUS);
		level.playSound(null, boomPos.x, boomPos.y, boomPos.z,
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.95f, 1.08f);
		level.playSound(null, boomPos.x, boomPos.y, boomPos.z,
				SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 0.65f);
	}

	private void tickUavs(int now) {
		if (uavs.isEmpty()) return;
		for (var entry : new ArrayList<>(uavs.entrySet())) {
			UUID ownerId = entry.getKey();
			Uav u = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			if (owner == null || !owner.isAlive() || owner.isSpectator()
					|| owner.level() != u.level || DuelRuntime.blocksAbility(ownerId)) {
				TechnicianVisuals.uavOff(u.level, owner, u.ownerId, u.pos);
				uavs.remove(ownerId);
				continue;
			}

			int age = now - u.launchedAt;
			LivingEntity target = findUavTarget(owner, u.level, u.orbitCenter);
			if (target != null) {
				u.targetId = target.getId();
				u.orbitCenter = u.orbitCenter.lerp(target.position(), 0.25);
				u.lockPos = snapToGround(u.level, target.position());
			} else {
				u.targetId = -1;
				u.lockPos = snapToGround(u.level, u.orbitCenter);
			}

			if (now >= u.expiresAt) {
				detonateUavDive(owner, u.level, u.pos);
				uavs.remove(ownerId);
				continue;
			}

			if (age < UAV_DIVE_AGE) {
				u.angleRad += 0.19;
				Vec3 desired = computeUavPatrolPos(u.orbitCenter, u.angleRad, UAV_ORBIT_RADIUS, u.lockPos.y, UAV_ALTITUDE);
				Vec3 prevPos = u.pos;
				u.pos = u.pos.lerp(desired, 0.35);
				Vec3 vel = u.pos.subtract(prevPos);
				if (vel.horizontalDistanceSqr() > 1e-6) {
					u.yawDeg = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
				}

				if ((age >= UAV_STRIKE_1_AGE && u.strikesFired == 0)
						|| (age >= UAV_STRIKE_2_AGE && u.strikesFired == 1)) {
					u.strikesFired++;
					fireUavBomb(owner, u);
				}
			} else {
				// Terminal Kamikaze Dive toward locked target/ground
				Vec3 diveGoal = target != null
						? target.position().add(0.0, target.getBbHeight() * 0.45, 0.0)
						: u.lockPos.add(0.0, 0.35, 0.0);
				Vec3 diveDir = diveGoal.subtract(u.pos);
				double distToGoal = diveDir.length();
				if (distToGoal > 1e-5) {
					Vec3 stepDir = diveDir.scale(1.0 / distToGoal);
					u.yawDeg = (float) Math.toDegrees(Math.atan2(-stepDir.x, stepDir.z));
					Vec3 nextPos = u.pos.add(stepDir.scale(Math.min(UAV_DIVE_SPEED, distToGoal)));

					var hit = u.level.clip(new ClipContext(u.pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
					if (hit.getType() == HitResult.Type.BLOCK || distToGoal <= 1.35) {
						Vec3 boomPos = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : diveGoal;
						detonateUavDive(owner, u.level, boomPos);
						uavs.remove(ownerId);
						continue;
					}
					u.pos = nextPos;
				}
			}

			if (age % 2 == 0) {
				TechnicianVisuals.syncUav(owner, u.pos, u.lockPos, u.targetId, u.yawDeg, u.expiresAt - now);
			}
		}
	}

	private void fireUavBomb(ServerPlayer owner, Uav u) {
		Vec3 strikePos = u.lockPos;
		for (var victim : AbilityTargeting.candidates(owner, u.level, strikePos, UAV_BOMB_RADIUS + 0.4)) {
			double dist = strikePos.distanceTo(victim.position());
			if (dist > UAV_BOMB_RADIUS) continue;
			float dmg = CombatModifiers.skillDamage(owner, victim, UAV_BOMB_DAMAGE);
			victim.hurtServer(u.level, owner.damageSources().explosion(owner, owner), dmg);
			Vec3 push = AbilityMovement.horizontalDir(strikePos, victim.position(), owner).scale(0.45).add(0.0, 0.36, 0.0);
			AbilityMovement.launch(victim, push);
		}
		TechnicianVisuals.uavStrike(owner, u.pos, strikePos, u.targetId);
		u.level.playSound(null, strikePos.x, strikePos.y, strikePos.z,
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.72f, 1.35f);
	}

	private void detonateUavDive(ServerPlayer owner, ServerLevel level, Vec3 boomPos) {
		for (var victim : AbilityTargeting.candidates(owner, level, boomPos, UAV_DIVE_RADIUS + 0.5)) {
			double dist = boomPos.distanceTo(victim.position().add(0.0, victim.getBbHeight() * 0.4, 0.0));
			if (dist > UAV_DIVE_RADIUS) continue;
			float rawDmg = computeFalloffDamage(dist, UAV_DIVE_RADIUS, UAV_DIVE_DAMAGE, 2.4f);
			float dmg = CombatModifiers.skillDamage(owner, victim, rawDmg);
			victim.hurtServer(level, owner.damageSources().explosion(owner, owner), dmg);
			Vec3 push = AbilityMovement.horizontalDir(boomPos, victim.position(), owner).scale(0.82).add(0.0, 0.56, 0.0);
			AbilityMovement.launch(victim, push);
			if (dist <= 2.3) Stuns.applySlowStun(victim, UAV_DIVE_STUN_TICKS);
		}
		TechnicianVisuals.uavBoom(owner, boomPos);
		level.playSound(null, boomPos.x, boomPos.y, boomPos.z,
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.95f, 0.95f);
	}

	private static LivingEntity findMissileTarget(ServerPlayer owner, ServerLevel level, Vec3 pos, Vec3 dir) {
		List<LivingEntity> candidates = AbilityTargeting.candidates(owner, level, pos, MISSILE_SEEK_RADIUS);
		return candidates.stream()
				.min(Comparator.comparingDouble(e -> {
					Vec3 to = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0).subtract(pos);
					double d = to.length();
					double dot = d > 1e-4 ? dir.dot(to.scale(1.0 / d)) : 1.0;
					return d * (dot < 0.0 ? 1.8 : 1.0 - dot * 0.25);
				}))
				.orElse(null);
	}

	private static LivingEntity findImpactVictim(ServerPlayer owner, ServerLevel level, Vec3 pos) {
		for (var e : AbilityTargeting.candidates(owner, level, pos, MISSILE_TRIGGER_RADIUS + 1.2)) {
			Vec3 center = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0);
			if (center.distanceToSqr(pos) <= MISSILE_TRIGGER_RADIUS * MISSILE_TRIGGER_RADIUS
					|| e.position().distanceToSqr(pos) <= MISSILE_TRIGGER_RADIUS * MISSILE_TRIGGER_RADIUS) {
				return e;
			}
		}
		return null;
	}

	private static LivingEntity findUavTarget(ServerPlayer owner, ServerLevel level, Vec3 around) {
		return AbilityTargeting.candidates(owner, level, around, UAV_SCAN_RADIUS).stream()
				.min(Comparator.comparingDouble(e -> e.position().distanceToSqr(around)))
				.orElse(null);
	}

	private static Vec3 snapToGround(ServerLevel level, Vec3 pos) {
		Vec3 top = new Vec3(pos.x, pos.y + 3.0, pos.z);
		Vec3 bottom = new Vec3(pos.x, pos.y - 8.0, pos.z);
		var hit = level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : pos;
	}

	static Vec3 steerDirection(Vec3 currentDir, Vec3 toTarget, double maxTurnBlend) {
		if (toTarget.lengthSqr() < 1e-6) return currentDir.normalize();
		Vec3 targetDir = toTarget.normalize();
		return currentDir.normalize().lerp(targetDir, Math.clamp(maxTurnBlend, 0.0, 1.0)).normalize();
	}

	static float computeFalloffDamage(double dist, double blastRadius, float maxDamage, float minDamage) {
		double t = Math.clamp(dist / Math.max(blastRadius, 0.01), 0.0, 1.0);
		return (float) (maxDamage - (maxDamage - minDamage) * t);
	}

	static Vec3 computeUavPatrolPos(Vec3 orbitCenter, double orbitAngleRad, double radius, double groundY, double altitude) {
		return new Vec3(
				orbitCenter.x + Math.cos(orbitAngleRad) * radius,
				groundY + altitude,
				orbitCenter.z + Math.sin(orbitAngleRad) * radius);
	}

	@Override
	public void reset(ServerPlayer player) {
		UUID id = player.getUUID();
		Missile m = missiles.remove(id);
		if (m != null) TechnicianVisuals.missileOff(m.level, player, m.ownerId, m.pos);
		Uav u = uavs.remove(id);
		if (u != null) TechnicianVisuals.uavOff(u.level, player, u.ownerId, u.pos);
	}

	@Override
	public void clear() {
		for (var e : missiles.entrySet()) {
			Missile m = e.getValue();
			TechnicianVisuals.missileOff(m.level, server.getPlayerList().getPlayer(e.getKey()), m.ownerId, m.pos);
		}
		missiles.clear();
		for (var e : uavs.entrySet()) {
			Uav u = e.getValue();
			TechnicianVisuals.uavOff(u.level, server.getPlayerList().getPlayer(e.getKey()), u.ownerId, u.pos);
		}
		uavs.clear();
	}
}
