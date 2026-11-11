package dev.flake.features.duel.mixin;

import dev.flake.features.duel.DuelRuntime;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class DuelPacketSafetyMixin {
	@Shadow public ServerPlayer player;

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void flake$lockedPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		// Vanilla schedules the packet back onto the server thread. Only filter that invocation.
		if (!player.level().getServer().isSameThread()) return;
		// Allow releasing bow, crossbow, shield, or consumable during an active fight.
		if (packet.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
				&& DuelRuntime.fighting(player.getUUID())) {
			return;
		}
		if (DuelRuntime.protectedInventory(player.getUUID())) {
			player.inventoryMenu.broadcastFullState();
			ci.cancel();
		}
	}

	@Inject(method = {"handleContainerClick", "handleContainerButtonClick", "handlePlaceRecipe", "handleSetCreativeModeSlot",
			"handlePickItemFromBlock", "handlePickItemFromEntity", "handleBundleItemSelectedPacket"}, at = @At("HEAD"), cancellable = true)
	private void flake$lockedInventory(CallbackInfo ci) {
		// Vanilla schedules the packet back onto the server thread. Only filter that invocation.
		if (!player.level().getServer().isSameThread()) return;
		if (DuelRuntime.protectedInventory(player.getUUID())) {
			player.inventoryMenu.broadcastFullState();
			ci.cancel();
		}
	}
}
