package dev.flake.features.deadlybleed;

import dev.flake.combat.Cooldowns;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.features.duel.DuelRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * «Смертельна кровотеча»: 7 базової шкоди. ПКМ завдає крит-удар на 200% базової
 * шкоди з помаранчевою анімацією криту. Ціль і результат визначає сервер.
 */
public final class DeadlyBleedRuntime {
	public static final int COOLDOWN_TICKS = 20;
	public static final float BASE_DAMAGE = 7.0f;
	public static final float CRIT_MULTIPLIER = 2.0f;
	public static final double AIM_COS = 0.6;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "deadly_bleed");
	public static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath("flake", "deadly_bleed_crit");

	private static SoundEvent sound;

	private DeadlyBleedRuntime() {}

	public static Item ITEM;

	static void bind(Item item) { ITEM = item; }
	static void bindSound(SoundEvent event) { sound = event; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isBlade(ItemStack stack) { return ITEM != null && stack.is(ITEM); }

	public static boolean crit(ServerPlayer player, Entity directTarget) {
		if (DuelRuntime.blocksAbility(player.getUUID()) || !(player.level() instanceof ServerLevel level)) return false;
		LivingEntity target = null;
		if (directTarget instanceof LivingEntity living && validTarget(player, living)
				&& player.isWithinEntityInteractionRange(living, 1.0)) target = living;
		if (target == null) target = AbilityTargeting.target(player, player.entityInteractionRange(), AIM_COS, true);
		if (target == null) {
			dev.flake.combat.CombatFeedback.noTarget(player, stack());
			return false;
		}

		player.getCooldowns().addCooldown(ID, Cooldowns.ticks(player, COOLDOWN_TICKS));
		float damage = Math.max(BASE_DAMAGE, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)) * CRIT_MULTIPLIER;
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		DeadlyBleedVisuals.crit(player, target);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.05f);
		return true;
	}

	public static boolean hasClientTarget(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double range = player.entityInteractionRange();
		var box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		for (var entity : player.level().getEntities(player, box, candidate -> candidate instanceof LivingEntity living && living.isAlive())) {
			Vec3 aim = entity.position().add(0, entity.getBbHeight() * 0.6, 0);
			Vec3 delta = aim.subtract(eye);
			double distance = delta.length();
			if (distance <= range && distance >= 0.3 && delta.scale(1.0 / distance).dot(look) >= AIM_COS) return true;
		}
		return false;
	}

	private static boolean validTarget(ServerPlayer player, LivingEntity living) {
		if (!living.isAlive() || living == player) return false;
		if (DuelRuntime.fighting(player.getUUID())) {
			return living instanceof ServerPlayer other && DuelRuntime.opponents(player.getUUID(), other.getUUID());
		}
		return living instanceof Player || (living instanceof Mob mob && mob.entityTags().contains("flake_test_dummy"));
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
	}
}
