package dev.flake.bootstrap;

import dev.flake.Flake;
import dev.flake.content.ContentCatalog;
import dev.flake.features.armor.ArmorItems;
import dev.flake.features.chronotrident.ChronoTridentRuntime;
import dev.flake.features.loadout.Loadout.ActiveItem;
import dev.flake.features.loadout.Loadout.ArmorMaterial;
import dev.flake.features.loadout.Loadout.ArmorSlot;
import dev.flake.features.loadout.Loadout.PassiveItem;
import dev.flake.features.loadout.Loadout.Weapon;
import dev.flake.features.voidpaladin.VoidPaladinRuntime;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Registers the sorted Flake Creative Mode tab for testing custom weapons, armor, actives, and passives. */
public final class FlakeCreativeTab {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "items");

	public static final List<Weapon> WEAPONS = List.of(
			Weapon.MIRROR_DAGGER,
			Weapon.JUNGLE_SPEAR,
			Weapon.CONTROL_HAMMER,
			Weapon.CHAIN_BLADE,
			Weapon.DEADLY_BLEED,
			Weapon.VOID_PALADIN,
			Weapon.CHAOS_BROADSWORD,
			Weapon.STORM_STAFF);

	public static final List<ArmorMaterial> ARMOR_SETS = List.of(
			ArmorMaterial.OBSIDIAN,
			ArmorMaterial.STORM_BERSERKER);

	public static final List<ArmorSlot> ARMOR_SLOTS = List.of(
			ArmorSlot.HELMET,
			ArmorSlot.CHESTPLATE,
			ArmorSlot.LEGGINGS,
			ArmorSlot.BOOTS);

	public static final List<ActiveItem> ACTIVES = List.of(
			// Control
			ActiveItem.CHRONO_TRIDENT,
			ActiveItem.VOID_ANCHOR,
			ActiveItem.STORM_HARPOON,
			ActiveItem.AXE_OF_CUMSHOT,
			ActiveItem.HUNTER_MARK,
			ActiveItem.DARK_CUBE,
			ActiveItem.SMOKE_BOMB,
			// Defense
			ActiveItem.MIRROR_TRAP,
			// Buffs
			ActiveItem.SPARK_SYRINGE,
			ActiveItem.CRYSTAL_CLAW,
			ActiveItem.MATADORA,
			ActiveItem.ADRENALINE,
			// Mobility
			ActiveItem.PHASE_CRYSTAL);

	public static final List<PassiveItem> PASSIVES = List.of(
			PassiveItem.BLOODY_SEAL,
			PassiveItem.STORM_CHARGE,
			PassiveItem.MIRROR_SCALE,
			PassiveItem.MELTING_AMETHYST);

	private static boolean registered;

	private FlakeCreativeTab() {}

	public static synchronized void register() {
		if (registered) return;
		registered = true;
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ID, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.flake.items"))
				.icon(FlakeCreativeTab::tabIcon)
				.displayItems(FlakeCreativeTab::populate)
				.build());
	}

	private static ItemStack tabIcon() {
		var icon = ChronoTridentRuntime.stack();
		return icon.isEmpty() ? VoidPaladinRuntime.stack() : icon;
	}

	private static void populate(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
		for (var weapon : WEAPONS) {
			var spec = ContentCatalog.weapon(weapon);
			if (spec != null) acceptSingle(output, spec.createStack());
		}
		for (var armor : ARMOR_SETS) {
			for (var slot : ARMOR_SLOTS) {
				acceptSingle(output, ArmorItems.create(armor, slot));
			}
		}
		for (var active : ACTIVES) {
			var spec = ContentCatalog.active(active);
			if (spec != null) acceptSingle(output, spec.createStack());
		}
		for (var passive : PASSIVES) {
			var spec = ContentCatalog.passive(passive);
			if (spec != null) acceptSingle(output, spec.createIcon());
		}
	}

	private static void acceptSingle(CreativeModeTab.Output output, ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		output.accept(stack.getCount() == 1 ? stack : stack.copyWithCount(1));
	}
}
