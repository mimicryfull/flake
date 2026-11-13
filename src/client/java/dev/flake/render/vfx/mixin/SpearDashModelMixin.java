package dev.flake.render.vfx.mixin;

import dev.flake.features.chaosbroadsword.client.BroadswordSpinPose;
import dev.flake.features.chronotrident.client.ChronoPlantPose;
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
			this.head.xRot = lerp(this.head.xRot, -0.36f, swordW);

			// Two-handed heavy grip bent flat parallel to the ground despite the 38-deg forward body lean
			this.rightArm.xRot = lerp(this.rightArm.xRot, -1.32f, swordW);
			this.rightArm.yRot = lerp(this.rightArm.yRot, 0.42f, swordW);
			this.rightArm.zRot = lerp(this.rightArm.zRot, -1.34f, swordW);

			this.leftArm.xRot = lerp(this.leftArm.xRot, -1.26f, swordW);
			this.leftArm.yRot = lerp(this.leftArm.yRot, -0.68f, swordW);
			this.leftArm.zRot = lerp(this.leftArm.zRot, 0.48f, swordW);

			// Deeply braced legs digging into the ground
			this.rightLeg.xRot = lerp(this.rightLeg.xRot, -0.78f, swordW);
			this.rightLeg.zRot = lerp(this.rightLeg.zRot, 0.30f, swordW);

			this.leftLeg.xRot = lerp(this.leftLeg.xRot, 0.72f, swordW);
			this.leftLeg.zRot = lerp(this.leftLeg.zRot, -0.32f, swordW);
		}

		float chronoW = ChronoPlantPose.weight(state.id);
		if (chronoW > 0.001f) {
			float lift = ChronoPlantPose.lift(state.id);
			float slam = ChronoPlantPose.slamSpring(state.id);
			float targetRightArmX = lerp(-2.35f, -0.58f, Math.clamp(slam, 0.0f, 1.35f));
			float targetLeftArmX = lerp(-1.85f, -0.78f, Math.clamp(slam, 0.0f, 1.35f));
			float armBlend = Math.max(lift, Math.min(1.0f, chronoW));

			this.head.xRot = lerp(this.head.xRot, -0.20f * lift + 0.44f * slam, chronoW);

			this.rightArm.xRot = lerp(this.rightArm.xRot, targetRightArmX, armBlend);
			this.rightArm.yRot = lerp(this.rightArm.yRot, -0.18f, chronoW);
			this.rightArm.zRot = lerp(this.rightArm.zRot, 0.14f, chronoW);

			this.leftArm.xRot = lerp(this.leftArm.xRot, targetLeftArmX, armBlend);
			this.leftArm.yRot = lerp(this.leftArm.yRot, 0.48f, chronoW);
			this.leftArm.zRot = lerp(this.leftArm.zRot, -0.16f, chronoW);

			this.rightLeg.xRot = lerp(this.rightLeg.xRot, -0.62f * slam + 0.25f * lift, chronoW);
			this.rightLeg.zRot = lerp(this.rightLeg.zRot, 0.20f * slam, chronoW);

			this.leftLeg.xRot = lerp(this.leftLeg.xRot, 0.54f * slam - 0.20f * lift, chronoW);
			this.leftLeg.zRot = lerp(this.leftLeg.zRot, -0.20f * slam, chronoW);
		}
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
