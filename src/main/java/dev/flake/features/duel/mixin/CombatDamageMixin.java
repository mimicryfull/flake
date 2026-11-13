package dev.flake.features.duel.mixin;

import dev.flake.combat.CombatModifiers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class CombatDamageMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void flake$gateDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
		if (!CombatModifiers.allowDamage((LivingEntity) (Object) this, source)) ci.setReturnValue(false);
	}

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float flake$scaleDamage(float amount, ServerLevel level, DamageSource source) {
		return CombatModifiers.modifyDamage((LivingEntity) (Object) this, source, amount);
	}

	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void flake$gateEffect(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> ci) {
		if (!CombatModifiers.allowEffect((LivingEntity) (Object) this, effect)) ci.setReturnValue(false);
	}
}
