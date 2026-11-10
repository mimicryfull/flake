package dev.flake.client.ui.mixin;

import dev.flake.client.ui.FlakeHudOverlay;
import dev.flake.client.ui.FlakeTabOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Also allow Tab for a lone integrated-server player (vanilla normally suppresses it). */
@Mixin(Hud.class)
public abstract class HudTabOverlayMixin {
	@Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
	private void flake$customTab(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
		var client = Minecraft.getInstance();
		FlakeHudOverlay.extract(graphics);
		boolean visible = client.options.keyPlayerList.isDown();
		((Hud) (Object) this).getTabList().setVisible(visible);
		if (visible) { graphics.nextStratum(); FlakeTabOverlay.extract(graphics); }
		ci.cancel();
	}
}
