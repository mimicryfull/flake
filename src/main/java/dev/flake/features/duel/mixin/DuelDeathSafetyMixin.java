package dev.flake.features.duel.mixin;

import dev.flake.features.duel.DuelRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class DuelDeathSafetyMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
	private void flake$keepOriginalsInJournal(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && DuelRuntime.protectedInventory(player.getUUID())) {
			player.getInventory().clearContent();
			ci.cancel();
		}
	}

	@Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
	private void flake$noDuelXp(ServerLevel level, CallbackInfoReturnable<Integer> ci) {
		if ((Object) this instanceof ServerPlayer player && DuelRuntime.protectedInventory(player.getUUID())) ci.setReturnValue(0);
	}
}
