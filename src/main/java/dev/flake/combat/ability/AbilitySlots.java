package dev.flake.combat.ability;

import dev.flake.features.loadout.Loadout.FighterClass;
import dev.flake.text.FlakeI18n;
import java.util.Locale;

/** Canonical two-slot active ability registry. Slot numbers are 1-based everywhere. */
public final class AbilitySlots {
	public static final int COUNT = 2;

	public enum Ability {
		/** activeTicks is the real server-side phase length, not a UI guess. */
		GRAVITATIONAL_DISSONANCE("flake.ability.gravitational_dissonance", 60, dev.flake.features.concussed.Shockwave.CHARGE_TICKS, true),
		ELECTRIC_QUAKE("flake.ability.electric_quake", 80, dev.flake.features.concussed.ElectricQuake.PROPAGATION_TICKS, true),
		SHIELD_RAM("flake.ability.shield_ram", 120, 0, true),
		BULWARK("flake.ability.bulwark", 280, dev.flake.features.bastion.BastionRuntime.BULWARK_TICKS, true),
		BLOOD_LUNGE("flake.ability.blood_lunge", 120, 0, true),
		BLOODLUST("flake.ability.bloodlust", 300, dev.flake.features.vampire.VampireRuntime.BLOODLUST_TICKS, true),
		PROXIMITY_MINE("flake.ability.proximity_mine", 160, dev.flake.features.demolitionist.DemolitionistRuntime.MINE_LIFE_TICKS, true),
		BLAST_JUMP("flake.ability.blast_jump", 180, 0, true),
		UNIMPLEMENTED("flake.ability.unassigned", 0, 0, false);

		private final String titleKey;
		private final int cooldownTicks;
		private final int activeTicks;
		private final boolean implemented;

		Ability(String titleKey, int cooldownTicks, int activeTicks, boolean implemented) {
			this.titleKey = titleKey;
			this.cooldownTicks = cooldownTicks;
			this.activeTicks = activeTicks;
			this.implemented = implemented;
		}

		public String titleKey() { return titleKey; }
		public String title() { return FlakeI18n.tr(titleKey); }
		public int cooldownTicks() { return cooldownTicks; }
		public int activeTicks() { return activeTicks; }
		public boolean implemented() { return implemented; }
	}

	private AbilitySlots() {}

	public static boolean valid(int slot) { return slot == 1 || slot == 2; }

	public static Ability resolve(FighterClass fighterClass, int slot) {
		if (fighterClass == null || !valid(slot)) return Ability.UNIMPLEMENTED;
		var spec = dev.flake.content.ContentCatalog.fighterClass(fighterClass);
		if (spec == null) return Ability.UNIMPLEMENTED;
		return slot == 1 ? spec.slot1() : spec.slot2();
	}

	public static Ability resolve(String fighterClassName, int slot) {
		if (fighterClassName == null || !valid(slot)) return Ability.UNIMPLEMENTED;
		try {
			return resolve(FighterClass.valueOf(fighterClassName.toUpperCase(Locale.ROOT)), slot);
		} catch (IllegalArgumentException ignored) {
			return Ability.UNIMPLEMENTED;
		}
	}

	public static String title(String fighterClassName, int slot) {
		var ability = resolve(fighterClassName, slot);
		return ability == Ability.UNIMPLEMENTED ? FlakeI18n.tr("flake.ability.fallback", slot) : ability.title();
	}
}
