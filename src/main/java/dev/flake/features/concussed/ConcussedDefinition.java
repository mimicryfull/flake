package dev.flake.features.concussed;

import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.features.loadout.Loadout.FighterClass;
import dev.flake.content.ContentSpec.ClassSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentCatalog;
import net.minecraft.server.level.ServerPlayer;

/** Class metadata and the only wiring needed to install Concussed. */
public final class ConcussedDefinition implements ClassSpec, Lifecycle {
	public static void install() {
		var feature = new ConcussedDefinition();
		ContentCatalog.registerClass(feature);
		ContentCatalog.registerLifecycle(feature);
	}
	@Override public FighterClass id() { return FighterClass.CONCUSSED; }
	@Override public String roleTitleKey() { return "flake.class.concussed.role"; }
	@Override public String skillSummaryKey() { return "flake.class.concussed.summary"; }
	@Override public Ability slot1() { return Ability.GRAVITATIONAL_DISSONANCE; }
	@Override public Ability slot2() { return Ability.ELECTRIC_QUAKE; }
	@Override public int activate(ServerPlayer player, int slot) { return ConcussedAbilities.cast(player, slot == 1 ? slot1() : slot2()); }
	@Override public int cooldownRemaining(ServerPlayer player, int slot, int now) {
		return slot == 1 ? ConcussedAbilities.cooldownRemainingTicks(player, now) : ConcussedAbilities.quakeCooldownRemainingTicks(player, now);
	}
	@Override public int cooldownTotal(ServerPlayer player, int slot, int now) {
		return slot == 1 ? ConcussedAbilities.shockwaveCooldownTotalTicks(player) : ConcussedAbilities.quakeCooldownTotalTicks(player);
	}
	@Override public int activeElapsed(ServerPlayer player, int slot, int now) {
		return slot == 1 ? ConcussedAbilities.chargeElapsedTicks(player, now) : ConcussedAbilities.quakeActiveElapsedTicks(player, now);
	}
	@Override public void register() { ConcussedAbilities.install(); }
	@Override public void reset(ServerPlayer player) { ConcussedAbilities.reset(player); }
}
