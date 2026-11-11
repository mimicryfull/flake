package dev.flake.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

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

	private static final List<SkillDamage> OUTGOING = new ArrayList<>();
	private static final List<SkillDamage> INCOMING = new ArrayList<>();
	private static final List<Cooldown> COOLDOWNS = new ArrayList<>();
	private static final List<ItemCooldown> ITEM_COOLDOWNS = new ArrayList<>();
	private static final List<MeleeHealing> MELEE_HEALING = new ArrayList<>();
	private static final List<ReflectedDamage> REFLECTIONS = new ArrayList<>();

	private CombatModifiers() {}

	public static void outgoingSkillDamage(SkillDamage modifier) { OUTGOING.add(modifier); }
	public static void incomingSkillDamage(SkillDamage modifier) { INCOMING.add(modifier); }
	public static void cooldown(Cooldown modifier) { COOLDOWNS.add(modifier); }
	public static void itemCooldown(ItemCooldown source) { ITEM_COOLDOWNS.add(source); }
	public static void meleeHealing(MeleeHealing source) { MELEE_HEALING.add(source); }
	public static void reflectedDamage(ReflectedDamage source) { REFLECTIONS.add(source); }

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
}
