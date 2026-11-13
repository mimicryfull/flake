package dev.flake.features.stormharpoon;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ItemRegistration;
import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/** Vertical slice for «Грім-гарпун» (Storm Harpoon): 14m raycast harpoon that deals 3.0 HP and pulls enemy in. */
public final class StormHarpoonDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.STORM_HARPOON,
			"flake.active.storm_harpoon.short",
			StormHarpoonRuntime.COOLDOWN_TICKS,
			0,
			false,
			StormHarpoonRuntime::stack
	);

	private StormHarpoonDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new StormHarpoonDefinition());
		CombatModifiers.itemCooldown((player, stack, base) ->
				StormHarpoonRuntime.isItem(stack) ? Cooldowns.ticks(player, base) : base);
	}

	public static void registerItems() {
		StormHarpoonRuntime.bind(ItemRegistration.register(StormHarpoonRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), StormHarpoonRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, Entity target) {
						return StormHarpoonRuntime.activate(player, target);
					}
				}));
	}

	@Override public void register() { StormHarpoonRuntime.register(); }
	@Override public void reset(ServerPlayer player) { StormHarpoonRuntime.reset(player); }
}
