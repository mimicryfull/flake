package dev.flake.render.vfx.mixin;

import dev.flake.features.vampire.client.GhoulRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
abstract class GhoulFovMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void flake$addBloodlustFov(boolean firstPerson, float fovEffectScale, CallbackInfoReturnable<Float> cir) {
		if ((Object) this != Minecraft.getInstance().player) return;
		float boost = GhoulRenderState.localBloodlustFovBoost();
		if (boost > 0f) {
			cir.setReturnValue(cir.getReturnValue() + boost);
		}
	}
}
