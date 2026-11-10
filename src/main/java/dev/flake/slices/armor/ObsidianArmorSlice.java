package dev.flake.slices.armor;

import dev.flake.build.Loadout;
import dev.flake.build.LoadoutCommands;
import dev.flake.duel.DuelRuntime;
import dev.flake.duel.ShockwaveVisuals;
import dev.flake.shared.CombatSlice.Lifecycle;
import dev.flake.shared.SliceRegistry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Vertical slice for the «Обсидіановий Бастіон» (Obsidian Bastion) heavy PvP armor set:
 * <ul>
 *   <li><b>Per-piece trade-off</b>: High armor (3/8/6/3 = 20) &amp; toughness (3.0 per piece = 12.0),
 *       +10% knockback resistance per piece, at the cost of <b>-3% movement speed per piece</b> (-12% for 4/4).</li>
 *   <li><b>4/4 Full Set Bonus («Обсидіановий панцир»)</b>:
 *     <ol>
 *       <li>Reduces incoming class-skill and explosion damage by <b>20%</b>.</li>
 *       <li><b>Reactive Carapace Pulse</b>: Every <b>16.0 HP</b> of combat damage taken triggers an obsidian shock-pulse
 *           (10s cooldown) granting <b>+4.0 HP Absorption</b> for 4s and dealing <b>2.5 HP</b> + knockback in a 2.8m radius.</li>
 *     </ol>
 *   </li>
 * </ul>
 */
public final class ObsidianArmorSlice implements Lifecycle {
	public static final double SPEED_PENALTY_PER_PIECE = -0.03;
	public static final float SKILL_DAMAGE_DAMPENING = 0.80f;
	public static final float PULSE_DAMAGE_THRESHOLD = 16.0f;
	public static final float PULSE_ABSORPTION_HP = 4.0f;
	public static final int PULSE_ABSORPTION_TICKS = 80;
	public static final int PULSE_COOLDOWN_TICKS = 200;
	public static final float PULSE_FEEDBACK_DAMAGE = 2.5f;
	public static final double PULSE_RADIUS = 2.8;
	public static final double PULSE_KNOCKBACK = 0.55;

	private static final Identifier WEIGHT_MOD = Identifier.fromNamespaceAndPath("flake", "obsidian_armor_weight");
	private static final Set<UUID> FULL_SET_ACTIVE = new HashSet<>();
	private static final Map<UUID, Float> CUMULATIVE_DAMAGE = new HashMap<>();
	private static final Map<UUID, Integer> PULSE_READY_AT = new HashMap<>();
	private static boolean registered;
	private static boolean pulsing;

	private ObsidianArmorSlice() {}

	public static void install() {
		SliceRegistry.registerLifecycle(new ObsidianArmorSlice());
	}

	/** Pure calculation of the movement speed multiplier delta for {@code pieces} Obsidian armor pieces. */
	public static double speedModifierForPieces(int pieces) {
		int clamped = Math.clamp(pieces, 0, 4);
		return clamped * SPEED_PENALTY_PER_PIECE;
	}

	/** Pure check for whether incoming damage crosses the reactive carapace threshold while off cooldown. */
	public static boolean shouldTriggerPulse(float accumulatedBefore, float taken, int nowTick, int readyAtTick) {
		if (taken <= 0f || !Float.isFinite(taken) || nowTick < readyAtTick) return false;
		return accumulatedBefore + taken >= PULSE_DAMAGE_THRESHOLD;
	}

	/** Pure calculation of skill damage after 4/4 Obsidian Carapace dampening. */
	public static float dampenSkillDamage(boolean hasFullObsidianSet, float damage) {
		if (damage <= 0f || !Float.isFinite(damage)) return 0f;
		return hasFullObsidianSet ? damage * SKILL_DAMAGE_DAMPENING : damage;
	}

	/** Server-side helper to mitigate incoming skill/blast damage when the target wears 4/4 Obsidian armor. */
	public static float mitigateSkillDamage(LivingEntity target, float damage) {
		if (target instanceof ServerPlayer sp && hasActiveFullSet(sp)) {
			return dampenSkillDamage(true, damage);
		}
		return damage;
	}

	public static boolean hasActiveFullSet(ServerPlayer player) {
		UUID id = player.getUUID();
		if (FULL_SET_ACTIVE.contains(id)) return true;
		if (!DuelRuntime.fighting(id)) {
			var build = LoadoutCommands.loadout(id);
			return build != null && build.hasFullObsidianSet() && wearingFullNetherite(player);
		}
		return false;
	}

	private static boolean wearingFullNetherite(ServerPlayer player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
				&& player.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
				&& player.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
				&& player.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS);
	}

	/** Applies per-piece weight modifiers and initializes 4/4 set tracking at duel kit preparation. */
	public static void applyKit(ServerPlayer player, Loadout build) {
		resetPlayer(player);
		if (build == null) return;
		int pieces = build.obsidianPieces();
		if (pieces > 0) {
			var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(WEIGHT_MOD);
				speed.addTransientModifier(new AttributeModifier(
						WEIGHT_MOD,
						speedModifierForPieces(pieces),
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
				));
			}
		}
		if (build.hasFullObsidianSet()) {
			FULL_SET_ACTIVE.add(player.getUUID());
			CUMULATIVE_DAMAGE.put(player.getUUID(), 0f);
		}
	}

	@Override
	public void register() {
		if (registered) return;
		registered = true;
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (blocked || taken <= 0f || pulsing) return;
			if (!(victim instanceof ServerPlayer hit) || !hit.isAlive()) return;
			if (!hasActiveFullSet(hit)) return;
			if (DuelRuntime.blocksAbility(hit.getUUID())) return;

			UUID id = hit.getUUID();
			int now = hit.level().getServer().getTickCount();
			int readyAt = PULSE_READY_AT.getOrDefault(id, 0);
			float before = CUMULATIVE_DAMAGE.getOrDefault(id, 0f);

			if (shouldTriggerPulse(before, taken, now, readyAt)) {
				CUMULATIVE_DAMAGE.put(id, 0f);
				PULSE_READY_AT.put(id, now + PULSE_COOLDOWN_TICKS);
				triggerReactivePulse(hit, source.getSourcePosition());
			} else if (now >= readyAt) {
				CUMULATIVE_DAMAGE.put(id, Math.min(PULSE_DAMAGE_THRESHOLD - 0.01f, before + taken));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> resetPlayer(handler.player));
	}

	private static void triggerReactivePulse(ServerPlayer wearer, Vec3 hitSourcePos) {
		if (!(wearer.level() instanceof ServerLevel level)) return;
		wearer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, PULSE_ABSORPTION_TICKS, 0, false, false, true));
		wearer.setAbsorptionAmount(Math.max(wearer.getAbsorptionAmount(), PULSE_ABSORPTION_HP));

		ShockwaveVisuals.bulwarkHit(wearer, hitSourcePos != null ? hitSourcePos : wearer.position());
		level.playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(),
				SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.55f, 0.65f);
		level.playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(),
				SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.75f, 0.9f);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL,
				wearer.getX(), wearer.getY() + 1.0, wearer.getZ(), 18, 0.55, 0.45, 0.55, 0.06);

		Vec3 origin = wearer.position();
		boolean inDuel = DuelRuntime.fighting(wearer.getUUID());
		double radiusSq = PULSE_RADIUS * PULSE_RADIUS;

		pulsing = true;
		try {
			for (var other : level.players()) {
				if (other == wearer || !other.isAlive()) continue;
				if (inDuel && !DuelRuntime.opponents(wearer.getUUID(), other.getUUID())) continue;
				if (other.position().distanceToSqr(origin) > radiusSq) continue;
				applyPulseHit(wearer, level, origin, other);
			}
			if (!inDuel) {
				var box = new AABB(origin.x - PULSE_RADIUS - 1, origin.y - PULSE_RADIUS - 1, origin.z - PULSE_RADIUS - 1,
						origin.x + PULSE_RADIUS + 1, origin.y + PULSE_RADIUS + 1, origin.z + PULSE_RADIUS + 1);
				for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.entityTags().contains("flake_test_dummy"))) {
					if (mob.position().distanceToSqr(origin) <= radiusSq) {
						applyPulseHit(wearer, level, origin, mob);
					}
				}
			}
		} finally {
			pulsing = false;
		}
	}

	private static void applyPulseHit(ServerPlayer wearer, ServerLevel level, Vec3 origin, LivingEntity target) {
		target.hurtServer(level, wearer.damageSources().thorns(wearer), PULSE_FEEDBACK_DAMAGE);
		Vec3 diff = target.position().subtract(origin).multiply(1, 0, 1);
		Vec3 dir = diff.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : diff.normalize();
		Vec3 knock = dir.scale(PULSE_KNOCKBACK).add(0, 0.22, 0);
		target.setDeltaMovement(knock);
		if (target instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
		else target.syncVelocity = true;
	}

	public static void resetPlayer(ServerPlayer player) {
		UUID id = player.getUUID();
		FULL_SET_ACTIVE.remove(id);
		CUMULATIVE_DAMAGE.remove(id);
		PULSE_READY_AT.remove(id);
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(WEIGHT_MOD);
	}

	@Override
	public void reset(ServerPlayer player) {
		resetPlayer(player);
	}
}
