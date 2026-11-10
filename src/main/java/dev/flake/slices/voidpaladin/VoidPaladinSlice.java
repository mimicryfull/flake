package dev.flake.slices.voidpaladin;

import dev.flake.build.Loadout.Weapon;
import dev.flake.duel.VoidPaladin;
import dev.flake.shared.CombatSlice.Lifecycle;
import dev.flake.shared.CombatSlice.WeaponSpec;
import dev.flake.shared.SliceRegistry;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Порожній Паладин» (Void Paladin): weapon metadata, kit stack, and server lifecycle. */
public final class VoidPaladinSlice implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.VOID_PALADIN,
			"flake.weapon.void_paladin.short",
			0,
			0,
			() -> VoidPaladin.stack()
	);

	private VoidPaladinSlice() {}

	public static void install() {
		SliceRegistry.registerWeapon(SPEC);
		SliceRegistry.registerLifecycle(new VoidPaladinSlice());
	}

	@Override public void register() { VoidPaladin.register(); }
	@Override public void reset(ServerPlayer player) { VoidPaladin.reset(player); }
}
