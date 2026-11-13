package dev.flake.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Explicit combat stages. Features contribute rules; the pipeline never imports features. */
public final class CombatModifiers {
	@FunctionalInterface
	public interface SkillDamage {
		float apply(ServerPlayer attacker, LivingEntity target, float damage);
	}

	@FunctionalInterface
	public interface Cooldown {
		float scale(ServerPlayer player);
	}

	@FunctionalInterface
	public interface ItemCooldown {
		int total(ServerPlayer player, ItemStack stack, int baseTicks);
	}

	@FunctionalInterface
	public interface MeleeHealing { float amount(ServerPlayer attacker, float taken); }

	public record Reflection(net.minecraft.world.damagesource.DamageSource source, float damage) {}

	@FunctionalInterface
	public interface ReflectedDamage { Reflection evaluate(ServerPlayer victim, ServerPlayer attacker, float taken); }

	@FunctionalInterface
	public interface DamageGate { boolean allow(LivingEntity victim, DamageSource source); }

	@FunctionalInterface
	public interface DamageScale { float apply(LivingEntity victim, DamageSource source, float amount); }

	@FunctionalInterface
	public interface MobEffectGate { boolean allow(LivingEntity entity, MobEffectInstance effect); }

	@FunctionalInterface
	public interface StepVolume { float scale(Entity entity); }

	@FunctionalInterface
	public interface ProjectileTrap { boolean intercept(Projectile projectile, LivingEntity victim); }

	@FunctionalInterface
	public interface CustomProjectileTrap { boolean intercept(ServerPlayer victim, ServerPlayer attacker, Vec3 origin); }

	@FunctionalInterface
	public interface AfterDamageListener { void onAfterDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked); }

	private static final List<SkillDamage> OUTGOING = new ArrayList<>();
	private static final List<SkillDamage> INCOMING = new ArrayList<>();
	private static final List<Cooldown> COOLDOWNS = new ArrayList<>();
	private static final List<ItemCooldown> ITEM_COOLDOWNS = new ArrayList<>();
	private static final List<MeleeHealing> MELEE_HEALING = new ArrayList<>();
	private static final List<ReflectedDamage> REFLECTIONS = new ArrayList<>();
	private static final List<DamageGate> DAMAGE_GATES = new ArrayList<>();
	private static final List<DamageScale> DAMAGE_SCALES = new ArrayList<>();
	private static final List<MobEffectGate> EFFECT_GATES = new ArrayList<>();
	private static final List<StepVolume> STEP_VOLUMES = new ArrayList<>();
	private static final List<ProjectileTrap> PROJECTILE_TRAPS = new ArrayList<>();
	private static final List<CustomProjectileTrap> CUSTOM_PROJECTILE_TRAPS = new ArrayList<>();
	private static final List<AfterDamageListener> AFTER_DAMAGE = new ArrayList<>();

	private CombatModifiers() {}

	public static void outgoingSkillDamage(SkillDamage modifier) { OUTGOING.add(modifier); }
	public static void incomingSkillDamage(SkillDamage modifier) { INCOMING.add(modifier); }
	public static void cooldown(Cooldown modifier) { COOLDOWNS.add(modifier); }
	public static void itemCooldown(ItemCooldown source) { ITEM_COOLDOWNS.add(source); }
	public static void meleeHealing(MeleeHealing source) { MELEE_HEALING.add(source); }
	public static void reflectedDamage(ReflectedDamage source) { REFLECTIONS.add(source); }
	public static void damageGate(DamageGate gate) { DAMAGE_GATES.add(gate); }
	public static void damageScale(DamageScale scale) { DAMAGE_SCALES.add(scale); }
	public static void effectGate(MobEffectGate gate) { EFFECT_GATES.add(gate); }
	public static void stepVolume(StepVolume volume) { STEP_VOLUMES.add(volume); }
	public static void projectileTrap(ProjectileTrap trap) { PROJECTILE_TRAPS.add(trap); }
	public static void customProjectileTrap(CustomProjectileTrap trap) { CUSTOM_PROJECTILE_TRAPS.add(trap); }
	public static void afterDamage(AfterDamageListener listener) { AFTER_DAMAGE.add(listener); }

	public static float meleeHealing(ServerPlayer attacker, float taken) {
		float healing = 0;
		for (var source : MELEE_HEALING) healing += source.amount(attacker, taken);
		return healing;
	}

	public static List<Reflection> reflections(ServerPlayer victim, ServerPlayer attacker, float taken) {
		var result = new ArrayList<Reflection>();
		for (var source : REFLECTIONS) {
			var reflection = source.evaluate(victim, attacker, taken);
			if (reflection != null && reflection.damage() > 0) result.add(reflection);
		}
		return result;
	}

	/** Outgoing amplification precedes incoming mitigation, as in the original gameplay. */
	public static float skillDamage(ServerPlayer attacker, LivingEntity target, float base) {
		float damage = base;
		for (var modifier : OUTGOING) damage = modifier.apply(attacker, target, damage);
		for (var modifier : INCOMING) damage = modifier.apply(attacker, target, damage);
		return damage;
	}

	public static float cooldownScale(ServerPlayer player) {
		float scale = 1f;
		for (var modifier : COOLDOWNS) scale *= modifier.scale(player);
		return scale;
	}

	public static int itemCooldownTotal(ServerPlayer player, ItemStack stack, int baseTicks) {
		int total = baseTicks;
		for (var source : ITEM_COOLDOWNS) total = source.total(player, stack, total);
		return total;
	}

	public static boolean allowDamage(LivingEntity victim, DamageSource source) {
		for (var gate : DAMAGE_GATES) if (!gate.allow(victim, source)) return false;
		return true;
	}

	public static float modifyDamage(LivingEntity victim, DamageSource source, float baseAmount) {
		float amount = baseAmount;
		for (var scale : DAMAGE_SCALES) amount = scale.apply(victim, source, amount);
		return Math.max(0f, amount);
	}

	public static boolean allowEffect(LivingEntity entity, MobEffectInstance effect) {
		for (var gate : EFFECT_GATES) if (!gate.allow(entity, effect)) return false;
		return true;
	}

	public static float stepVolumeScale(Entity entity) {
		float scale = 1.0f;
		for (var mod : STEP_VOLUMES) scale *= mod.scale(entity);
		return Math.clamp(scale, 0.1f, 2.0f);
	}

	public static boolean interceptProjectile(Projectile projectile, LivingEntity victim) {
		for (var trap : PROJECTILE_TRAPS) if (trap.intercept(projectile, victim)) return true;
		return false;
	}

	public static boolean interceptCustomProjectile(ServerPlayer victim, ServerPlayer attacker, Vec3 origin) {
		for (var trap : CUSTOM_PROJECTILE_TRAPS) if (trap.intercept(victim, attacker, origin)) return true;
		return false;
	}

	public static void notifyAfterDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
		for (var listener : AFTER_DAMAGE) listener.onAfterDamage(victim, source, taken, blocked);
	}
}
