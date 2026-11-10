package dev.flake.duel.mixin;

import dev.flake.duel.DuelEquipment;
import dev.flake.duel.DuelRuntime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class DuelArrowSafetyMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void flake$noTemporaryAmmo(Player player, CallbackInfo ci) {
		if (DuelRuntime.protectedInventory(player.getUUID())
				|| DuelEquipment.isTemporary(((AbstractArrow) (Object) this).getPickupItemStackOrigin())) ci.cancel();
	}
}
