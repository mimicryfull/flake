package dev.flake.render.vfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.flake.features.junglespear.client.SpearLungePose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tilts the entire player body forward during a Jungle Spear lunge dash. */
@Mixin(AvatarRenderer.class)
abstract class SpearDashBodyMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void flake$tiltBodyOnSpearDash(AvatarRenderState state, PoseStack poseStack, float ageInTicks, float bodyYRot, CallbackInfo ci) {
		float w = SpearLungePose.weight(state.id);
		if (w <= 0.001f) return;

		poseStack.translate(0.0f, 0.24f * w, 0.0f);
		poseStack.rotateDegrees(Axis.XP, -42.0f * w);
	}
}
