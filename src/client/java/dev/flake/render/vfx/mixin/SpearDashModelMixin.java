package dev.flake.render.vfx.mixin;

import dev.flake.features.junglespear.client.SpearLungePose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Procedural lunge pose for Jungle Spear: both arms thrust forward with the spear,
 * one leg braces underneath while the other kicks high behind, then smoothly recovers.
 */
@Mixin(PlayerModel.class)
abstract class SpearDashModelMixin extends HumanoidModel<AvatarRenderState> {
	private SpearDashModelMixin(ModelPart root) {
		super(root);
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void flake$applySpearLungePose(AvatarRenderState state, CallbackInfo ci) {
		float w = SpearLungePose.weight(state.id);
		if (w <= 0.001f) return;

		// Head looks straight ahead, compensating for the 42-degree forward body tilt
		this.head.xRot = lerp(this.head.xRot, -0.58f, w);

		// Thrust both arms forward along the dash direction
		this.rightArm.xRot = lerp(this.rightArm.xRot, -1.28f, w);
		this.rightArm.yRot = lerp(this.rightArm.yRot, -0.18f, w);
		this.rightArm.zRot = lerp(this.rightArm.zRot, 0.06f, w);

		this.leftArm.xRot = lerp(this.leftArm.xRot, -1.16f, w);
		this.leftArm.yRot = lerp(this.leftArm.yRot, 0.40f, w);
		this.leftArm.zRot = lerp(this.leftArm.zRot, -0.12f, w);

		// Right leg braces tucked underneath; left leg kicks high up behind
		this.rightLeg.xRot = lerp(this.rightLeg.xRot, -0.56f, w);
		this.rightLeg.zRot = lerp(this.rightLeg.zRot, 0.06f, w);

		this.leftLeg.xRot = lerp(this.leftLeg.xRot, 1.08f, w);
		this.leftLeg.zRot = lerp(this.leftLeg.zRot, -0.20f, w);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
