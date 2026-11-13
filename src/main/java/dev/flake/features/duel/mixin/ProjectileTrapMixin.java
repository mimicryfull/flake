package dev.flake.features.duel.mixin;

import dev.flake.combat.CombatModifiers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
abstract class ProjectileTrapMixin {
	@Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
	private void flake$interceptProjectile(EntityHitResult hitResult, CallbackInfo ci) {
		if (hitResult.getEntity() instanceof LivingEntity victim
				&& CombatModifiers.interceptProjectile((Projectile) (Object) this, victim)) {
			ci.cancel();
		}
	}
}
