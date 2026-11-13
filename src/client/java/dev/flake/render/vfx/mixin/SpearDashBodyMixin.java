package dev.flake.render.vfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.flake.features.chaosbroadsword.client.BroadswordSpinPose;
import dev.flake.features.junglespear.client.SpearLungePose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tilts and spins the player body during Jungle Spear lunge and Chaos Broadsword spin-sweep. */
@Mixin(AvatarRenderer.class)
abstract class SpearDashBodyMixin {
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void flake$tiltBodyOnWeaponAbility(AvatarRenderState state, PoseStack poseStack, float ageInTicks, float bodyYRot, CallbackInfo ci) {
		float spearW = SpearLungePose.weight(state.id);
		if (spearW > 0.001f) {
			poseStack.translate(0.0f, 0.24f * spearW, 0.0f);
			poseStack.rotateDegrees(Axis.XP, -42.0f * spearW);
		}

		float swordW = BroadswordSpinPose.weight(state.id);
		if (swordW > 0.001f) {
			float spinDeg = BroadswordSpinPose.spinDegrees(state.id);
			// Heavy low crouch pressing into the ground with deep forward lean and weighted 360 spin
			poseStack.translate(0.0f, -0.26f * swordW, 0.0f);
			poseStack.rotateDegrees(Axis.YP, spinDeg);
			poseStack.rotateDegrees(Axis.XP, -38.0f * swordW);
			poseStack.rotateDegrees(Axis.ZP, 14.0f * swordW);
		}
	}
}
