package dev.flake.features.crystalclaw;

import dev.flake.features.loadout.Loadout.ActiveItem;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentCatalog;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Кристалевий кіготь»: item metadata, buff/cooldown timings, kit stack, and server lifecycle. */
public final class CrystalClawDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.CRYSTAL_CLAW,
			"flake.active.crystal_claw.short",
			CrystalClawRuntime.COOLDOWN_TICKS,
			CrystalClawRuntime.BUFF_TICKS,
			false,
			CrystalClawRuntime::stack,
			"crystal_claw"
	);

	private CrystalClawDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new CrystalClawDefinition());
		dev.flake.combat.CombatModifiers.outgoingSkillDamage((attacker, target, damage) -> CrystalClawRuntime.skillDamage(attacker, damage));
		dev.flake.combat.CombatStatuses.register((player, now, statuses) -> {
			int remaining = CrystalClawRuntime.remaining(player, now);
			if (remaining > 0) statuses.add(new dev.flake.combat.CombatStatuses.Status("crystal_claw", remaining, CrystalClawRuntime.BUFF_TICKS, 1));
		});
	}

	public static void registerItems() {
		CrystalClawRuntime.bind(dev.flake.content.ItemRegistration.active(CrystalClawRuntime.ID, (player, target) -> CrystalClawRuntime.activate(player)));
	}

	@Override public void register() { CrystalClawRuntime.register(); }
	@Override public void reset(ServerPlayer player) { CrystalClawRuntime.reset(player); }
}
