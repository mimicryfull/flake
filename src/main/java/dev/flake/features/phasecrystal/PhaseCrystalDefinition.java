package dev.flake.features.phasecrystal;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.CombatStatuses;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.StatusSpec;
import dev.flake.content.ItemRegistration;
import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/** Vertical slice for «Фазовий кристал» (Phase Crystal): 2s untargetable/pacified phase, then 3m blink. */
public final class PhaseCrystalDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.PHASE_CRYSTAL,
			"flake.active.phase_crystal.short",
			PhaseCrystalRuntime.COOLDOWN_TICKS,
			PhaseCrystalRuntime.BUFF_TICKS,
			false,
			PhaseCrystalRuntime::stack,
			"phase_crystal"
	);

	private PhaseCrystalDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new PhaseCrystalDefinition());
		ContentCatalog.registerStatus(new StatusSpec("phase_crystal", "flake.active.phase_crystal.short", PhaseCrystalRuntime::stack));
		CombatModifiers.itemCooldown((player, stack, base) ->
				PhaseCrystalRuntime.isItem(stack) ? Cooldowns.ticks(player, base) : base);
		CombatModifiers.damageGate((victim, source) -> {
			if (victim instanceof ServerPlayer vp && PhaseCrystalRuntime.active(vp)) return false;
			if (source.getEntity() instanceof ServerPlayer ap && PhaseCrystalRuntime.active(ap)) {
				dev.flake.combat.CombatFeedback.phaseLocked(ap, PhaseCrystalRuntime.stack());
				return false;
			}
			return true;
		});
		CombatStatuses.register((player, now, statuses) -> {
			int remaining = PhaseCrystalRuntime.remaining(player, now);
			if (remaining > 0) statuses.add(new CombatStatuses.Status("phase_crystal", remaining, PhaseCrystalRuntime.BUFF_TICKS, 1));
		});
	}

	public static void registerItems() {
		PhaseCrystalRuntime.bind(ItemRegistration.register(PhaseCrystalRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), PhaseCrystalRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, Entity target) {
						return PhaseCrystalRuntime.activate(player);
					}
				}));
	}

	@Override public void register() { PhaseCrystalRuntime.register(); }
	@Override public void reset(ServerPlayer player) { PhaseCrystalRuntime.reset(player); }
}
