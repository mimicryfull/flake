package dev.flake.duel.mixin;

import dev.flake.duel.DuelRuntime;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class DuelPlayerSafetyMixin {
	@Inject(method = "openMenu", at = @At("HEAD"), cancellable = true)
	private void flake$noContainers(MenuProvider menu, CallbackInfoReturnable<OptionalInt> ci) {
		if (DuelRuntime.protectedInventory(((ServerPlayer) (Object) this).getUUID())) ci.setReturnValue(OptionalInt.empty());
	}

	@Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
	private void flake$noDrop(boolean all, CallbackInfo ci) {
		var player = (ServerPlayer) (Object) this;
		if (DuelRuntime.protectedInventory(player.getUUID())) { player.inventoryMenu.broadcastFullState(); ci.cancel(); }
	}

	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void flake$noGearEntity(ItemStack stack, boolean random, Prediction prediction, CallbackInfoReturnable<ItemEntity> ci) {
		if (DuelRuntime.protectedInventory(((ServerPlayer) (Object) this).getUUID())) ci.setReturnValue(null);
	}
}
