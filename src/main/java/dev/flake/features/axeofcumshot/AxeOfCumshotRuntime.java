package dev.flake.features.axeofcumshot;

import dev.flake.combat.Cooldowns;
import dev.flake.combat.Stuns;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * «Сокира Кумшоту»: активний артефакт (ПКМ). Кидає сокиру в ціль; влучання оглушує
 * на 1.5 секунди. Ціль, шкода, оглушення та відкат визначаються сервером.
 */
public final class AxeOfCumshotRuntime {
	public static final int COOLDOWN_TICKS = 180;
	public static final int STUN_TICKS = 30;
	public static final float DAMAGE = 4.0f;
	public static final double RANGE = 18.0;
	/** Широкий конус, щоб кидок не смикався під час динамічного PvP. */
	public static final double AIM_COS = 0.72;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "axe_of_cumshot");
	public static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath("flake", "axe_of_cumshot_activate");

	private static SoundEvent sound;

	private AxeOfCumshotRuntime() {}

	public static Item ITEM;

	static void bind(Item item) { ITEM = item; }
	static void bindSound(SoundEvent event) { sound = event; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isAxe(ItemStack stack) { return ITEM != null && stack.is(ITEM); }

	public static boolean throwAxe(ServerPlayer player, Entity directTarget) {
		if (DuelRuntime.blocksAbility(player.getUUID()) || !(player.level() instanceof ServerLevel level)) return false;
		LivingEntity target = null;
		if (directTarget instanceof LivingEntity living && validTarget(player, living)) target = living;
		if (target == null) target = AbilityTargeting.target(player, RANGE, AIM_COS, true);
		if (target == null) return false;

		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		player.getCooldowns().addCooldown(ID, cooldown);
		Stuns.apply(target, STUN_TICKS);
		target.hurtServer(level, player.damageSources().playerAttack(player), DAMAGE);
		AxeOfCumshotVisuals.throwAxe(player, target);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
		return true;
	}

	public static boolean hasClientTarget(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		var box = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(2.0);
		for (var entity : player.level().getEntities(player, box, candidate -> candidate instanceof LivingEntity living && living.isAlive())) {
			Vec3 aim = entity.position().add(0, entity.getBbHeight() * 0.6, 0);
			Vec3 delta = aim.subtract(eye);
			double distance = delta.length();
			if (distance <= RANGE && distance >= 0.3 && delta.scale(1.0 / distance).dot(look) >= AIM_COS) return true;
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
