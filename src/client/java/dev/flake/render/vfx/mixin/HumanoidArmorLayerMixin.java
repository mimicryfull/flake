package dev.flake.render.vfx.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.flake.features.armorsets.client.ArmorSet3DRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces flat vanilla armor rendering with true 3D custom armor geometry for Phoenix, Barrel, Wizard, and Hydra sets. */
@Mixin(HumanoidArmorLayer.class)
abstract class HumanoidArmorLayerMixin<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>>
		extends RenderLayer<S, M> {
	protected HumanoidArmorLayerMixin(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	@Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
	private void flake$renderCustomArmor3D(
			PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack,
			EquipmentSlot slot, int packedLight, S renderState, CallbackInfo ci) {
		if (ArmorSet3DRenderer.tryRender(poseStack, collector, this.getParentModel(), stack, slot, packedLight)) {
			ci.cancel();
		}
	}
}
