package dev.flake.render.vfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.flake.features.chronotrident.ChronoTridentRuntime;
import dev.flake.features.chronotrident.client.ChronoPlantPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flips the held Chrono Trident upside-down in third-person and bounces it into the ground. */
@Mixin(ItemInHandLayer.class)
abstract class ChronoItemHandMixin<S extends ArmedEntityRenderState> {
	@Inject(
			method = "submitArmWithItem",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
			)
	)
	private void flake$flipChronoTridentInHand(
			S state,
			ItemStackRenderState itemState,
			ItemStack stack,
			HumanoidArm arm,
			PoseStack poseStack,
			SubmitNodeCollector collector,
			int packedLight,
			CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState avatar) || !ChronoTridentRuntime.isTrident(stack)) return;
		float w = ChronoPlantPose.weight(avatar.id);
		if (w <= 0.001f) return;

		float flip = ChronoPlantPose.flipProgress(avatar.id);
		float slam = ChronoPlantPose.slamSpring(avatar.id);
		float bounceDelta = slam - flip;
		// Flip prongs 180 deg downward toward the ground with springy impact recoil
		poseStack.translate(0.0f, -0.08f * slam, 0.06f * flip);
		poseStack.rotateDegrees(Axis.XP, -172.0f * flip - 14.0f * bounceDelta);
	}
}
