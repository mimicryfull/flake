package dev.flake.duel;

import dev.flake.shared.FlakeI18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Short action-bar reason so a failed ability press is never silent. */
public final class CombatFeedback {
	private CombatFeedback() {}

	public static void actionBar(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(Component.literal(FlakeI18n.tr(key, args)), true);
	}

	public static void unavailable(ServerPlayer player) {
		actionBar(player, "flake.feedback.unavailable");
	}

	public static void onCooldown(ServerPlayer player, int ticks) {
		float seconds = Math.max(0.1f, ticks / 20f);
		actionBar(player, "flake.feedback.cooldown", String.format(java.util.Locale.ROOT, "%.1f", seconds));
	}

	public static void noTarget(ServerPlayer player) {
		actionBar(player, "flake.feedback.no_target");
	}

	public static void blocked(ServerPlayer player) {
		if (ControlHammer.isStunned(player.getUUID())) actionBar(player, "flake.feedback.stunned");
		else if (DuelRuntime.protectedInventory(player.getUUID()) && !DuelRuntime.fighting(player.getUUID()))
			actionBar(player, "flake.feedback.inventory_locked");
		else actionBar(player, "flake.feedback.wait_start");
	}
}