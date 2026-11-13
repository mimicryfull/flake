package dev.flake.combat.ability;

import dev.flake.combat.CombatFeedback;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.LoadoutService;
import net.minecraft.server.level.ServerPlayer;

/** Shared input boundary for class-slot commands and packets. */
public final class ClassAbilityApplication {
	private ClassAbilityApplication() {}

	public static int activate(ServerPlayer player, int slot) {
		if (!AbilitySlots.valid(slot)) return 0;
		var build = LoadoutService.of(player.level().getServer()).get(player.getUUID());
		var ability = AbilitySlots.resolve(build.fighterClass(), slot);
		if (!player.isAlive() || player.isSpectator()) {
			CombatFeedback.unavailable(player, ability);
			return 0;
		}
		if (DuelRuntime.blocksAbility(player.getUUID())) {
			CombatFeedback.blocked(player, ability);
			return 0;
		}
		var definition = dev.flake.content.ContentCatalog.fighterClass(build.fighterClass());
		if (definition == null) {
			CombatFeedback.unavailable(player, ability);
			return 0;
		}
		return definition.activate(player, slot);
	}
}
