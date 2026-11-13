package dev.flake.features.utilities;

import dev.flake.features.loadout.Loadout.ActiveItem;
import dev.flake.features.loadout.Loadout.Weapon;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.content.ContentCatalog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** Standard weapons and vanilla utility active items. */
public final class UtilityEquipment implements Lifecycle {
	private UtilityEquipment() {}

	public static void registerItems() {}

	public static void install() {
		// Standard weapons
		ContentCatalog.registerWeapon(new WeaponSpec(Weapon.IRON_SWORD, "flake.weapon.iron_sword.short", 0, 0, () -> new ItemStack(Items.IRON_SWORD)));
		ContentCatalog.registerWeapon(new WeaponSpec(Weapon.STONE_AXE, "flake.weapon.stone_axe.short", 0, 0, () -> new ItemStack(Items.STONE_AXE)));
		ContentCatalog.registerWeapon(new WeaponSpec(Weapon.DIAMOND_SWORD, "flake.weapon.diamond_sword.short", 0, 0, () -> new ItemStack(Items.DIAMOND_SWORD)));
		ContentCatalog.registerWeapon(new WeaponSpec(Weapon.BOW, "flake.weapon.bow.short", 0, 32, () -> new ItemStack(Items.BOW)));
		ContentCatalog.registerWeapon(new WeaponSpec(Weapon.CROSSBOW, "flake.weapon.crossbow.short", 0, 24, () -> new ItemStack(Items.CROSSBOW)));

		// Standard utility active items
		ContentCatalog.registerActive(new ActiveItemSpec(ActiveItem.NONE, "flake.active.none.short", 0, 0, false, () -> ItemStack.EMPTY));
		ContentCatalog.registerActive(new ActiveItemSpec(ActiveItem.GOLDEN_APPLE, "flake.active.golden_apple.short", 0, 0, false, () -> new ItemStack(Items.GOLDEN_APPLE, 2)));
		ContentCatalog.registerActive(new ActiveItemSpec(ActiveItem.SPLASH_HEALING, "flake.active.splash_healing.short", 0, 0, false, () -> {
			var potion = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING);
			potion.setCount(2);
			return potion;
		}));
		ContentCatalog.registerActive(new ActiveItemSpec(ActiveItem.ENDER_PEARL, "flake.active.ender_pearl.short", 20, 0, false, () -> new ItemStack(Items.ENDER_PEARL, 2)));
		ContentCatalog.registerActive(new ActiveItemSpec(ActiveItem.SHIELD, "flake.active.shield.short", 100, 0, true, () -> new ItemStack(Items.SHIELD)));
		ContentCatalog.registerActive(new ActiveItemSpec(ActiveItem.WIND_CHARGE, "flake.active.wind_charge.short", 10, 0, false, () -> new ItemStack(Items.WIND_CHARGE, 4)));

		ContentCatalog.registerLifecycle(new UtilityEquipment());
	}

	@Override public void register() { }
	@Override public void reset(ServerPlayer player) { }
}
