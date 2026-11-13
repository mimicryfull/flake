package dev.flake.features.shaman;

import dev.flake.combat.CombatFeedback;
import dev.flake.combat.CombatModifiers;
import dev.flake.combat.ability.AbilityEffects;
import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.combat.ability.ClassAbilityRuntime;
import dev.flake.combat.ability.ClassAbilityRuntimes;
import dev.flake.combat.ability.RuntimeClassSpec;
import dev.flake.content.ContentCatalog;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.Loadout.FighterClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shaman owns its Totem of Pain zone and Spirit Ward ally aura mechanics. */
public final class ShamanRuntime implements ClassAbilityRuntime {
	public static final int TOTEM_LIFE_TICKS = 120;
	public static final double TOTEM_RADIUS = 3.5;
	public static final int TOTEM_TICK_INTERVAL = 20;
	public static final float TOTEM_DAMAGE = 1.0f;
	public static final double TOTEM_SLOW_MULTIPLIER = -0.10;

	public static final int WARD_TICKS = 80;
	public static final double WARD_RADIUS = 4.0;
	public static final int WARD_HEAL_INTERVAL = 20;
	public static final float WARD_HEAL_AMOUNT = 0.5f;
	public static final double WARD_SPEED_MULTIPLIER = 0.10;

	private static final Identifier TOTEM_SLOW_MOD = Identifier.fromNamespaceAndPath("flake", "shaman_totem_slow");
	private static final Identifier WARD_SPEED_MOD = Identifier.fromNamespaceAndPath("flake", "shaman_ward_speed");

	private static boolean installed;
	private final MinecraftServer server;
	private final Map<UUID, Totem> totems = new HashMap<>();
	private final Map<UUID, Ward> wards = new HashMap<>();

	private static final class Totem {
		final ServerLevel level;
		final Vec3 pos;
		final int ownerId;
		final int placedAt;
		final int expiresAt;
		int nextDamageAt;
		final Set<UUID> slowedIds = new HashSet<>();

		Totem(ServerLevel level, Vec3 pos, int ownerId, int placedAt, int expiresAt, int nextDamageAt) {
			this.level = level;
			this.pos = pos;
			this.ownerId = ownerId;
			this.placedAt = placedAt;
			this.expiresAt = expiresAt;
			this.nextDamageAt = nextDamageAt;
		}
	}

	private static final class Ward {
		final ServerLevel level;
		final int startedAt;
		final int expiresAt;
		int nextHealAt;
		final Set<UUID> buffedIds = new HashSet<>();

		Ward(ServerLevel level, int startedAt, int expiresAt, int nextHealAt) {
			this.level = level;
			this.startedAt = startedAt;
			this.expiresAt = expiresAt;
			this.nextHealAt = nextHealAt;
		}
	}

	public ShamanRuntime(MinecraftServer server) { this.server = server; }

	public static void install() {
		if (installed) return;
		installed = true;
		ClassAbilityRuntimes.registerRuntime(FighterClass.SHAMAN, ShamanRuntime::new);
		ContentCatalog.registerClass(new RuntimeClassSpec(FighterClass.SHAMAN,
				"flake.class.shaman.role", "flake.class.shaman.summary", Ability.TOTEM_OF_PAIN, Ability.SPIRIT_WARD));
	}

	@Override public List<Ability> abilities() { return List.of(Ability.TOTEM_OF_PAIN, Ability.SPIRIT_WARD); }

	@Override public boolean cast(ServerPlayer player, Ability ability, int now) {
		ServerLevel level = (ServerLevel) player.level();
		return switch (ability) {
			case TOTEM_OF_PAIN -> placeTotem(player, level, now);
			case SPIRIT_WARD -> spiritWard(player, level, now);
			default -> false;
		};
	}

	public static final double TOTEM_MAX_GROUND_DROP = 0.55;

	static Vec3 resolveTotemGroundPos(Vec3 origin, boolean onGround, boolean hitGround, double hitY) {
		if (!hitGround) return null;
		double drop = origin.y - hitY;
		if (!onGround && (drop < -0.15 || drop > TOTEM_MAX_GROUND_DROP)) return null;
		if (drop < -0.5 || drop > 1.5) return null;
		return new Vec3(origin.x, hitY, origin.z);
	}

	private static Vec3 findTotemGroundPos(ServerPlayer player, ServerLevel level) {
		Vec3 origin = player.position();
		BlockHitResult hit = level.clip(new ClipContext(
				origin.add(0.0, 0.35, 0.0),
				origin.subtract(0.0, 1.5, 0.0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		boolean hitGround = hit.getType() == HitResult.Type.BLOCK;
		return resolveTotemGroundPos(origin, player.onGround(), hitGround, hitGround ? hit.getLocation().y : origin.y);
	}

	private boolean placeTotem(ServerPlayer player, ServerLevel level, int now) {
		Vec3 pos = findTotemGroundPos(player, level);
		if (pos == null) {
			CombatFeedback.groundOnly(player, Ability.TOTEM_OF_PAIN);
			return false;
		}
		UUID ownerId = player.getUUID();
		Totem previous = totems.remove(ownerId);
		if (previous != null) clearTotemSlows(previous.slowedIds);

		Totem totem = new Totem(level, pos, player.getId(), now, now + TOTEM_LIFE_TICKS, now + TOTEM_TICK_INTERVAL);
		totems.put(ownerId, totem);
		applyTotemZone(player, totem, false, new HashSet<>());

		ShamanVisuals.totem(player, pos);
		AbilityEffects.sound(level, player, SoundEvents.DEEPSLATE_BREAK, 0.65f, 0.75f);
		AbilityEffects.sound(level, player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.55f, 0.72f);
		return true;
	}

	private boolean spiritWard(ServerPlayer player, ServerLevel level, int now) {
		UUID ownerId = player.getUUID();
		Ward previous = wards.remove(ownerId);
		if (previous != null) clearWardSpeeds(previous.buffedIds);

		Ward ward = new Ward(level, now, now + WARD_TICKS, now + WARD_HEAL_INTERVAL);
		wards.put(ownerId, ward);
		applyWardAura(ownerId, player, ward, false, new HashSet<>());

		ShamanVisuals.spiritWard(player);
		AbilityEffects.sound(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.70f, 1.15f);
		return true;
	}

	@Override public int activeElapsed(UUID player, int slot, int now) {
		if (slot == 1) {
			Totem totem = totems.get(player);
			if (totem == null || now >= totem.expiresAt) return -1;
			return Math.clamp(now - totem.placedAt, 0, TOTEM_LIFE_TICKS);
		}
		if (slot == 2) {
			Ward ward = wards.get(player);
			if (ward == null || now >= ward.expiresAt) return -1;
			return Math.clamp(now - ward.startedAt, 0, WARD_TICKS);
		}
		return -1;
	}

	@Override public void tick(int now) {
		tickTotems(now);
		tickWards(now);
	}

	private void tickTotems(int now) {
		if (totems.isEmpty()) return;
		Set<UUID> candidatesForClear = new HashSet<>();

		for (var entry : new ArrayList<>(totems.entrySet())) {
			UUID ownerId = entry.getKey();
			Totem totem = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			boolean blocked = owner == null || !owner.isAlive() || owner.level() != totem.level || DuelRuntime.blocksAbility(ownerId);

			if (blocked || now > totem.expiresAt) {
				candidatesForClear.addAll(totem.slowedIds);
				totems.remove(ownerId);
				ShamanVisuals.totemOff(totem.level, owner, totem.ownerId, totem.pos);
				continue;
			}

			boolean damageTick = now >= totem.nextDamageAt;
			if (damageTick) totem.nextDamageAt += TOTEM_TICK_INTERVAL;

			applyTotemZone(owner, totem, damageTick, candidatesForClear);

			if (now >= totem.expiresAt) {
				candidatesForClear.addAll(totem.slowedIds);
				totems.remove(ownerId);
				ShamanVisuals.totemOff(totem.level, owner, totem.ownerId, totem.pos);
			}
		}

		if (!candidatesForClear.isEmpty()) clearTotemSlows(candidatesForClear);
	}

	private void applyTotemZone(ServerPlayer owner, Totem totem, boolean damageTick, Set<UUID> candidatesForClear) {
		double radiusSq = TOTEM_RADIUS * TOTEM_RADIUS;
		Set<UUID> insideNow = new HashSet<>();
		for (LivingEntity victim : AbilityTargeting.candidates(owner, totem.level, totem.pos, TOTEM_RADIUS)) {
			if (victim.position().distanceToSqr(totem.pos) > radiusSq) continue;
			UUID victimId = victim.getUUID();
			insideNow.add(victimId);
			ensureSpeedModifier(victim, TOTEM_SLOW_MOD, TOTEM_SLOW_MULTIPLIER);
			if (damageTick) {
				float dmg = CombatModifiers.skillDamage(owner, victim, TOTEM_DAMAGE);
				victim.hurtServer(totem.level, owner.damageSources().indirectMagic(null, owner), dmg);
			}
		}
		for (UUID prevId : totem.slowedIds) {
			if (!insideNow.contains(prevId)) candidatesForClear.add(prevId);
		}
		totem.slowedIds.clear();
		totem.slowedIds.addAll(insideNow);
	}

	private void tickWards(int now) {
		if (wards.isEmpty()) return;
		Set<UUID> candidatesForClear = new HashSet<>();

		for (var entry : new ArrayList<>(wards.entrySet())) {
			UUID ownerId = entry.getKey();
			Ward ward = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			boolean blocked = owner == null || !owner.isAlive() || owner.level() != ward.level || DuelRuntime.blocksAbility(ownerId);

			if (blocked || now > ward.expiresAt) {
				candidatesForClear.addAll(ward.buffedIds);
				wards.remove(ownerId);
				continue;
			}

			boolean healTick = now >= ward.nextHealAt;
			if (healTick) ward.nextHealAt += WARD_HEAL_INTERVAL;

			applyWardAura(ownerId, owner, ward, healTick, candidatesForClear);

			if (now >= ward.expiresAt) {
				candidatesForClear.addAll(ward.buffedIds);
				wards.remove(ownerId);
			}
		}

		if (!candidatesForClear.isEmpty()) clearWardSpeeds(candidatesForClear);
	}

	private void applyWardAura(UUID ownerId, ServerPlayer owner, Ward ward, boolean healTick, Set<UUID> candidatesForClear) {
		Vec3 center = owner.position();
		double radiusSq = WARD_RADIUS * WARD_RADIUS;
		Set<UUID> insideNow = new HashSet<>();
		for (ServerPlayer ally : ward.level.players()) {
			if (!ally.isAlive() || !DuelRuntime.allies(ownerId, ally.getUUID())) continue;
			if (ally.position().distanceToSqr(center) > radiusSq) continue;
			UUID allyId = ally.getUUID();
			insideNow.add(allyId);
			ensureSpeedModifier(ally, WARD_SPEED_MOD, WARD_SPEED_MULTIPLIER);
			if (healTick) ally.heal(WARD_HEAL_AMOUNT);
		}
		for (UUID prevId : ward.buffedIds) {
			if (!insideNow.contains(prevId)) candidatesForClear.add(prevId);
		}
		ward.buffedIds.clear();
		ward.buffedIds.addAll(insideNow);
	}

	private void clearTotemSlows(Set<UUID> entityIds) {
		for (UUID id : entityIds) {
			boolean stillSlowed = false;
			for (Totem active : totems.values()) {
				if (active.slowedIds.contains(id)) {
					stillSlowed = true;
					break;
				}
			}
			if (stillSlowed) continue;
			LivingEntity entity = findLiving(id);
			if (entity != null) removeSpeedModifier(entity, TOTEM_SLOW_MOD);
		}
	}

	private void clearWardSpeeds(Set<UUID> entityIds) {
		for (UUID id : entityIds) {
			boolean stillBuffed = false;
			for (Ward active : wards.values()) {
				if (active.buffedIds.contains(id)) {
					stillBuffed = true;
					break;
				}
			}
			if (stillBuffed) continue;
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) removeSpeedModifier(player, WARD_SPEED_MOD);
		}
	}

	private LivingEntity findLiving(UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null) return player;
		Entity entity = server.overworld().getEntityInAnyDimension(id);
		return entity instanceof LivingEntity living ? living : null;
	}

	private static void ensureSpeedModifier(LivingEntity entity, Identifier id, double amount) {
		var attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attr == null || attr.hasModifier(id)) return;
		attr.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void removeSpeedModifier(LivingEntity entity, Identifier id) {
		var attr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (attr != null) attr.removeModifier(id);
	}

	@Override public void reset(ServerPlayer player) {
		UUID id = player.getUUID();
		removeSpeedModifier(player, TOTEM_SLOW_MOD);
		removeSpeedModifier(player, WARD_SPEED_MOD);

		Totem removedTotem = totems.remove(id);
		if (removedTotem != null) {
			clearTotemSlows(removedTotem.slowedIds);
			ShamanVisuals.totemOff(removedTotem.level, player, removedTotem.ownerId, removedTotem.pos);
		}
		Ward removedWard = wards.remove(id);
		if (removedWard != null) clearWardSpeeds(removedWard.buffedIds);

		for (Totem active : totems.values()) active.slowedIds.remove(id);
		for (Ward active : wards.values()) active.buffedIds.remove(id);
	}

	@Override public void clear() {
		Set<UUID> allSlowed = new HashSet<>();
		for (var entry : totems.entrySet()) {
			Totem totem = entry.getValue();
			allSlowed.addAll(totem.slowedIds);
			ShamanVisuals.totemOff(totem.level, server.getPlayerList().getPlayer(entry.getKey()), totem.ownerId, totem.pos);
		}
		totems.clear();
		for (UUID id : allSlowed) {
			LivingEntity entity = findLiving(id);
			if (entity != null) removeSpeedModifier(entity, TOTEM_SLOW_MOD);
		}

		Set<UUID> allBuffed = new HashSet<>();
		for (Ward ward : wards.values()) allBuffed.addAll(ward.buffedIds);
		wards.clear();
		for (UUID id : allBuffed) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) removeSpeedModifier(player, WARD_SPEED_MOD);
		}
	}
}
