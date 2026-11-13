package dev.flake.render.vfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

/** First-person spear lunge thrust during Jungle Spear dash. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class SpearFirstPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
	private void flake$thrustSpearFirstPerson(
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
		float w = SpearLungePose.weight(player.getId());
		if (w <= 0.001f) return;

		if (hand == InteractionHand.MAIN_HAND) {
			poseStack.translate(-0.18f * w, 0.10f * w, -0.38f * w);
			poseStack.rotateDegrees(Axis.XP, -42.0f * w);
			poseStack.rotateDegrees(Axis.YP, 16.0f * w);
			poseStack.rotateDegrees(Axis.ZP, 10.0f * w);
		} else {
			poseStack.translate(0.14f * w, -0.02f * w, -0.22f * w);
			poseStack.rotateDegrees(Axis.XP, -26.0f * w);
		}
	}
}
