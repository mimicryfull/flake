package dev.flake.slices.darkcube;

import dev.flake.build.Loadout.ActiveItem;
import dev.flake.duel.DarkCube;
import dev.flake.shared.CombatSlice.ActiveItemSpec;
import dev.flake.shared.CombatSlice.Lifecycle;
import dev.flake.shared.SliceRegistry;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Куб темряви» (Cube of Darkness): item metadata, cooldown, kit stack, and server lifecycle. */
public final class DarkCubeSlice implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.DARK_CUBE,
			"flake.active.dark_cube.short",
			DarkCube.COOLDOWN_TICKS,
			0,
			false,
			() -> DarkCube.stack()
	);

	private DarkCubeSlice() {}

	public static void install() {
		SliceRegistry.registerActive(SPEC);
		SliceRegistry.registerLifecycle(new DarkCubeSlice());
	}

	@Override public void register() { DarkCube.register(); }
	@Override public void reset(ServerPlayer player) { DarkCube.reset(player); }
}
