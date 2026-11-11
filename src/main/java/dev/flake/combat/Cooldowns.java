package dev.flake.combat;

import dev.flake.features.loadout.Loadout;
import net.minecraft.server.level.ServerPlayer;

/** Server-side skill cooldown scaling from passive items. */
public final class Cooldowns {
	private Cooldowns() {}

	/** Multiplier applied to skill cooldowns, e.g. 0.8 for «Плавлячий аметист». */
	public static float scale(ServerPlayer player) {
		return dev.flake.combat.CombatModifiers.cooldownScale(player);
	}

	/** Scaled cooldown length in ticks; a zero cooldown stays zero. */
	public static int ticks(ServerPlayer player, int baseTicks) {
		if (baseTicks <= 0) return 0;
		return Math.max(1, Math.round(baseTicks * scale(player)));
	}
}
