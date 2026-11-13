package dev.flake.render.vfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.flake.features.chaosbroadsword.client.BroadswordSpinPose;
import dev.flake.features.junglespear.client.SpearLungePose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person weapon ability transforms for Jungle Spear thrust and Chaos Broadsword heavy slash. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class SpearFirstPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
	private void flake$applyFirstPersonWeaponPose(
			PlayerRenderState playerRenderState,
			FirstPersonHandsAndItemsRenderState handsState,
			float xRot,
			float swingProgress,
			InteractionHand hand,
			float equipProgress,
			ItemStack stack,
			float swingAmount,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			int packedLight,
			CallbackInfo ci) {
		var player = Minecraft.getInstance().player;
		if (player == null) return;

		float spearW = SpearLungePose.weight(player.getId());
		if (spearW > 0.001f) {
			if (hand == InteractionHand.MAIN_HAND) {
				poseStack.translate(-0.18f * spearW, 0.10f * spearW, -0.38f * spearW);
				poseStack.rotateDegrees(Axis.XP, -42.0f * spearW);
				poseStack.rotateDegrees(Axis.YP, 16.0f * spearW);
				poseStack.rotateDegrees(Axis.ZP, 10.0f * spearW);
			} else {
				poseStack.translate(0.14f * spearW, -0.02f * spearW, -0.22f * spearW);
				poseStack.rotateDegrees(Axis.XP, -26.0f * spearW);
			}
		}

		float swordW = BroadswordSpinPose.weight(player.getId());
		if (swordW > 0.001f) {
			float spin = BroadswordSpinPose.spinProgress(player.getId());
			float sweep = -1.0f + spin * 2.0f; // -1 (right windup) -> +1 (full left follow-through)
			if (hand == InteractionHand.MAIN_HAND) {
				poseStack.translate((-0.24f - 0.34f * sweep) * swordW, -0.04f * swordW, -0.34f * swordW);
				poseStack.rotateDegrees(Axis.XP, -68.0f * swordW);
				poseStack.rotateDegrees(Axis.YP, (48.0f + 78.0f * sweep) * swordW);
				poseStack.rotateDegrees(Axis.ZP, 62.0f * swordW);
			} else {
				// Off-hand reaches across to grip the sword two-handed
				poseStack.translate((0.28f - 0.22f * sweep) * swordW, -0.06f * swordW, -0.30f * swordW);
				poseStack.rotateDegrees(Axis.XP, -42.0f * swordW);
				poseStack.rotateDegrees(Axis.YP, (-32.0f + 45.0f * sweep) * swordW);
			}
		}
	}
}
