package dev.flake.slices.controlhammer;

import dev.flake.build.Loadout.Weapon;
import dev.flake.duel.ControlHammer;
import dev.flake.shared.CombatSlice.Lifecycle;
import dev.flake.shared.CombatSlice.WeaponSpec;
import dev.flake.shared.SliceRegistry;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Молот контролю» (Control Hammer): weapon metadata, kit stack, cooldown, and server lifecycle. */
public final class ControlHammerSlice implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.CONTROL_HAMMER,
			"flake.weapon.control_hammer.short",
			ControlHammer.COOLDOWN_TICKS,
			0,
			() -> ControlHammer.stack()
	);

	private ControlHammerSlice() {}

	public static void install() {
		SliceRegistry.registerWeapon(SPEC);
		SliceRegistry.registerLifecycle(new ControlHammerSlice());
	}

	@Override public void register() { ControlHammer.register(); }
	@Override public void reset(ServerPlayer player) { ControlHammer.reset(player); }
}
