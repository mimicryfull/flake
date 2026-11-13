package dev.flake.features.duel.mixin;

import dev.flake.combat.CombatModifiers;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
abstract class EntityStepSoundMixin {
	@Redirect(
			method = "playStepSound",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;playSound(Lnet/minecraft/sounds/SoundEvent;FF)V")
	)
	private void flake$scaleStepSound(Entity instance, SoundEvent sound, float volume, float pitch) {
		instance.playSound(sound, volume * CombatModifiers.stepVolumeScale(instance), pitch);
	}
}
