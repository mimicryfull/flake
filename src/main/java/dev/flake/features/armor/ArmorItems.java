package dev.flake.features.armor;

import dev.flake.features.armorsets.BarrelArmor;
import dev.flake.features.armorsets.HydraArmor;
import dev.flake.features.armorsets.PhoenixArmor;
import dev.flake.features.armorsets.WizardArmor;
import dev.flake.features.loadout.Loadout.ArmorMaterial;
import dev.flake.features.loadout.Loadout.ArmorSlot;
import dev.flake.features.obsidianarmor.ObsidianArmor;
import dev.flake.features.stormberserker.StormBerserkerArmor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One item mapping for both the server kit and client previews. */
public final class ArmorItems {
	private ArmorItems() {}

	public static ItemStack create(ArmorMaterial material, ArmorSlot slot) {
		if (material == ArmorMaterial.STORM_BERSERKER) return StormBerserkerArmor.createPiece(slot);
		var item = switch (material) {
			case NONE -> null;
			case CHAINMAIL -> switch (slot) {
				case HELMET -> Items.CHAINMAIL_HELMET; case CHESTPLATE -> Items.CHAINMAIL_CHESTPLATE;
				case LEGGINGS -> Items.CHAINMAIL_LEGGINGS; case BOOTS -> Items.CHAINMAIL_BOOTS;
			};
			case GOLD -> switch (slot) {
				case HELMET -> Items.GOLDEN_HELMET; case CHESTPLATE -> Items.GOLDEN_CHESTPLATE;
				case LEGGINGS -> Items.GOLDEN_LEGGINGS; case BOOTS -> Items.GOLDEN_BOOTS;
			};
			case IRON -> switch (slot) {
				case HELMET -> Items.IRON_HELMET; case CHESTPLATE -> Items.IRON_CHESTPLATE;
				case LEGGINGS -> Items.IRON_LEGGINGS; case BOOTS -> Items.IRON_BOOTS;
			};
			case DIAMOND -> switch (slot) {
				case HELMET -> Items.DIAMOND_HELMET; case CHESTPLATE -> Items.DIAMOND_CHESTPLATE;
				case LEGGINGS -> Items.DIAMOND_LEGGINGS; case BOOTS -> Items.DIAMOND_BOOTS;
			};
			case OBSIDIAN -> ObsidianArmor.itemFor(slot);
			case STORM_BERSERKER -> null;
			case PHOENIX -> PhoenixArmor.itemFor(slot);
			case BARREL -> BarrelArmor.itemFor(slot);
			case WIZARD -> WizardArmor.itemFor(slot);
			case HYDRA -> HydraArmor.itemFor(slot);
		};
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
