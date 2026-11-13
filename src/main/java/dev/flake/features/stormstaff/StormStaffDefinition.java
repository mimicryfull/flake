package dev.flake.features.stormstaff;

import dev.flake.combat.Cooldowns;
import dev.flake.combat.CombatModifiers;
import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.content.ItemRegistration;
import dev.flake.features.loadout.Loadout.Weapon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/** Vertical slice for «Грозовий жезл» (Storm Staff): 5.0-damage staff firing an 8m stunning lightning ray. */
public final class StormStaffDefinition implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.STORM_STAFF,
			"flake.weapon.storm_staff.short",
			StormStaffRuntime.COOLDOWN_TICKS,
			0,
			StormStaffRuntime::stack
	);

	private StormStaffDefinition() {}

	public static void install() {
		ContentCatalog.registerWeapon(SPEC);
		ContentCatalog.registerLifecycle(new StormStaffDefinition());
		CombatModifiers.itemCooldown((player, stack, base) ->
				StormStaffRuntime.isStaff(stack) ? Cooldowns.ticks(player, base) : base);
	}

	public static void registerItems() {
		// Base 1.0 + ToolMaterial.IRON (2.0) + 2.0 = 5.0 total melee damage; 4.0 - 2.4 = 1.6 attack speed.
		StormStaffRuntime.bind(ItemRegistration.register(StormStaffRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key)
						.sword(ToolMaterial.IRON, 2.0f, -2.4f), StormStaffRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, Entity target) {
						return StormStaffRuntime.castRay(player);
					}
				}));
	}

	@Override public void register() { StormStaffRuntime.register(); }
	@Override public void reset(ServerPlayer player) { StormStaffRuntime.reset(player); }
}
