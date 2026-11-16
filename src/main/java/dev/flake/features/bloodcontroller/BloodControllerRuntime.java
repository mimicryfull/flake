package dev.flake.features.bloodcontroller;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Stuns;
import dev.flake.combat.ability.AbilityEffects;
import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.combat.ability.ClassAbilityRuntime;
import dev.flake.combat.ability.ClassAbilityRuntimes;
import dev.flake.combat.ability.RuntimeClassSpec;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.Loadout.FighterClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blood Controller class runtime:
 * 1) Voodoo Doll (4s / 80 ticks): spawns a smaller voxel player doll tethered to the enemy; hits on the doll transfer to the enemy.
 * 2) Blood Drain: deals 5 damage to the target and heals the caster by the same amount.
 */
public final class BloodControllerRuntime implements ClassAbilityRuntime {
	public static final int VOODOO_TICKS = 80;
	public static final double VOODOO_CAST_RANGE = 12.0;
	public static final double DRAIN_RANGE = 10.0;
	public static final double AIM_COS = 0.78;
	public static final float DRAIN_DAMAGE = 5.0f;
	public static final String VOODOO_ENTITY_TAG = "flake_voodoo_doll";

	private static final Map<MinecraftServer, BloodControllerRuntime> INSTANCES = new IdentityHashMap<>();
	private static boolean installed;
	private static boolean hooksRegistered;

	private final MinecraftServer server;
	private final Map<UUID, VoodooSession> byOwner = new HashMap<>();
	private final Map<UUID, VoodooSession> byDoll = new HashMap<>();

	private static final class VoodooSession {
		final ServerLevel level;
		final UUID ownerId;
		final int ownerEntityId;
		final UUID targetId;
		final UUID dollEntityId;
		final Vec3 dollPos;
		final int startedAt;
		final int expiresAt;

		VoodooSession(ServerLevel level, UUID ownerId, int ownerEntityId, UUID targetId,
				UUID dollEntityId, Vec3 dollPos, int startedAt, int expiresAt) {
			this.level = level;
			this.ownerId = ownerId;
			this.ownerEntityId = ownerEntityId;
			this.targetId = targetId;
			this.dollEntityId = dollEntityId;
			this.dollPos = dollPos;
			this.startedAt = startedAt;
			this.expiresAt = expiresAt;
		}
	}

	public BloodControllerRuntime(MinecraftServer server) {
		this.server = server;
		INSTANCES.put(server, this);
	}

	public static void install() {
		if (installed) return;
		installed = true;
		ClassAbilityRuntimes.registerRuntime(FighterClass.BLOOD_CONTROLLER, BloodControllerRuntime::new);
		ContentCatalog.registerClass(new RuntimeClassSpec(
				FighterClass.BLOOD_CONTROLLER,
				"flake.class.blood_controller.role",
				"flake.class.blood_controller.summary",
				Ability.VOODOO_DOLL,
				Ability.BLOOD_DRAIN));
		ContentCatalog.registerLifecycle(new ContentSpec.Lifecycle() {
			@Override public void register() { registerServerHooks(); }
			@Override public void reset(ServerPlayer player) {}
		});
	}

	private static void registerServerHooks() {
		if (hooksRegistered) return;
		hooksRegistered = true;
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer attacker)) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof Interaction) || !entity.entityTags().contains(VOODOO_ENTITY_TAG)) {
				return InteractionResult.PASS;
			}
			BloodControllerRuntime runtime = INSTANCES.get(attacker.level().getServer());
			if (runtime == null) return InteractionResult.PASS;
			return runtime.handleDollHit(attacker, entity);
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Interaction && entity.entityTags().contains(VOODOO_ENTITY_TAG)) {
				BloodControllerRuntime runtime = INSTANCES.get(level.getServer());
				if (runtime == null || !runtime.byDoll.containsKey(entity.getUUID())) {
					entity.discard();
				}
			}
		});
	}

	@Override public List<Ability> abilities() {
		return List.of(Ability.VOODOO_DOLL, Ability.BLOOD_DRAIN);
	}

	@Override public boolean cast(ServerPlayer player, Ability ability, int now) {
		ServerLevel level = (ServerLevel) player.level();
		return switch (ability) {
			case VOODOO_DOLL -> castVoodoo(player, level, now);
			case BLOOD_DRAIN -> castBloodDrain(player, level);
			default -> false;
		};
	}

	private boolean castVoodoo(ServerPlayer player, ServerLevel level, int now) {
		LivingEntity target = AbilityTargeting.target(player, VOODOO_CAST_RANGE, AIM_COS, true);
		if (target == null) return false;

		removeSession(player.getUUID(), false);

		Vec3 dollPos = resolveDollSpawnPos(player, level);
		Interaction hitbox = EntityTypes.INTERACTION.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (hitbox == null) return false;
		hitbox.setWidth(0.70f);
		hitbox.setHeight(1.30f);
		hitbox.setResponse(true);
		hitbox.setPos(dollPos.x, dollPos.y, dollPos.z);
		hitbox.addTag(VOODOO_ENTITY_TAG);
		if (!level.addFreshEntity(hitbox)) return false;

		VoodooSession session = new VoodooSession(
				level, player.getUUID(), player.getId(), target.getUUID(),
				hitbox.getUUID(), dollPos, now, now + VOODOO_TICKS);
		byOwner.put(player.getUUID(), session);
		byDoll.put(hitbox.getUUID(), session);

		float yawRad = (float) Math.atan2(target.getX() - dollPos.x, target.getZ() - dollPos.z);
		BloodControllerVisuals.voodooOn(player, target, dollPos, yawRad);
		AbilityEffects.sound(level, player, SoundEvents.WARDEN_HEARTBEAT, 0.90f, 0.85f);
		AbilityEffects.sound(level, player, SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 0.75f, 0.65f);
		return true;
	}

	private boolean castBloodDrain(ServerPlayer player, ServerLevel level) {
		LivingEntity target = AbilityTargeting.target(player, DRAIN_RANGE, AIM_COS, true);
		if (target == null) return false;

		float dmg = CombatModifiers.skillDamage(player, target, DRAIN_DAMAGE);
		boolean hit = target.hurtServer(level, player.damageSources().indirectMagic(null, player), dmg);
		if (hit) {
			player.heal(CombatModifiers.scaleHealing(player, dmg));
		}

		BloodControllerVisuals.bloodDrain(player, target, dmg);
		AbilityEffects.sound(level, player, SoundEvents.WARDEN_HEARTBEAT, 0.85f, 1.05f);
		AbilityEffects.sound(level, player, SoundEvents.PHANTOM_BITE, 0.80f, 0.72f);
		if (hit) {
			AbilityEffects.sound(level, player, SoundEvents.PLAYER_HURT, 0.55f, 0.85f);
		}
		return true;
	}

	private InteractionResult handleDollHit(ServerPlayer attacker, Entity dollEntity) {
		VoodooSession session = byDoll.get(dollEntity.getUUID());
		if (session == null) {
			dollEntity.discard();
			return InteractionResult.PASS;
		}
		if (Stuns.remaining(attacker, server.getTickCount()) > 0 || DuelRuntime.blocksAbility(attacker.getUUID())) {
			return InteractionResult.FAIL;
		}
		if (attacker.getUUID().equals(session.targetId)) {
			return InteractionResult.FAIL;
		}

		Entity targetEntity = session.level.getEntity(session.targetId);
		if (!(targetEntity instanceof LivingEntity target) || !target.isAlive() || target.level() != session.level) {
			removeSession(session.ownerId, true);
			return InteractionResult.PASS;
		}
		if (target instanceof ServerPlayer victimPlayer && DuelRuntime.protectedInventory(victimPlayer.getUUID())) {
			if (!DuelRuntime.fighting(victimPlayer.getUUID())
					|| !DuelRuntime.opponents(attacker.getUUID(), victimPlayer.getUUID())) {
				return InteractionResult.FAIL;
			}
		}

		float strength = attacker.getAttackStrengthScale(0.5f);
		float chargeScale = 0.2f + strength * strength * 0.8f;
		float baseDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
		boolean crit = strength > 0.9f && attacker.fallDistance > 0.0f && !attacker.onGround()
				&& !attacker.onClimbable() && !attacker.isInWater() && !attacker.isPassenger() && !attacker.isSprinting();
		float rawDamage = Math.max(0.5f, baseDamage * chargeScale * (crit ? 1.5f : 1.0f));
		attacker.resetAttackStrengthTicker();

		boolean hurt = target.hurtServer(session.level, attacker.damageSources().playerAttack(attacker), rawDamage);
		if (crit) attacker.crit(dollEntity);

		BloodControllerVisuals.voodooHit(attacker, session.level, session.ownerEntityId, session.dollPos, target, rawDamage);
		AbilityEffects.sound(session.level, attacker, crit ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG,
				0.85f, 0.82f);
		AbilityEffects.sound(session.level, attacker, SoundEvents.SLIME_SQUISH_SMALL, 0.70f, 0.65f);
		if (hurt) {
			session.level.playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.PLAYERS, 0.80f, 1.15f);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	private static Vec3 resolveDollSpawnPos(ServerPlayer player, ServerLevel level) {
		Vec3 look = player.getLookAngle();
		Vec3 horiz = new Vec3(look.x, 0.0, look.z);
		if (horiz.lengthSqr() < 1e-4) {
			double yawRad = Math.toRadians(player.getYRot());
			horiz = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
		} else {
			horiz = horiz.normalize();
		}

		Vec3 start = player.position().add(0.0, 0.9, 0.0);
		Vec3 end = start.add(horiz.scale(1.85));
		BlockHitResult wallHit = level.clip(new ClipContext(
				start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 flat = wallHit.getType() == HitResult.Type.BLOCK
				? wallHit.getLocation().subtract(horiz.scale(0.48))
				: end;

		BlockHitResult groundHit = level.clip(new ClipContext(
				flat.add(0.0, 0.35, 0.0),
				flat.subtract(0.0, 1.8, 0.0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double y = groundHit.getType() == HitResult.Type.BLOCK ? groundHit.getLocation().y + 0.06 : player.position().y;
		return new Vec3(flat.x, y, flat.z);
	}

	@Override public int activeElapsed(UUID player, int slot, int now) {
		if (slot != 1) return -1;
		VoodooSession session = byOwner.get(player);
		if (session == null || now >= session.expiresAt) return -1;
		return Math.clamp(now - session.startedAt, 0, VOODOO_TICKS);
	}

	@Override public void tick(int now) {
		if (byOwner.isEmpty()) return;
		for (var entry : new ArrayList<>(byOwner.entrySet())) {
			UUID ownerId = entry.getKey();
			VoodooSession session = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			Entity targetEntity = session.level.getEntity(session.targetId);
			Entity doll = session.level.getEntity(session.dollEntityId);

			boolean invalidOwner = owner == null || !owner.isAlive() || owner.level() != session.level || DuelRuntime.blocksAbility(ownerId);
			boolean invalidTarget = !(targetEntity instanceof LivingEntity target) || !target.isAlive() || target.level() != session.level;
			boolean invalidDoll = doll == null || doll.isRemoved();

			if (invalidOwner || invalidTarget || invalidDoll || now >= session.expiresAt) {
				removeSession(ownerId, true);
			}
		}
	}

	private void removeSession(UUID ownerId, boolean sendOffVfx) {
		VoodooSession session = byOwner.remove(ownerId);
		if (session == null) return;
		byDoll.remove(session.dollEntityId);
		Entity doll = session.level.getEntity(session.dollEntityId);
		if (doll != null) doll.discard();
		if (sendOffVfx) {
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			BloodControllerVisuals.voodooOff(session.level, owner, session.ownerEntityId, session.dollPos);
		}
	}

	@Override public void reset(ServerPlayer player) {
		removeSession(player.getUUID(), true);
	}

	@Override public void clear() {
		for (VoodooSession session : new ArrayList<>(byOwner.values())) {
			Entity doll = session.level.getEntity(session.dollEntityId);
			if (doll != null) doll.discard();
		}
		byOwner.clear();
		byDoll.clear();
		INSTANCES.remove(server);
	}
}
