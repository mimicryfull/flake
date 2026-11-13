package dev.flake.render.vfx.mixin;

import dev.flake.features.chaosbroadsword.client.BroadswordSpinPose;
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
 * Procedural third-person poses for Jungle Spear lunge and Chaos Broadsword two-handed spin sweep.
 */
@Mixin(PlayerModel.class)
abstract class SpearDashModelMixin extends HumanoidModel<AvatarRenderState> {
	private SpearDashModelMixin(ModelPart root) {
		super(root);
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void flake$applyWeaponPoses(AvatarRenderState state, CallbackInfo ci) {
		float spearW = SpearLungePose.weight(state.id);
		if (spearW > 0.001f) {
			this.head.xRot = lerp(this.head.xRot, -0.58f, spearW);

			this.rightArm.xRot = lerp(this.rightArm.xRot, -1.28f, spearW);
			this.rightArm.yRot = lerp(this.rightArm.yRot, -0.18f, spearW);
			this.rightArm.zRot = lerp(this.rightArm.zRot, 0.06f, spearW);

			this.leftArm.xRot = lerp(this.leftArm.xRot, -1.16f, spearW);
			this.leftArm.yRot = lerp(this.leftArm.yRot, 0.40f, spearW);
			this.leftArm.zRot = lerp(this.leftArm.zRot, -0.12f, spearW);

			this.rightLeg.xRot = lerp(this.rightLeg.xRot, -0.56f, spearW);
			this.rightLeg.zRot = lerp(this.rightLeg.zRot, 0.06f, spearW);

			this.leftLeg.xRot = lerp(this.leftLeg.xRot, 1.08f, spearW);
			this.leftLeg.zRot = lerp(this.leftLeg.zRot, -0.20f, spearW);
		}

		float swordW = BroadswordSpinPose.weight(state.id);
		if (swordW > 0.001f) {
			this.head.xRot = lerp(this.head.xRot, -0.18f, swordW);

			// Two-handed heavy broadsword grip extended horizontally forward into the spin
			this.rightArm.xRot = lerp(this.rightArm.xRot, -1.48f, swordW);
			this.rightArm.yRot = lerp(this.rightArm.yRot, 0.34f, swordW);
			this.rightArm.zRot = lerp(this.rightArm.zRot, -0.55f, swordW);

			this.leftArm.xRot = lerp(this.leftArm.xRot, -1.40f, swordW);
			this.leftArm.yRot = lerp(this.leftArm.yRot, -0.58f, swordW);
			this.leftArm.zRot = lerp(this.leftArm.zRot, 0.25f, swordW);

			// Wide, deeply planted stance bracing into the ground
			this.rightLeg.xRot = lerp(this.rightLeg.xRot, -0.54f, swordW);
			this.rightLeg.zRot = lerp(this.rightLeg.zRot, 0.26f, swordW);

			this.leftLeg.xRot = lerp(this.leftLeg.xRot, 0.50f, swordW);
			this.leftLeg.zRot = lerp(this.leftLeg.zRot, -0.28f, swordW);
		}
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
