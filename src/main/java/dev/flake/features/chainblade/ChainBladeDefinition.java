package dev.flake.features.chainblade;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.content.ItemRegistration;
import dev.flake.features.loadout.Loadout.Weapon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/** Vertical slice for «Ланцюгове лезо» (Chain Blade): 6.5-damage sword with a 9m pull and +30% marked follow-up. */
public final class ChainBladeDefinition implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.CHAIN_BLADE,
			"flake.weapon.chain_blade.short",
			ChainBladeRuntime.COOLDOWN_TICKS,
			0,
			ChainBladeRuntime::stack
	);

	private ChainBladeDefinition() {}

	public static void install() {
		ContentCatalog.registerWeapon(SPEC);
		ContentCatalog.registerLifecycle(new ChainBladeDefinition());
		CombatModifiers.itemCooldown((player, stack, base) ->
				ChainBladeRuntime.isChainBlade(stack) ? Cooldowns.ticks(player, base) : base);
		CombatModifiers.damageScale(ChainBladeRuntime::scaleMeleeDamage);
	}

	public static void registerItems() {
		// Base 1.0 + ToolMaterial.IRON (2.0) + 3.5 = 6.5 total melee damage; 4.0 - 2.4 = 1.6 attack speed.
		ChainBladeRuntime.bind(ItemRegistration.register(ChainBladeRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key)
						.sword(ToolMaterial.IRON, 3.5f, -2.4f), ChainBladeRuntime.ID) {
					@Override protected boolean canConsumeOnClient(Player player) {
						return ChainBladeRuntime.hasClientTarget(player);
					}
					@Override protected boolean activateServer(ServerPlayer player, Entity target) {
						return ChainBladeRuntime.pull(player, target);
					}
				}));
	}

	@Override public void register() { ChainBladeRuntime.register(); }
	@Override public void reset(ServerPlayer player) { ChainBladeRuntime.reset(player); }
}
