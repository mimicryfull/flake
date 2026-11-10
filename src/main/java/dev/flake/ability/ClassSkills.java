package dev.flake.ability;

import dev.flake.ability.AbilitySlots.Ability;
import dev.flake.duel.CombatFeedback;
import dev.flake.duel.Cooldowns;
import dev.flake.duel.CrystalClaw;
import dev.flake.duel.DuelRuntime;
import dev.flake.duel.GhoulAnimation;
import dev.flake.duel.ShockwaveVisuals;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Active skills of Бастіон, Упир and Підривник (two each). Server-authoritative;
 * Bastion and Ghoul send confirmed state to the custom VFX pass. Hits affect duel opponents (or test dummies outside a duel).
 */
public final class ClassSkills {
	private static final Map<UUID, int[]> READY_AT = new HashMap<>();
	private static final Map<UUID, int[]> LAST_COOLDOWN = new HashMap<>();
	private static final Map<UUID, Integer> BULWARK_UNTIL = new HashMap<>(), BLOODLUST_UNTIL = new HashMap<>();
	private static final Map<UUID, Mine> MINES = new HashMap<>();
	private static final Identifier KB_MOD = Identifier.fromNamespaceAndPath("flake", "bulwark_kb");
	/** Heals the Упир for this share of the melee damage dealt while Bloodlust is on. */
	public static final float BLOODLUST_STEAL = 0.18f;
	public static final int BULWARK_TICKS = 80;
	public static final int BLOODLUST_TICKS = 100;
	/** Proximity mine arming delay and lifetime, in ticks. */
	public static final int MINE_ARM_TICKS = 20;
	public static final int MINE_LIFE_TICKS = 400;
	public static final double MINE_TRIGGER_RADIUS = 1.7;
	/** Thorns passive: reflect share of incoming damage, with a documented minimum (see locale desc). */
	public static final float THORNS_SHARE = 0.2f;
	public static final float THORNS_MIN_DAMAGE = 0.5f;

	private record Mine(ServerLevel level, Vec3 pos, int armedAt, int expiresAt) {}

	private ClassSkills() {}

	public static int remaining(UUID id, int slot, int now) {
		var ready = READY_AT.get(id);
		return ready == null ? 0 : Math.max(0, ready[slot - 1] - now);
	}

	/** Actual cooldown length stored at cast time (includes Melting Amethyst), for the HUD ring. */
	public static int cooldownTotal(UUID id, int slot, int now) {
		var totals = LAST_COOLDOWN.get(id);
		return totals == null ? 0 : Math.max(0, totals[slot - 1]);
	}

	public static int activeElapsed(UUID id, int slot, int now) {
		if (slot != 2) return -1;
		Integer bulwark = BULWARK_UNTIL.get(id);
		if (bulwark != null && bulwark > now) return Math.clamp(BULWARK_TICKS - (bulwark - now), 0, BULWARK_TICKS);
		Integer bloodlust = BLOODLUST_UNTIL.get(id);
		if (bloodlust != null && bloodlust > now) return Math.clamp(BLOODLUST_TICKS - (bloodlust - now), 0, BLOODLUST_TICKS);
		return -1;
	}

	public static boolean active(Map<UUID, Integer> map, UUID id) { return map.containsKey(id); }

	/** Remaining Bulwark ticks for HUD status; 0 when inactive. */
	public static int bulwarkRemaining(UUID id, int now) {
		Integer until = BULWARK_UNTIL.get(id);
		return until == null ? 0 : Math.max(0, until - now);
	}

	/** Remaining Bloodlust ticks for HUD status; 0 when inactive. */
	public static int bloodlustRemaining(UUID id, int now) {
		Integer until = BLOODLUST_UNTIL.get(id);
		return until == null ? 0 : Math.max(0, until - now);
	}

	/** Total Bulwark/Bloodlust duration in ticks, for HUD progress bars. */
	public static int bulwarkTicks() { return BULWARK_TICKS; }
	public static int bloodlustTicks() { return BLOODLUST_TICKS; }

	/**
	 * Stun cancels ongoing class buffs instead of leaving stale effects/state behind.
	 * Vanilla effects granted by the buff are removed too, but only those that match the buff's own
	 * amplifier and cannot outlive it — a stronger or longer effect from another source is kept.
	 */
	public static void cancelBuffs(ServerPlayer player) {
		var id = player.getUUID();
		int now = player.level().getServer().getTickCount();
		int bulwarkLeft = bulwarkRemaining(id, now);
		if (BULWARK_UNTIL.remove(id) != null) {
			ShockwaveVisuals.bulwark(player, false);
			var kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (kb != null) kb.removeModifier(KB_MOD);
			removeOwnEffect(player, MobEffects.RESISTANCE, 1, bulwarkLeft);
			removeOwnEffect(player, MobEffects.SLOWNESS, 0, bulwarkLeft);
			boolean ownAbsorption = removeOwnEffect(player, MobEffects.ABSORPTION, 0, bulwarkLeft);
			// Only clear the shield Bulwark itself granted; a stronger absorption source is left untouched.
			if (ownAbsorption && player.getAbsorptionAmount() <= 4.01f) player.setAbsorptionAmount(0f);
		}
		int bloodlustLeft = bloodlustRemaining(id, now);
		if (BLOODLUST_UNTIL.remove(id) != null) {
			ShockwaveVisuals.bloodlust(player, false);
			removeOwnEffect(player, MobEffects.SPEED, 0, bloodlustLeft);
		}
	}

	private static boolean removeOwnEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
			int amplifier, int buffTicksLeft) {
		if (buffTicksLeft <= 0) return false;
		var active = player.getEffect(effect);
		if (active == null || active.getAmplifier() != amplifier) return false;
		// Same duration as the buff means the buff granted it; a longer one came from elsewhere.
		if (active.getDuration() > buffTicksLeft + 2) return false;
		return player.removeEffect(effect);
	}

	/** Returns 1 on cast. A cast that finds no valid target does not spend the cooldown. */
	public static int cast(ServerPlayer player, Ability ability, int slot) {
		int now = player.level().getServer().getTickCount();
		int remaining = remaining(player.getUUID(), slot, now);
		if (DuelRuntime.blocksAbility(player.getUUID())) {
			CombatFeedback.blocked(player);
			return 0;
		}
		if (remaining > 0) {
			CombatFeedback.onCooldown(player, remaining);
			return 0;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			CombatFeedback.unavailable(player);
			return 0;
		}
		boolean ok = switch (ability) {
			case SHIELD_RAM -> shieldRam(player, level);
			case BULWARK -> bulwark(player, level, now);
			case BLOOD_LUNGE -> bloodLunge(player, level);
			case BLOODLUST -> bloodlust(player, level, now);
			case PROXIMITY_MINE -> mine(player, level, now);
			case BLAST_JUMP -> blastJump(player, level);
			default -> false;
		};
		if (!ok) {
			CombatFeedback.noTarget(player);
			return 0;
		}
		int cooldown = Cooldowns.ticks(player, ability.cooldownTicks());
		READY_AT.computeIfAbsent(player.getUUID(), k -> new int[2])[slot - 1] = now + cooldown;
		LAST_COOLDOWN.computeIfAbsent(player.getUUID(), k -> new int[2])[slot - 1] = cooldown;
		return 1;
	}

	// ---- Бастіон ---------------------------------------------------------------------------------------
	private static boolean shieldRam(ServerPlayer p, ServerLevel level) {
		Vec3 dir = flat(p);
		boolean shield = p.getOffhandItem().is(Items.SHIELD);
		launch(p, dir.scale(1.15).add(0, 0.18, 0));
		var target = target(p, 4.5, 0.65, false);
		sound(level, p, shield ? SoundEvents.MACE_SMASH_GROUND : SoundEvents.ANVIL_LAND, shield ? 0.9f : 0.6f, shield ? 0.72f : 0.8f);
		LivingEntity confirmed = null;
		if (target != null) {
			float damage = CrystalClaw.skillDamage(p, p.getOffhandItem().is(Items.SHIELD) ? 6.0f : 4.0f); // a raised shield hits harder
			if (target.hurtServer(level, p.damageSources().playerAttack(p), damage)) confirmed = target;
			launch(target, dir.scale(1.2).add(0, 0.35, 0));
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 2, false, false, true));
		}
		// No client-side target prediction: only a successful server hit gets the contact climax.
		ShockwaveVisuals.shieldRam(p, confirmed, dir, shield);
		return true;
	}

	private static boolean bulwark(ServerPlayer p, ServerLevel level, int now) {
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, BULWARK_TICKS, 1, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BULWARK_TICKS, 0, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BULWARK_TICKS, 0, false, false, true));
		p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 4.0f));
		var kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) { kb.removeModifier(KB_MOD); kb.addTransientModifier(new AttributeModifier(KB_MOD, 1.0, AttributeModifier.Operation.ADD_VALUE)); }
		BULWARK_UNTIL.put(p.getUUID(), now + BULWARK_TICKS);
		ShockwaveVisuals.bulwark(p, true);
		sound(level, p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35f, 0.6f);
		return true;
	}

	// ---- Упир ------------------------------------------------------------------------------------------
	private static boolean bloodLunge(ServerPlayer p, ServerLevel level) {
		var target = target(p, 9.0, 0.82, true);
		if (target == null) return false;
		Vec3 dir = horizontalDir(p.position(), target.position(), p);
		launch(p, dir.scale(1.7).add(0, 0.22, 0));
		boolean hit = target.hurtServer(level, p.damageSources().playerAttack(p), CrystalClaw.skillDamage(p, 4.0f));
		p.heal(1.5f);
		ShockwaveVisuals.bloodLunge(p, target, dir, hit);
		sound(level, p, SoundEvents.MACE_SMASH_AIR, 0.6f, 1.4f);
		return true;
	}

	private static boolean bloodlust(ServerPlayer p, ServerLevel level, int now) {
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, BLOODLUST_TICKS, 0, false, false, true));
		BLOODLUST_UNTIL.put(p.getUUID(), now + BLOODLUST_TICKS);
		ShockwaveVisuals.bloodlust(p, true);
		sound(level, p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3f, 1.8f);
		return true;
	}

	// ---- Підривник -------------------------------------------------------------------------------------
	private static boolean mine(ServerPlayer p, ServerLevel level, int now) {
		MINES.put(p.getUUID(), new Mine(level, p.position(), now + MINE_ARM_TICKS, now + MINE_LIFE_TICKS)); // one mine at a time
		level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.2, 0.05, 0.2, 0.01);
		sound(level, p, SoundEvents.ANVIL_LAND, 0.4f, 1.6f);
		return true;
	}

	private static boolean blastJump(ServerPlayer p, ServerLevel level) {
		Vec3 origin = p.position();
		launch(p, flat(p).scale(1.1).add(0, 1.0, 0));
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, true));
		p.fallDistance = 0;
		for (var other : candidates(p, level, origin, 3.5)) {
			if (other.position().distanceToSqr(origin) > 3.5 * 3.5) continue;
			other.hurtServer(level, p.damageSources().explosion(p, p), CrystalClaw.skillDamage(p, 3.0f));
			launch(other, horizontalDir(origin, other.position(), p).scale(1.2).add(0, 0.5, 0));
		}
		level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y + 0.2, origin.z, 2, 0.3, 0.1, 0.3, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y + 0.2, origin.z, 14, 0.5, 0.1, 0.5, 0.05);
		sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.3f);
		return true;
	}

	// ---- helpers ---------------------------------------------------------------------------------------
	private static Vec3 flat(ServerPlayer p) {
		Vec3 look = p.getLookAngle().multiply(1, 0, 1);
		return look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
	}

	private static Vec3 horizontalDir(Vec3 from, Vec3 to, ServerPlayer fallback) {
		Vec3 diff = to.subtract(from).multiply(1, 0, 1);
		return diff.lengthSqr() < 1e-4 ? flat(fallback) : diff.normalize();
	}

	private static void launch(LivingEntity e, Vec3 v) {
		e.setDeltaMovement(v);
		if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
		else e.syncVelocity = true;
	}

	private static void sound(ServerLevel level, ServerPlayer p, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void ring(ServerLevel level, ServerPlayer p, net.minecraft.core.particles.SimpleParticleType type, int count, double radius) {
		for (int i = 0; i < count; i++) {
			double a = i * Math.PI * 2 / count;
			level.sendParticles(type, p.getX() + Math.cos(a) * radius, p.getY() + 0.2 + (i % 3) * 0.6, p.getZ() + Math.sin(a) * radius, 1, 0, 0.1, 0, 0.02);
		}
	}

	private static List<LivingEntity> candidates(ServerPlayer p, ServerLevel level, Vec3 centre, double radius) {
		var list = new ArrayList<LivingEntity>();
		boolean inDuel = DuelRuntime.fighting(p.getUUID());
		for (var other : level.players()) {
			if (other == p || !other.isAlive()) continue;
			if (!inDuel || DuelRuntime.opponents(p.getUUID(), other.getUUID())) list.add(other);
		}
		if (!inDuel) {
			var box = new AABB(centre.x - radius - 1, centre.y - radius - 1, centre.z - radius - 1,
					centre.x + radius + 1, centre.y + radius + 1, centre.z + radius + 1);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.entityTags().contains("flake_test_dummy"))) {
				list.add(mob);
			}
		}
		return list;
	}

	/** Closest duel opponent (or test dummy outside a duel) inside a view cone; optionally needs line of sight. */
	private static LivingEntity target(ServerPlayer p, double range, double minCos, boolean lineOfSight) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		LivingEntity best = null;
		double bestCos = minCos;
		for (var other : candidates(p, level, p.position(), range)) {
			Vec3 aim = other.position().add(0, other.getBbHeight() * 0.5, 0), delta = aim.subtract(eye);
			double dist = delta.length();
			if (dist > range || dist < 0.3) continue;
			double cos = delta.scale(1 / dist).dot(look);
			if (cos < bestCos) continue;
			if (lineOfSight && level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
			best = other; bestCos = cos;
		}
		return best;
	}

	// ---- lifecycle -------------------------------------------------------------------------------------
	public static void reset(ServerPlayer player) {
		var id = player.getUUID();
		READY_AT.remove(id); LAST_COOLDOWN.remove(id); MINES.remove(id);
		if (BLOODLUST_UNTIL.remove(id) != null) ShockwaveVisuals.bloodlust(player, false);
		if (BULWARK_UNTIL.remove(id) != null) ShockwaveVisuals.bulwark(player, false);
		var kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) kb.removeModifier(KB_MOD);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			for (var entry : new ArrayList<>(BULWARK_UNTIL.entrySet())) {
				var player = server.getPlayerList().getPlayer(entry.getKey());
				if (entry.getValue() > now && player != null && player.isAlive() && !DuelRuntime.blocksAbility(entry.getKey())) continue;
				BULWARK_UNTIL.remove(entry.getKey());
				if (player != null) {
					ShockwaveVisuals.bulwark(player, false);
					var kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE); if (kb != null) kb.removeModifier(KB_MOD);
				}
			}
			BLOODLUST_UNTIL.entrySet().removeIf(e -> {
				boolean end = e.getValue() <= now || DuelRuntime.blocksAbility(e.getKey());
				var player = end ? server.getPlayerList().getPlayer(e.getKey()) : null;
				if (player != null) ShockwaveVisuals.bloodlust(player, false);
				return end;
			});
			for (var entry : new ArrayList<>(MINES.entrySet())) {
				var mine = entry.getValue();
				var owner = server.getPlayerList().getPlayer(entry.getKey());
				if (owner == null || now >= mine.expiresAt || DuelRuntime.blocksAbility(entry.getKey())) { MINES.remove(entry.getKey()); continue; }
				if (now % 20 == 0) mine.level.sendParticles(ParticleTypes.ASH, mine.pos.x, mine.pos.y + 0.1, mine.pos.z, 2, 0.2, 0.02, 0.2, 0);
				if (now < mine.armedAt) continue;
				for (var victim : candidates(owner, mine.level, mine.pos, 2.0)) {
					if (victim.position().distanceToSqr(mine.pos) > MINE_TRIGGER_RADIUS * MINE_TRIGGER_RADIUS) continue;
					victim.hurtServer(mine.level, owner.damageSources().explosion(owner, owner), CrystalClaw.skillDamage(owner, 6.0f));
					launch(victim, horizontalDir(mine.pos, victim.position(), owner).scale(0.9).add(0, 0.7, 0));
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1, false, true, true));
					mine.level.sendParticles(ParticleTypes.EXPLOSION, mine.pos.x, mine.pos.y + 0.3, mine.pos.z, 2, 0.3, 0.1, 0.3, 0);
					mine.level.sendParticles(ParticleTypes.LARGE_SMOKE, mine.pos.x, mine.pos.y + 0.3, mine.pos.z, 16, 0.5, 0.2, 0.5, 0.05);
					mine.level.playSound(null, mine.pos.x, mine.pos.y, mine.pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
					MINES.remove(entry.getKey());
					break;
				}
			}
		});
		// Lifesteal (Bloodlust and the Leech passive) and reflect (Thorns passive) share one hook.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (blocked || taken <= 0 || reflecting) return;
			if (victim instanceof ServerPlayer hit && bulwarkRemaining(hit.getUUID(), hit.level().getServer().getTickCount()) > 0)
				ShockwaveVisuals.bulwarkHit(hit, source.getSourcePosition());
			if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker && !DuelRuntime.blocksAbility(attacker.getUUID())) {
				float heal = 0;
				if (BLOODLUST_UNTIL.containsKey(attacker.getUUID())) heal += taken * BLOODLUST_STEAL;
				if (DuelRuntime.passiveOf(attacker.getUUID()) == dev.flake.build.Loadout.PassiveItem.LEECH) heal += taken * 0.08f;
				if (heal > 0) {
					float before = attacker.getHealth();
					attacker.heal(heal); // Preserve the single combined Bloodlust + Leech heal and its exact formula.
					boolean bloodlust = bloodlustRemaining(attacker.getUUID(), attacker.level().getServer().getTickCount()) > 0;
					if (GhoulAnimation.confirmedHealing(bloodlust, taken, before, attacker.getHealth()))
						ShockwaveVisuals.bloodSiphon(attacker, victim, attacker.getHealth() - before);
					else if (!bloodlust)
						attacker.level().getServer().getLevel(attacker.level().dimension()).sendParticles(ParticleTypes.CRIMSON_SPORE,
								attacker.getX(), attacker.getY() + 1.2, attacker.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
				}
			}
			if (victim instanceof ServerPlayer hit && source.getEntity() instanceof ServerPlayer attacker && attacker != hit
					&& DuelRuntime.opponents(hit.getUUID(), attacker.getUUID())
					&& DuelRuntime.passiveOf(hit.getUUID()) == dev.flake.build.Loadout.PassiveItem.THORNS) {
				reflecting = true; // guard against reflect → reflect loops
				try {
					attacker.hurtServer((ServerLevel) attacker.level(), hit.damageSources().thorns(hit),
							Math.max(THORNS_MIN_DAMAGE, taken * THORNS_SHARE));
				}
				finally { reflecting = false; }
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}

	private static boolean reflecting;
	public static List<Ability> skillsOf(dev.flake.build.Loadout.FighterClass cls) { return List.of(AbilitySlots.resolve(cls, 1), AbilitySlots.resolve(cls, 2)); }
}
