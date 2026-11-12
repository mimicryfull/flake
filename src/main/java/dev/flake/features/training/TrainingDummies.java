package dev.flake.features.training;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Shared training-target identity, durability, and physics setup used by commands and ability hits. */
public final class TrainingDummies {
	public static final String TAG = "flake_test_dummy";
	private static final double MAX_HEALTH = 200.0;
	private static boolean installed;

	private TrainingDummies() {}

	public static void install() {
		if (installed) return;
		installed = true;
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Mob mob) || !mob.entityTags().contains(TAG)) return true;
			if (mob.getMaxHealth() < 100.0f) prepare(mob);
			if (mob.getHealth() - amount < 1.0f) mob.setHealth(mob.getMaxHealth());
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (blocked || !(entity instanceof Mob mob) || !mob.entityTags().contains(TAG)) return;
			updateLabel(mob);
		});
	}

	public static void prepare(Mob dummy) {
		// NoAI disables movement physics in 26.3, swallowing knockback.
		dummy.setNoAi(false);
		var maxHealth = dummy.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null && maxHealth.getBaseValue() < MAX_HEALTH) maxHealth.setBaseValue(MAX_HEALTH);
		dummy.setHealth(dummy.getMaxHealth());
		dummy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, -1, 10, false, false, false));
		dummy.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 1, false, false, false));
		updateLabel(dummy);
	}

	private static void updateLabel(Mob dummy) {
		dummy.setCustomName(Component.literal("Тестовий манекен Flake · " + Math.round(dummy.getHealth()) + "/" + Math.round(dummy.getMaxHealth())));
		dummy.setCustomNameVisible(true);
	}
}
