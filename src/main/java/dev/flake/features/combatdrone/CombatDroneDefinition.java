package dev.flake.features.combatdrone;

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

/** Vertical slice for «Бойовий дрон» (Combat Drone): 4s voxel drone dealing 2 HP periodic melee damage. */
public final class CombatDroneDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.COMBAT_DRONE,
			"flake.active.combat_drone.short",
			CombatDroneRuntime.COOLDOWN_TICKS,
			CombatDroneRuntime.DURATION_TICKS,
			false,
			CombatDroneRuntime::stack,
			"combat_drone"
	);

	private CombatDroneDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new CombatDroneDefinition());
		ContentCatalog.registerStatus(new StatusSpec("combat_drone", "flake.active.combat_drone.short", CombatDroneRuntime::stack));
		CombatModifiers.itemCooldown((player, stack, base) ->
				CombatDroneRuntime.isItem(stack) ? Cooldowns.ticks(player, base) : base);
		CombatStatuses.register((player, now, statuses) -> {
			int remaining = CombatDroneRuntime.remaining(player, now);
			if (remaining > 0) statuses.add(new CombatStatuses.Status("combat_drone", remaining, CombatDroneRuntime.DURATION_TICKS, 1));
		});
	}

	public static void registerItems() {
		CombatDroneRuntime.bind(ItemRegistration.register(CombatDroneRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), CombatDroneRuntime.ID) {
					@Override
					protected boolean activateServer(ServerPlayer player, Entity directTarget) {
						return CombatDroneRuntime.activate(player);
					}
				}));
	}

	@Override public void register() { CombatDroneRuntime.register(); }
	@Override public void reset(ServerPlayer player) { CombatDroneRuntime.reset(player); }
}
