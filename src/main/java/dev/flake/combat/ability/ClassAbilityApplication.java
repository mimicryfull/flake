package dev.flake.combat.ability;

import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.LoadoutService;
import net.minecraft.server.level.ServerPlayer;

/** Shared input boundary for class-slot commands and packets. */
public final class ClassAbilityApplication {
	private ClassAbilityApplication() {}

	public static int activate(ServerPlayer player, int slot) {
		if (!AbilitySlots.valid(slot)) return 0;
		if (!player.isAlive() || player.isSpectator() || DuelRuntime.blocksAbility(player.getUUID())) return 0;
		var build = LoadoutService.of(player.level().getServer()).get(player.getUUID());
		var definition = dev.flake.content.ContentCatalog.fighterClass(build.fighterClass());
		return definition == null ? 0 : definition.activate(player, slot);
	}
}
