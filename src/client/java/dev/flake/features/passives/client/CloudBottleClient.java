package dev.flake.features.passives.client;

import dev.flake.client.network.FlakeUiClient;
import dev.flake.features.hud.HudStatePayload;
import dev.flake.features.passives.CloudBottleRuntime;
import dev.flake.features.passives.CloudJumpPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Client-side input handler for Cloud in a Bottle double jump. */
public final class CloudBottleClient {
	private static boolean airJumpUsed;
	private static boolean releasedInAir;

	private CloudBottleClient() {}

	public static void clear() {
		airJumpUsed = false;
		releasedInAir = false;
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.isPaused()) return;

		if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()
				|| player.getAbilities().flying || player.isSpectator() || !player.isAlive()) {
			airJumpUsed = false;
			releasedInAir = false;
			return;
		}

		boolean jumpDown = client.options.keyJump.isDown();
		if (!jumpDown) {
			releasedInAir = true;
			return;
		}
		if (!releasedInAir || airJumpUsed || !canClientDoubleJump(player)) return;

		airJumpUsed = true;
		Vec3 vel = player.getDeltaMovement();
		player.setDeltaMovement(vel.x, CloudBottleRuntime.DOUBLE_JUMP_Y_VEL, vel.z);
		player.resetFallDistance();

		if (ClientPlayNetworking.canSend(CloudJumpPayload.TYPE)) {
			ClientPlayNetworking.send(CloudJumpPayload.INSTANCE);
		}
	}

	private static boolean canClientDoubleJump(LocalPlayer player) {
		var hud = FlakeUiClient.hud();
		if (hud != null) {
			if (hud.abilityBlocked()) return false;
			if ("CLOUD_BOTTLE".equals(hud.passiveItem())) return true;
			if (hud.viewerRole() != HudStatePayload.Role.NONE) return false;
		}
		if (CloudBottleRuntime.ITEM == null) return false;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(CloudBottleRuntime.ITEM)) return true;
		}
		return false;
	}
}
