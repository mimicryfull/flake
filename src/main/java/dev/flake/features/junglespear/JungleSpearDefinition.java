package dev.flake.features.junglespear;

import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.features.loadout.Loadout.Weapon;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Джунглеве спис»: 6-damage spear with extra reach and a 3-block dash. */
public final class JungleSpearDefinition implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.JUNGLE_SPEAR,
			"flake.weapon.jungle_spear.short",
			JungleSpearRuntime.COOLDOWN_TICKS,
			0,
			JungleSpearRuntime::stack
	);

	private JungleSpearDefinition() {}

	public static void install() {
		ContentCatalog.registerWeapon(SPEC);
		ContentCatalog.registerLifecycle(new JungleSpearDefinition());
		dev.flake.combat.CombatModifiers.itemCooldown((player, stack, base) ->
				JungleSpearRuntime.isSpear(stack) ? dev.flake.combat.Cooldowns.ticks(player, base) : base);
	}

	public static void registerItems() {
		JungleSpearRuntime.bind(dev.flake.content.ItemRegistration.register(JungleSpearRuntime.ID, key ->
				new ActiveCombatItem(new net.minecraft.world.item.Item.Properties().setId(key)
						.sword(net.minecraft.world.item.ToolMaterial.IRON, 3.0f, -2.4f), JungleSpearRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, net.minecraft.world.entity.Entity target) {
						return JungleSpearRuntime.dash(player);
					}
				}));
		JungleSpearRuntime.bindSound(dev.flake.content.ItemRegistration.sound(JungleSpearRuntime.SOUND_ID));
	}

	@Override public void register() { JungleSpearRuntime.register(); }
	@Override public void reset(ServerPlayer player) { JungleSpearRuntime.removeReach(player); JungleSpearRuntime.reset(player); }
}
