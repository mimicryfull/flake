package dev.flake.features.training;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

/** Shared training-target identity and physics setup used by commands and ability hits. */
public final class TrainingDummies {
	public static final String TAG = "flake_test_dummy";
	private TrainingDummies() {}

	public static void prepare(Mob dummy) {
		// NoAI disables movement physics in 26.3, swallowing knockback.
		dummy.setNoAi(false);
		dummy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, -1, 10, false, false, false));
	}
}
