package dev.flake.slices.crystalclaw;

import dev.flake.build.Loadout.ActiveItem;
import dev.flake.duel.CrystalClaw;
import dev.flake.shared.CombatSlice.ActiveItemSpec;
import dev.flake.shared.CombatSlice.Lifecycle;
import dev.flake.shared.SliceRegistry;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Кристалевий кіготь»: item metadata, buff/cooldown timings, kit stack, and server lifecycle. */
public final class CrystalClawSlice implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.CRYSTAL_CLAW,
			"flake.active.crystal_claw.short",
			CrystalClaw.COOLDOWN_TICKS,
			CrystalClaw.BUFF_TICKS,
			false,
			CrystalClaw::stack
	);

	private CrystalClawSlice() {}

	public static void install() {
		SliceRegistry.registerActive(SPEC);
		SliceRegistry.registerLifecycle(new CrystalClawSlice());
	}

	@Override public void register() { CrystalClaw.register(); }
	@Override public void reset(ServerPlayer player) { CrystalClaw.reset(player); }
}
