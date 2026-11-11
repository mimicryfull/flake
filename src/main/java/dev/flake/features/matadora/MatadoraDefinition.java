package dev.flake.features.matadora;

import dev.flake.features.loadout.Loadout.ActiveItem;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentCatalog;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Матадора» (Matadora Mask): item metadata, buff/cooldown timings, kit stack, and server lifecycle. */
public final class MatadoraDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.MATADORA,
			"flake.active.matadora.short",
			MatadoraRuntime.COOLDOWN_TICKS,
			MatadoraRuntime.BUFF_TICKS,
			false,
			MatadoraRuntime::stack,
			"matadora"
	);

	private MatadoraDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new MatadoraDefinition());
		dev.flake.combat.CombatStatuses.register((player, now, statuses) -> {
			int remaining = MatadoraRuntime.remaining(player, now);
			if (remaining > 0) statuses.add(new dev.flake.combat.CombatStatuses.Status("matadora", remaining, MatadoraRuntime.BUFF_TICKS, 1));
		});
	}

	public static void registerItems() {
		MatadoraRuntime.bind(dev.flake.content.ItemRegistration.active(MatadoraRuntime.ID, (player, target) -> MatadoraRuntime.activate(player)));
		MatadoraRuntime.bindSound(dev.flake.content.ItemRegistration.sound(MatadoraRuntime.SOUND_ID));
	}

	@Override public void register() { MatadoraRuntime.register(); }
	@Override public void reset(ServerPlayer player) { MatadoraRuntime.reset(player); }
}
