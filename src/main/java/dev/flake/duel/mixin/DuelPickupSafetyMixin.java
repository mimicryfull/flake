package dev.flake.duel.mixin;

import dev.flake.duel.DuelEquipment;
import dev.flake.duel.DuelRuntime;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class DuelPickupSafetyMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void flake$noMixedItems(Player player, CallbackInfo ci) {
		var entity = (ItemEntity) (Object) this;
		if (DuelEquipment.isTemporary(entity.getItem())) { entity.discard(); ci.cancel(); }
		else if (DuelRuntime.protectedInventory(player.getUUID())) ci.cancel();
	}
}
