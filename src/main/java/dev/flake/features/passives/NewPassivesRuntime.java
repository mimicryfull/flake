package dev.flake.features.passives;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.content.ItemRegistration;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.Loadout.PassiveItem;
import dev.flake.features.training.TrainingDummies;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server runtime for Bloody Seal, Mirror Scale, Hunter Compass, Storm Charge, and Silent Step. */
public final class NewPassivesRuntime {
	public static final Identifier BLOODY_HAND_ID = Identifier.fromNamespaceAndPath("flake", "bloody_hand");
	public static final Identifier MIRROR_SCALE_ID = Identifier.fromNamespaceAndPath("flake", "mirror_scale");
	public static final Identifier STORM_BOLT_ID = Identifier.fromNamespaceAndPath("flake", "storm_bolt");
	private static final Identifier SILENT_STEP_MOD = Identifier.fromNamespaceAndPath("flake", "silent_step_speed");

	public static final float BLOODY_SEAL_HP_THRESHOLD = 0.40f;
	public static final float BLOODY_SEAL_OUTGOING_SCALE = 1.12f;
	public static final float BLOODY_SEAL_INCOMING_SCALE = 1.05f;
	public static final float MIRROR_SCALE_FIRST_HIT = 0.50f;
	public static final int HUNTER_INTERVAL_TICKS = 5;
	public static final float HUNTER_HP_THRESHOLD = 0.30f;
	public static final int HUNTER_VFX_LEASE_TICKS = 12;
	public static final double HUNTER_FALLBACK_RANGE = 64.0;
	public static final int STORM_HIT_INTERVAL = 5;
	public static final float STORM_PRIMARY_DAMAGE = 2.0f;
	public static final float STORM_SECONDARY_DAMAGE = 1.0f;
	public static final double STORM_CHAIN_RADIUS = 2.0;
	public static final int STORM_VFX_LEASE_TICKS = 10;
	public static final float SILENT_STEP_VOLUME = 0.75f;
	public static final int SILENT_STEP_DELAY_TICKS = 60;
	public static final double SILENT_STEP_SPEED_BONUS = 0.10;

	public static Item BLOODY_HAND_ITEM;
	public static Item MIRROR_SCALE_ITEM;
	public static Item STORM_BOLT_ITEM;

	private static final class State {
		final Set<UUID> mirrorConsumed = new HashSet<>();
		final Map<UUID, Integer> lastHunterTick = new HashMap<>();
		final Map<UUID, Integer> stormHits = new HashMap<>();
		final Map<UUID, Integer> lastAttackTick = new HashMap<>();
		boolean stormTriggering;
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static boolean installed;
	private static boolean registered;

	private NewPassivesRuntime() {}

	public static void registerItems() {
		BLOODY_HAND_ITEM = ItemRegistration.register(BLOODY_HAND_ID,
				key -> new Item(new Item.Properties().setId(key)));
		MIRROR_SCALE_ITEM = ItemRegistration.register(MIRROR_SCALE_ID,
				key -> new Item(new Item.Properties().setId(key)));
		STORM_BOLT_ITEM = ItemRegistration.register(STORM_BOLT_ID,
				key -> new Item(new Item.Properties().setId(key)));
	}

	public static ItemStack bloodyHandStack() {
		return BLOODY_HAND_ITEM == null ? ItemStack.EMPTY : new ItemStack(BLOODY_HAND_ITEM);
	}

	public static ItemStack mirrorScaleStack() {
		return MIRROR_SCALE_ITEM == null ? ItemStack.EMPTY : new ItemStack(MIRROR_SCALE_ITEM);
	}

	public static ItemStack stormBoltStack() {
		return STORM_BOLT_ITEM == null ? ItemStack.EMPTY : new ItemStack(STORM_BOLT_ITEM);
	}

	public static float bloodySealOutgoingScale(float health, float maxHealth) {
		if (maxHealth <= 0f) return 1.0f;
		return health < maxHealth * BLOODY_SEAL_HP_THRESHOLD ? BLOODY_SEAL_OUTGOING_SCALE : 1.0f;
	}

	public static float bloodySealIncomingScale(float health, float maxHealth) {
		if (maxHealth <= 0f) return 1.0f;
		return health > maxHealth * BLOODY_SEAL_HP_THRESHOLD ? BLOODY_SEAL_INCOMING_SCALE : 1.0f;
	}

	public static void install() {
		if (installed) return;
		installed = true;

		CombatModifiers.damageScale(NewPassivesRuntime::scaleDamage);
		CombatModifiers.afterDamage(NewPassivesRuntime::onAfterDamage);
		CombatModifiers.stepVolume(entity ->
				DuelRuntime.passiveOf(entity.getUUID()) == PassiveItem.SILENT_STEP ? SILENT_STEP_VOLUME : 1.0f);
	}

	public static void register() {
		if (registered) return;
		registered = true;
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}

	private static float scaleDamage(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0f || !Float.isFinite(amount)) return amount;
		if (source.getEntity() instanceof ServerPlayer attacker
				&& DuelRuntime.passiveOf(attacker.getUUID()) == PassiveItem.BLOODY_SEAL) {
			amount *= bloodySealOutgoingScale(attacker.getHealth(), attacker.getMaxHealth());
		}
		if (victim instanceof ServerPlayer victimPlayer) {
			PassiveItem victimPassive = DuelRuntime.passiveOf(victimPlayer.getUUID());
			if (victimPassive == PassiveItem.BLOODY_SEAL) {
				amount *= bloodySealIncomingScale(victimPlayer.getHealth(), victimPlayer.getMaxHealth());
			} else if (victimPassive == PassiveItem.MIRROR_SCALE
					&& !STATES.of(victimPlayer).mirrorConsumed.contains(victimPlayer.getUUID())) {
				amount *= MIRROR_SCALE_FIRST_HIT;
			}
		}
		return amount;
	}

	private static void onAfterDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
		if (blocked || taken <= 0f) return;

		if (victim instanceof ServerPlayer victimPlayer
				&& DuelRuntime.passiveOf(victimPlayer.getUUID()) == PassiveItem.MIRROR_SCALE) {
			STATES.of(victimPlayer).mirrorConsumed.add(victimPlayer.getUUID());
		}

		if (!(source.getEntity() instanceof ServerPlayer attacker)) return;

		if (victim != attacker && DuelRuntime.passiveOf(attacker.getUUID()) == PassiveItem.SILENT_STEP) {
			int now = attacker.level().getServer().getTickCount();
			STATES.of(attacker).lastAttackTick.put(attacker.getUUID(), now);
			removeSilentSpeed(attacker);
		}

		if (source.getDirectEntity() == attacker
				&& victim != attacker
				&& DuelRuntime.passiveOf(attacker.getUUID()) == PassiveItem.STORM_CHARGE) {
			handleStormMeleeHit(attacker, victim);
		}
	}

	private static void handleStormMeleeHit(ServerPlayer attacker, LivingEntity victim) {
		if (!(attacker.level() instanceof ServerLevel level)) return;
		var state = STATES.of(attacker);
		if (state.stormTriggering) return;

		UUID attackerId = attacker.getUUID();
		int count = state.stormHits.getOrDefault(attackerId, 0) + 1;
		if (count < STORM_HIT_INTERVAL) {
			state.stormHits.put(attackerId, count);
			return;
		}
		state.stormHits.put(attackerId, 0);

		Vec3 targetCenter = victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0);
		Vec3 victimPos = victim.position();
		double radiusSq = STORM_CHAIN_RADIUS * STORM_CHAIN_RADIUS;
		var magicSource = attacker.damageSources().indirectMagic(attacker, attacker);

		state.stormTriggering = true;
		try {
			if (victim.isAlive()) {
				float primaryDamage = CombatModifiers.skillDamage(attacker, victim, STORM_PRIMARY_DAMAGE);
				int prevInvuln = victim.getInvulnerableTime();
				victim.setInvulnerableTime(0);
				try {
					victim.hurtServer(level, magicSource, primaryDamage);
				} finally {
					victim.setInvulnerableTime(prevInvuln);
				}
			}

			int arcCount = 0;
			for (LivingEntity other : AbilityTargeting.candidates(attacker, level, victimPos, STORM_CHAIN_RADIUS)) {
				if (other == victim || !other.isAlive()) continue;
				if (other.position().distanceToSqr(victimPos) > radiusSq) continue;

				float secondaryDamage = CombatModifiers.skillDamage(attacker, other, STORM_SECONDARY_DAMAGE);
				other.hurtServer(level, magicSource, secondaryDamage);

				Vec3 otherCenter = other.position().add(0.0, other.getBbHeight() * 0.5, 0.0);
				CombatVisuals.broadcast(attacker,
						new CombatVfxPayload(targetCenter, attacker.getId(), CombatVfxPayload.Phase.STORM_ARC, 0f, other.getId(), otherCenter),
						targetCenter, STORM_VFX_LEASE_TICKS);
				arcCount++;
			}

			if (arcCount == 0) {
				CombatVisuals.broadcast(attacker,
						new CombatVfxPayload(targetCenter, attacker.getId(), CombatVfxPayload.Phase.STORM_ARC, 0f, victim.getId(), victimPos),
						targetCenter, STORM_VFX_LEASE_TICKS);
			}

			level.playSound(null, victimPos.x, victimPos.y, victimPos.z,
					SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.32f, 1.55f);
		} finally {
			state.stormTriggering = false;
		}
	}

	public static void resetMirrorScale(ServerPlayer player) {
		STATES.of(player).mirrorConsumed.remove(player.getUUID());
	}

	public static void resetHunterCompass(ServerPlayer player) {
		STATES.of(player).lastHunterTick.remove(player.getUUID());
	}

	public static void tickHunterCompass(ServerPlayer player, int nowTick) {
		if (!player.isAlive() || !(player.level() instanceof ServerLevel level)) return;
		var state = STATES.of(player);
		UUID id = player.getUUID();
		Integer last = state.lastHunterTick.get(id);
		if (last != null && nowTick - last < HUNTER_INTERVAL_TICKS) return;
		state.lastHunterTick.put(id, nowTick);

		if (DuelRuntime.fighting(id)) {
			for (ServerPlayer other : level.players()) {
				if (other == player || !other.isAlive()) continue;
				if (!DuelRuntime.opponents(id, other.getUUID())) continue;
				if (isLowHealth(other)) broadcastHighlight(player, other);
			}
			return;
		}

		double maxDistSq = HUNTER_FALLBACK_RANGE * HUNTER_FALLBACK_RANGE;
		ServerPlayer nearest = null;
		double bestDistSq = maxDistSq;
		for (ServerPlayer other : level.players()) {
			if (other == player || !other.isAlive()) continue;
			double distSq = other.distanceToSqr(player);
			if (distSq <= bestDistSq) {
				bestDistSq = distSq;
				nearest = other;
			}
		}
		if (nearest != null && isLowHealth(nearest)) {
			broadcastHighlight(player, nearest);
		}

		var box = player.getBoundingBox().inflate(HUNTER_FALLBACK_RANGE);
		for (Mob dummy : level.getEntitiesOfClass(Mob.class, box,
				m -> m.isAlive() && m.entityTags().contains(TrainingDummies.TAG))) {
			if (isLowHealth(dummy)) {
				broadcastHighlight(player, dummy);
			}
		}
	}

	private static boolean isLowHealth(LivingEntity entity) {
		return entity.getHealth() < entity.getMaxHealth() * HUNTER_HP_THRESHOLD;
	}

	private static void broadcastHighlight(ServerPlayer player, LivingEntity enemy) {
		Vec3 pos = enemy.position();
		CombatVisuals.broadcast(player,
				new CombatVfxPayload(pos, player.getId(), CombatVfxPayload.Phase.HUNTER_HIGHLIGHT, 0f, enemy.getId(), pos),
				pos, HUNTER_VFX_LEASE_TICKS);
	}

	public static void resetStormCharge(ServerPlayer player) {
		STATES.of(player).stormHits.remove(player.getUUID());
	}

	public static void initSilentStep(ServerPlayer player) {
		removeSilentSpeed(player);
		var server = player.level().getServer();
		int now = server != null ? server.getTickCount() : 0;
		STATES.of(player).lastAttackTick.put(player.getUUID(), now);
	}

	public static void tickSilentStep(ServerPlayer player, int nowTick) {
		if (!player.isAlive()) {
			removeSilentSpeed(player);
			return;
		}
		var state = STATES.of(player);
		int last = state.lastAttackTick.computeIfAbsent(player.getUUID(), ignored -> nowTick);
		if (nowTick - last >= SILENT_STEP_DELAY_TICKS) {
			applySilentSpeed(player);
		} else {
			removeSilentSpeed(player);
		}
	}

	private static void applySilentSpeed(ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null || speed.hasModifier(SILENT_STEP_MOD)) return;
		speed.addTransientModifier(new AttributeModifier(
				SILENT_STEP_MOD, SILENT_STEP_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void removeSilentSpeed(ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(SILENT_STEP_MOD);
	}

	public static void reset(ServerPlayer player) {
		UUID id = player.getUUID();
		var state = STATES.of(player);
		state.mirrorConsumed.remove(id);
		state.lastHunterTick.remove(id);
		state.stormHits.remove(id);
		state.lastAttackTick.remove(id);
		removeSilentSpeed(player);
	}
}
