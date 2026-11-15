package dev.flake.features.armorsets;

/** Installs and registers items for all four Iron-tier custom armor sets. */
public final class ArmorSets {
	private ArmorSets() {}

	public static void install() {
		PhoenixArmor.install();
		BarrelArmor.install();
		WizardArmor.install();
		HydraArmor.install();
	}

	public static void registerItems() {
		PhoenixArmor.registerItems();
		BarrelArmor.registerItems();
		WizardArmor.registerItems();
		HydraArmor.registerItems();
	}
}
