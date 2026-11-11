package dev.flake.combat.ability;

import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.features.loadout.Loadout.FighterClass;
import dev.flake.content.ContentSpec.ClassSpec;
import java.util.UUID;

/** Metadata adapter while the shared ClassSpec contract still exposes UUID-only HUD queries. */
public record RuntimeClassSpec(FighterClass id, String roleTitleKey, String skillSummaryKey,
		Ability slot1, Ability slot2) implements ClassSpec {
	@Override public int activate(net.minecraft.server.level.ServerPlayer player, int slot) {
		return ClassAbilityRuntimes.of(player.level().getServer()).cast(player, slot == 1 ? slot1 : slot2, slot);
	}

	@Override public int cooldownRemaining(net.minecraft.server.level.ServerPlayer player, int slot, int now) {
		var state = ClassAbilityRuntimes.find(player.level().getServer());
		return state == null ? 0 : state.remaining(player.getUUID(), slot, now);
	}

	@Override public int cooldownTotal(net.minecraft.server.level.ServerPlayer player, int slot, int now) {
		var state = ClassAbilityRuntimes.find(player.level().getServer());
		int stored = state == null ? 0 : state.cooldownTotal(player.getUUID(), slot);
		return stored > 0 ? stored : (slot == 1 ? slot1 : slot2).cooldownTicks();
	}

	@Override public int activeElapsed(net.minecraft.server.level.ServerPlayer player, int slot, int now) {
		var state = ClassAbilityRuntimes.find(player.level().getServer());
		return state == null ? -1 : state.activeElapsed(player.getUUID(), slot, now);
	}
}
