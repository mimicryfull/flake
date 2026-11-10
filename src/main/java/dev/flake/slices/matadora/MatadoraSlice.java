package dev.flake.slices.matadora;

import dev.flake.build.Loadout.ActiveItem;
import dev.flake.duel.Matadora;
import dev.flake.shared.CombatSlice.ActiveItemSpec;
import dev.flake.shared.CombatSlice.Lifecycle;
import dev.flake.shared.SliceRegistry;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Матадора» (Matadora Mask): item metadata, buff/cooldown timings, kit stack, and server lifecycle. */
public final class MatadoraSlice implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.MATADORA,
			"flake.active.matadora.short",
			Matadora.COOLDOWN_TICKS,
			Matadora.BUFF_TICKS,
			false,
			() -> Matadora.stack()
	);

	private MatadoraSlice() {}

	public static void install() {
		SliceRegistry.registerActive(SPEC);
		SliceRegistry.registerLifecycle(new MatadoraSlice());
	}

	@Override public void register() { Matadora.register(); }
	@Override public void reset(ServerPlayer player) { Matadora.reset(player); }
}
