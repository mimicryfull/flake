package dev.flake.features.hud.client.mixin;

import dev.flake.features.hud.client.StatusHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class EffectIconsMixin {
	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void flake$hideVanillaEffects(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		if (StatusHud.active()) ci.cancel();
	}
}
