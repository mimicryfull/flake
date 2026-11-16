package dev.flake.features.shadowclaws;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.CombatStatuses;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.StatusSpec;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.content.ItemRegistration;
import dev.flake.features.loadout.Loadout.Weapon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Shadow Fight 2 style twin claws:
 * 3.0 total melee damage, 2x sword attack speed (3.2 APS), and a 35% chance on hit to inflict poison-like Bleed DoT.
 */
public final class ShadowClawsDefinition implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.SHADOW_CLAWS,
			"flake.weapon.shadow_claws.short",
			0,
			0,
			ShadowClawsRuntime::stack
	);

	private ShadowClawsDefinition() {}

	public static void install() {
		ContentCatalog.registerWeapon(SPEC);
		ContentCatalog.registerLifecycle(new ShadowClawsDefinition());
		ContentCatalog.registerStatus(new StatusSpec(
				"shadow_bleed", "flake.status.shadow_bleed", ShadowClawsRuntime::stack));
		CombatModifiers.afterDamage(ShadowClawsRuntime::afterMeleeDamage);
		CombatStatuses.register((player, now, statuses) -> {
			int rem = ShadowClawsRuntime.bleedRemaining(player, now);
			if (rem > 0) statuses.add(new CombatStatuses.Status("shadow_bleed", rem, ShadowClawsRuntime.BLEED_DURATION_TICKS, 0));
		});
	}

	public static void registerItems() {
		// Base 1.0 + ToolMaterial.IRON (2.0) + 0.0 = 3.0 total melee damage.
		// Base 4.0 - 0.8 = 3.2 attack speed (2x standard 1.6 sword speed).
		ShadowClawsRuntime.bind(ItemRegistration.register(ShadowClawsRuntime.ID, key ->
				new Item(new Item.Properties().setId(key)
						.sword(ToolMaterial.IRON, 0.0f, -0.8f))));
	}

	@Override public void register() { ShadowClawsRuntime.register(); }
	@Override public void reset(ServerPlayer player) { ShadowClawsRuntime.reset(player); }
}
