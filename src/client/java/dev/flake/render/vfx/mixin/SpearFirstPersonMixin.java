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
			float swing = BroadswordSpinPose.swingProgress(player.getId());
			float sweep = -1.0f + swing * 2.0f; // windup right -> heavy flat sweep across to the left
			if (hand == InteractionHand.MAIN_HAND) {
				poseStack.translate((-0.26f - 0.38f * sweep) * swordW, -0.10f * swordW, -0.38f * swordW);
				// Lay the broadsword flat and parallel to the ground
				poseStack.rotateDegrees(Axis.XP, -86.0f * swordW);
				poseStack.rotateDegrees(Axis.YP, (46.0f + 86.0f * sweep) * swordW);
				poseStack.rotateDegrees(Axis.ZP, 84.0f * swordW);
			} else {
				poseStack.translate((0.28f - 0.24f * sweep) * swordW, -0.10f * swordW, -0.32f * swordW);
				poseStack.rotateDegrees(Axis.XP, -52.0f * swordW);
				poseStack.rotateDegrees(Axis.YP, (-32.0f + 52.0f * sweep) * swordW);
			}
		}
	}
}
