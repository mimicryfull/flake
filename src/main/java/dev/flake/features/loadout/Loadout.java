package dev.flake.features.loadout;

import dev.flake.text.FlakeI18n;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record Loadout(
		FighterClass fighterClass,
		ArmorMaterial helmet,
		ArmorMaterial chestplate,
		ArmorMaterial leggings,
		ArmorMaterial boots,
		Weapon weapon,
		PassiveItem passiveItem,
		ActiveItem activeItem1,
		ActiveItem activeItem2,
		List<String> migrationConflicts
) {
	public static final int ACTIVE_ITEM_SLOTS = 2;
	/** PvP health pool; balance is intentionally provisional. */
	public static final float BASE_HEALTH = 50.0f;

	public Loadout {
		Objects.requireNonNull(fighterClass);
		Objects.requireNonNull(helmet);
		Objects.requireNonNull(chestplate);
		Objects.requireNonNull(leggings);
		Objects.requireNonNull(boots);
		Objects.requireNonNull(weapon);
		Objects.requireNonNull(passiveItem);
		Objects.requireNonNull(activeItem1);
		Objects.requireNonNull(activeItem2);
		migrationConflicts = List.copyOf(migrationConflicts);
	}

	public Loadout(FighterClass fighterClass, ArmorMaterial helmet, ArmorMaterial chestplate,
			ArmorMaterial leggings, ArmorMaterial boots, Weapon weapon, PassiveItem passiveItem,
			ActiveItem activeItem1, ActiveItem activeItem2) {
		this(fighterClass, helmet, chestplate, leggings, boots, weapon, passiveItem, activeItem1, activeItem2, List.of());
	}

	/** Session-only v1 import. Conflicting content is retained in migrationConflicts and cannot be equipped. */
	public Loadout(FighterClass fighterClass, Armor armor, Weapon weapon, List<Accessory> accessories) {
		this(fighterClass, fromArmorPreset(armor), fromArmorPreset(armor), fromArmorPreset(armor),
				fromArmorPreset(armor), weapon, accessories, PassiveItem.NONE, UtilityItem.NONE);
	}

	public Loadout(FighterClass fighterClass, ArmorMaterial helmet, ArmorMaterial chestplate,
			ArmorMaterial leggings, ArmorMaterial boots, Weapon weapon, List<Accessory> accessories,
			PassiveItem passiveItem, UtilityItem utilityItem) {
		this(fighterClass, helmet, chestplate, leggings, boots, weapon,
				migratedPassive(accessories, passiveItem),
				accessories.contains(Accessory.WIND_CHARGE) ? ActiveItem.WIND_CHARGE : ActiveItem.valueOf(utilityItem.name()),
				accessories.contains(Accessory.WIND_CHARGE) ? ActiveItem.valueOf(utilityItem.name()) : ActiveItem.NONE,
				legacyConflicts(accessories, passiveItem));
	}

	private static PassiveItem migratedPassive(List<Accessory> accessories, PassiveItem passive) {
		if (passive != PassiveItem.NONE) return passive;
		return accessories.stream().filter(a -> !a.rightClick()).findFirst()
				.map(a -> PassiveItem.valueOf(a.name())).orElse(PassiveItem.NONE);
	}

	private static List<String> legacyConflicts(List<Accessory> accessories, PassiveItem passive) {
		var conflicts = new ArrayList<String>();
		boolean occupied = passive != PassiveItem.NONE;
		for (var accessory : accessories) {
			if (accessory.rightClick()) continue;
			if (occupied) conflicts.add(FlakeI18n.tr("flake.loadout.conflict.passive", accessory.name(), accessory.title()));
			occupied = true;
		}
		if (accessories.size() > 2) conflicts.add(FlakeI18n.tr("flake.loadout.conflict.too_many", accessories));
		if (accessories.stream().distinct().count() != accessories.size()) conflicts.add(FlakeI18n.tr("flake.loadout.conflict.duplicate_acc", accessories));
		return List.copyOf(conflicts);
	}

	private static ArmorMaterial fromArmorPreset(Armor armor) {
		Objects.requireNonNull(armor);
		return armor == Armor.DIAMOND ? ArmorMaterial.DIAMOND : ArmorMaterial.IRON;
	}

	public List<String> errors() {
		var errors = new ArrayList<String>();
		errors.addAll(migrationConflicts);
		if (activeItem1 != ActiveItem.NONE && activeItem1 == activeItem2) errors.add(FlakeI18n.tr("flake.loadout.error.duplicate"));
		return List.copyOf(errors);
	}

	public boolean hasFullArmorSet() {
		return helmet != ArmorMaterial.NONE
				&& helmet == chestplate
				&& chestplate == leggings
				&& leggings == boots;
	}

	/** Number of equipped Obsidian Bastion pieces (0..4). Each piece trades -3% speed for heavy poise/toughness. */
	public int obsidianPieces() {
		int count = 0;
		if (helmet == ArmorMaterial.OBSIDIAN) count++;
		if (chestplate == ArmorMaterial.OBSIDIAN) count++;
		if (leggings == ArmorMaterial.OBSIDIAN) count++;
		if (boots == ArmorMaterial.OBSIDIAN) count++;
		return count;
	}

	/** True when all 4 armor slots are equipped with Obsidian Bastion armor. */
	public boolean hasFullObsidianSet() {
		return hasFullArmorSet() && helmet == ArmorMaterial.OBSIDIAN;
	}

	/** Number of equipped Storm Berserker pieces (0..4). Each piece trades +2% incoming damage for +3% attack speed. */
	public int stormBerserkerPieces() {
		int count = 0;
		if (helmet == ArmorMaterial.STORM_BERSERKER) count++;
		if (chestplate == ArmorMaterial.STORM_BERSERKER) count++;
		if (leggings == ArmorMaterial.STORM_BERSERKER) count++;
		if (boots == ArmorMaterial.STORM_BERSERKER) count++;
		return count;
	}

	/** True when all 4 armor slots are equipped with Storm Berserker armor. */
	public boolean hasFullStormBerserkerSet() {
		return hasFullArmorSet() && helmet == ArmorMaterial.STORM_BERSERKER;
	}

	public Stats stats() {
		int armorPoints = helmet.helmetArmor() + chestplate.chestArmor() + leggings.legsArmor() + boots.bootsArmor();
		float toughness = helmet.toughness() + chestplate.toughness() + leggings.toughness() + boots.toughness();
		var bonuses = passiveItem.bonuses();
		float maxHp = BASE_HEALTH + bonuses.health();
		float absorption = bonuses.absorption();
		float attackDamage = weapon.ranged() ? Float.NaN : weapon.attackDamage() + bonuses.meleeDamage();
		int speedPercent = 100 + bonuses.speedPercent() - obsidianPieces() * 3;
		boolean fullSet = hasFullArmorSet();
		// Armor-only upper estimate. Actual reduction depends on incoming damage and toughness.
		float dmgReduction = armorPoints * 4.0f;

		var passives = new ArrayList<String>();
		if (passiveItem != PassiveItem.NONE) {
			passives.add(passiveItem.title() + ": " + passiveItem.description());
		}
		if (hasFullObsidianSet()) {
			passives.add(FlakeI18n.tr("flake.armor.obsidian.set_bonus"));
		}
		if (hasFullStormBerserkerSet()) {
			passives.add(FlakeI18n.tr("flake.armor.storm_berserker.set_bonus"));
		}

		var actives = new ArrayList<String>();
		for (var key : fighterClass.activeDescriptionKeys()) actives.add(FlakeI18n.tr(key));
		if (!weapon.activeInfo().isEmpty()) {
			actives.add(weapon.title() + ": " + weapon.activeInfo());
		}
		for (var item : List.of(activeItem1, activeItem2))
			if (item != ActiveItem.NONE) actives.add(item.title() + " (ПКМ): " + item.description());

		return new Stats(
				maxHp,
				absorption,
				armorPoints,
				toughness,
				dmgReduction,
				attackDamage,
				speedPercent,
				fullSet,
				List.copyOf(passives),
				List.copyOf(actives)
		);
	}

	public Loadout withArmorSlot(ArmorSlot slot, ArmorMaterial material) {
		return switch (slot) {
			case HELMET -> new Loadout(fighterClass, material, chestplate, leggings, boots, weapon, passiveItem, activeItem1, activeItem2, migrationConflicts);
			case CHESTPLATE -> new Loadout(fighterClass, helmet, material, leggings, boots, weapon, passiveItem, activeItem1, activeItem2, migrationConflicts);
			case LEGGINGS -> new Loadout(fighterClass, helmet, chestplate, material, boots, weapon, passiveItem, activeItem1, activeItem2, migrationConflicts);
			case BOOTS -> new Loadout(fighterClass, helmet, chestplate, leggings, material, weapon, passiveItem, activeItem1, activeItem2, migrationConflicts);
		};
	}

	public ArmorMaterial armorAt(ArmorSlot slot) {
		return switch (slot) {
			case HELMET -> helmet;
			case CHESTPLATE -> chestplate;
			case LEGGINGS -> leggings;
			case BOOTS -> boots;
		};
	}

	public Loadout withClass(FighterClass value) {
		return new Loadout(value, helmet, chestplate, leggings, boots, weapon, passiveItem, activeItem1, activeItem2, migrationConflicts);
	}

	public Loadout withWeapon(Weapon value) {
		return new Loadout(fighterClass, helmet, chestplate, leggings, boots, value, passiveItem, activeItem1, activeItem2, migrationConflicts);
	}

	public Loadout withPassive(PassiveItem value) {
		return new Loadout(fighterClass, helmet, chestplate, leggings, boots, weapon, value, activeItem1, activeItem2, migrationConflicts);
	}

	/** One-based active ITEM index, not an ability slot. */
	public Loadout withActiveItem(int slot, ActiveItem value) {
		if (slot < 1 || slot > ACTIVE_ITEM_SLOTS) throw new IllegalArgumentException("Invalid active item slot: " + slot);
		Objects.requireNonNull(value);
		return new Loadout(fighterClass, helmet, chestplate, leggings, boots, weapon, passiveItem,
				slot == 1 ? value : activeItem1, slot == 2 ? value : activeItem2, migrationConflicts);
	}

	public ActiveItem activeItemAt(int slot) {
		if (slot < 1 || slot > ACTIVE_ITEM_SLOTS) throw new IllegalArgumentException("Invalid active item slot: " + slot);
		return slot == 1 ? activeItem1 : activeItem2;
	}

	public static Loadout defaultLoadout() {
		return new Loadout(FighterClass.BASTION, ArmorMaterial.IRON, ArmorMaterial.IRON, ArmorMaterial.IRON,
				ArmorMaterial.IRON, Weapon.IRON_SWORD, PassiveItem.NONE, ActiveItem.NONE, ActiveItem.NONE);
	}

	public enum ArmorSlot {
		HELMET("flake.armor_slot.helmet"),
		CHESTPLATE("flake.armor_slot.chestplate"),
		LEGGINGS("flake.armor_slot.leggings"),
		BOOTS("flake.armor_slot.boots");

		private final String titleKey;
		ArmorSlot(String titleKey) { this.titleKey = titleKey; }
		public String titleKey() { return titleKey; }
		public String title() { return FlakeI18n.tr(titleKey); }
	}

	public enum FighterClass {
		CONCUSSED("flake.class.concussed.title", "flake.class.concussed.desc"),
		BASTION("flake.class.bastion.title", "flake.class.bastion.desc"),
		VAMPIRE("flake.class.vampire.title", "flake.class.vampire.desc"),
		DEMOLITIONIST("flake.class.demolitionist.title", "flake.class.demolitionist.desc"),
		SHAMAN("flake.class.shaman.title", "flake.class.shaman.desc");

		private final String titleKey;
		private final String descriptionKey;
		FighterClass(String titleKey, String descriptionKey) {
			this.titleKey = titleKey;
			this.descriptionKey = descriptionKey;
		}
		public String titleKey() { return titleKey; }
		public String descriptionKey() { return descriptionKey; }
		public String title() { return FlakeI18n.tr(titleKey); }
		public String description() { return FlakeI18n.tr(descriptionKey); }
		public List<String> activeDescriptionKeys() {
			String prefix = "flake.class." + name().toLowerCase(java.util.Locale.ROOT) + ".";
			return List.of(prefix + "active1", prefix + "active2");
		}
	}

	public enum Armor { IRON, DIAMOND }

	public enum ArmorMaterial {
		NONE("flake.armor.none.title", 0, 0, 0, 0, 0.0f),
		CHAINMAIL("flake.armor.chainmail.title", 2, 5, 4, 1, 0.0f),
		GOLD("flake.armor.gold.title", 2, 5, 3, 1, 0.0f),
		IRON("flake.armor.iron.title", 2, 6, 5, 2, 0.0f),
		DIAMOND("flake.armor.diamond.title", 3, 8, 6, 3, 2.0f),
		OBSIDIAN("flake.armor.obsidian.title", 3, 8, 6, 3, 3.0f),
		STORM_BERSERKER("flake.armor.storm_berserker.title", 2, 6, 5, 2, 1.0f);

		private final String titleKey;
		private final int helmetArmor;
		private final int chestArmor;
		private final int legsArmor;
		private final int bootsArmor;
		private final float toughness;

		ArmorMaterial(String titleKey, int helmetArmor, int chestArmor, int legsArmor, int bootsArmor, float toughness) {
			this.titleKey = titleKey;
			this.helmetArmor = helmetArmor;
			this.chestArmor = chestArmor;
			this.legsArmor = legsArmor;
			this.bootsArmor = bootsArmor;
			this.toughness = toughness;
		}

		public String titleKey() { return titleKey; }
		public String title() { return FlakeI18n.tr(titleKey); }
		public int helmetArmor() { return helmetArmor; }
		public int chestArmor() { return chestArmor; }
		public int legsArmor() { return legsArmor; }
		public int bootsArmor() { return bootsArmor; }
		public float toughness() { return toughness; }

		public int armorFor(ArmorSlot slot) {
			return switch (slot) {
				case HELMET -> helmetArmor;
				case CHESTPLATE -> chestArmor;
				case LEGGINGS -> legsArmor;
				case BOOTS -> bootsArmor;
			};
		}
	}

	public enum Weapon {
		IRON_SWORD("flake.weapon.iron_sword.title", "flake.weapon.iron_sword.cat", 6.0f, "flake.weapon.iron_sword.info"),
		STONE_AXE("flake.weapon.stone_axe.title", "flake.weapon.stone_axe.cat", 9.0f, "flake.weapon.stone_axe.info"),
		DIAMOND_SWORD("flake.weapon.diamond_sword.title", "flake.weapon.diamond_sword.cat", 7.0f, "flake.weapon.diamond_sword.info"),
		BOW("flake.weapon.bow.title", "flake.weapon.bow.cat", 6.0f, "flake.weapon.bow.info"),
		CROSSBOW("flake.weapon.crossbow.title", "flake.weapon.crossbow.cat", 7.0f, "flake.weapon.crossbow.info"),
		/** Тимчасова шкода; база — залізний меч (6.0). */
		CONTROL_HAMMER("flake.weapon.control_hammer.title", "flake.weapon.control_hammer.cat", 6.0f, "flake.weapon.control_hammer.info"),
		/** Меч між алмазним і незеритовим; кожен 4-й удар — крит. */
		VOID_PALADIN("flake.weapon.void_paladin.title", "flake.weapon.void_paladin.cat", 7.5f, "flake.weapon.void_paladin.info"),
		/** Смертельна кровотеча: 7 шкоди, ПКМ — крит 200%. */
		DEADLY_BLEED("flake.weapon.deadly_bleed.title", "flake.weapon.deadly_bleed.cat", 7.0f, "flake.weapon.deadly_bleed.info"),
		/** Джунглеве спис: 6 шкоди, довший випад, ПКМ — ривок. */
		JUNGLE_SPEAR("flake.weapon.jungle_spear.title", "flake.weapon.jungle_spear.cat", 6.0f, "flake.weapon.jungle_spear.info"),
		/** Хаотичний палаш: 9 шкоди, повільна атака, ПКМ — розмах із послабленням. */
		CHAOS_BROADSWORD("flake.weapon.chaos_broadsword.title", "flake.weapon.chaos_broadsword.cat", 9.0f, "flake.weapon.chaos_broadsword.info"),
		CHAIN_BLADE("flake.weapon.chain_blade.title", "flake.weapon.chain_blade.cat", 6.5f, "flake.weapon.chain_blade.info"),
		STORM_STAFF("flake.weapon.storm_staff.title", "flake.weapon.storm_staff.cat", 5.0f, "flake.weapon.storm_staff.info"),
		MIRROR_DAGGER("flake.weapon.mirror_dagger.title", "flake.weapon.mirror_dagger.cat", 5.5f, "flake.weapon.mirror_dagger.info");

		private final String titleKey;
		private final String categoryKey;
		private final float attackDamage;
		private final String activeInfoKey;

		Weapon(String titleKey, String categoryKey, float attackDamage, String activeInfoKey) {
			this.titleKey = titleKey;
			this.categoryKey = categoryKey;
			this.attackDamage = attackDamage;
			this.activeInfoKey = activeInfoKey;
		}

		public String titleKey() { return titleKey; }
		public String categoryKey() { return categoryKey; }
		public String activeInfoKey() { return activeInfoKey; }
		public String title() { return FlakeI18n.tr(titleKey); }
		public String category() { return FlakeI18n.tr(categoryKey); }
		public float attackDamage() { return attackDamage; }
		public String activeInfo() { return FlakeI18n.tr(activeInfoKey); }
		public boolean ranged() { return this == BOW || this == CROSSBOW; }
	}

	public enum Accessory {
		VITALITY("flake.passive.vitality.title", "flake.accessory.passive_cat", "flake.passive.vitality.desc", false),
		MOBILITY("flake.passive.mobility.title", "flake.accessory.passive_cat", "flake.passive.mobility.desc", false),
		REGENERATION("flake.passive.regeneration.title", "flake.accessory.passive_cat", "flake.passive.regeneration.desc", false),
		WIND_CHARGE("flake.active.wind_charge.title", "flake.accessory.active_cat", "flake.active.wind_charge.desc", true);

		private final String titleKey;
		private final String categoryKey;
		private final String descriptionKey;
		private final boolean rightClick;

		Accessory(String titleKey, String categoryKey, String descriptionKey, boolean rightClick) {
			this.titleKey = titleKey;
			this.categoryKey = categoryKey;
			this.descriptionKey = descriptionKey;
			this.rightClick = rightClick;
		}

		public String title() { return FlakeI18n.tr(titleKey); }
		public String category() { return FlakeI18n.tr(categoryKey); }
		public String description() { return FlakeI18n.tr(descriptionKey); }
		public boolean rightClick() { return rightClick; }
	}

	public enum PassiveItem {
		NONE("flake.passive.none.title", "flake.passive.none.desc"),
		IRON_AEGIS("flake.passive.iron_aegis.title", "flake.passive.iron_aegis.desc", new dev.flake.features.passives.PassiveBonuses(0, 2, 0, 0)),
		CRIMSON_FANG("flake.passive.crimson_fang.title", "flake.passive.crimson_fang.desc", new dev.flake.features.passives.PassiveBonuses(0, 0, 3, 0)),
		STONE_PLATE("flake.passive.stone_plate.title", "flake.passive.stone_plate.desc"),
		VITALITY("flake.passive.vitality.title", "flake.passive.vitality.desc", new dev.flake.features.passives.PassiveBonuses(4, 0, 0, 0)),
		MOBILITY("flake.passive.mobility.title", "flake.passive.mobility.desc", new dev.flake.features.passives.PassiveBonuses(0, 0, 0, 20)),
		REGENERATION("flake.passive.regeneration.title", "flake.passive.regeneration.desc"),
		LEECH("flake.passive.leech.title", "flake.passive.leech.desc"),
		THORNS("flake.passive.thorns.title", "flake.passive.thorns.desc"),
		FURY("flake.passive.fury.title", "flake.passive.fury.desc"),
		/** Пасивно скорочує перезарядку класових здібностей і здібності зброї на 20%. */
		MELTING_AMETHYST("flake.passive.melting_amethyst.title", "flake.passive.melting_amethyst.desc"),
		BLOODY_SEAL("flake.passive.bloody_seal.title", "flake.passive.bloody_seal.desc"),
		MIRROR_SCALE("flake.passive.mirror_scale.title", "flake.passive.mirror_scale.desc"),
		HUNTER_COMPASS("flake.passive.hunter_compass.title", "flake.passive.hunter_compass.desc"),
		STORM_CHARGE("flake.passive.storm_charge.title", "flake.passive.storm_charge.desc"),
		SILENT_STEP("flake.passive.silent_step.title", "flake.passive.silent_step.desc");

		private final String titleKey;
		private final String descriptionKey;

		PassiveItem(String titleKey, String descriptionKey) {
			this(titleKey, descriptionKey, dev.flake.features.passives.PassiveBonuses.NONE);
		}

		private final dev.flake.features.passives.PassiveBonuses bonuses;
		PassiveItem(String titleKey, String descriptionKey, dev.flake.features.passives.PassiveBonuses bonuses) {
			this.titleKey = titleKey;
			this.descriptionKey = descriptionKey;
			this.bonuses = bonuses;
		}
		public dev.flake.features.passives.PassiveBonuses bonuses() { return bonuses; }

		public String titleKey() { return titleKey; }
		public String descriptionKey() { return descriptionKey; }
		public String title() { return FlakeI18n.tr(titleKey); }
		public String description() { return FlakeI18n.tr(descriptionKey); }
	}

	public enum ActiveItem {
		NONE("flake.active.none.title", "flake.active.none.desc", "flake.category.utility"),
		GOLDEN_APPLE("flake.active.golden_apple.title", "flake.active.golden_apple.desc", "flake.category.defense"),
		SPLASH_HEALING("flake.active.splash_healing.title", "flake.active.splash_healing.desc", "flake.category.defense"),
		ENDER_PEARL("flake.active.ender_pearl.title", "flake.active.ender_pearl.desc", "flake.category.mobility"),
		SHIELD("flake.active.shield.title", "flake.active.shield.desc", "flake.category.defense"),
		WIND_CHARGE("flake.active.wind_charge.title", "flake.active.wind_charge.desc", "flake.category.mobility"),
		DARK_CUBE("flake.active.dark_cube.title", "flake.active.dark_cube.desc", "flake.category.control"),
		MATADORA("flake.active.matadora.title", "flake.active.matadora.desc", "flake.category.buff"),
		CRYSTAL_CLAW("flake.active.crystal_claw.title", "flake.active.crystal_claw.desc", "flake.category.buff"),
		AXE_OF_CUMSHOT("flake.active.axe_of_cumshot.title", "flake.active.axe_of_cumshot.desc", "flake.category.control"),
		CHRONO_TRIDENT("flake.active.chrono_trident.title", "flake.active.chrono_trident.desc", "flake.category.control"),
		VOID_ANCHOR("flake.active.void_anchor.title", "flake.active.void_anchor.desc", "flake.category.control"),
		MIRROR_TRAP("flake.active.mirror_trap.title", "flake.active.mirror_trap.desc", "flake.category.defense"),
		SPARK_SYRINGE("flake.active.spark_syringe.title", "flake.active.spark_syringe.desc", "flake.category.buff"),
		STORM_HARPOON("flake.active.storm_harpoon.title", "flake.active.storm_harpoon.desc", "flake.category.control"),
		PHASE_CRYSTAL("flake.active.phase_crystal.title", "flake.active.phase_crystal.desc", "flake.category.mobility");

		private final String titleKey, descriptionKey, categoryKey;
		ActiveItem(String titleKey, String descriptionKey, String categoryKey) {
			this.titleKey = titleKey; this.descriptionKey = descriptionKey; this.categoryKey = categoryKey;
		}
		public String titleKey() { return titleKey; }
		public String descriptionKey() { return descriptionKey; }
		public String categoryKey() { return categoryKey; }
		public String title() { return FlakeI18n.tr(titleKey); }
		public String description() { return FlakeI18n.tr(descriptionKey); }
		public String category() { return FlakeI18n.tr(categoryKey); }
	}

	/** v1 names are kept only as explicit import types, never additional equipment slots. */
	public enum UtilityItem {
		NONE("flake.active.none.title", "flake.active.none.desc"),
		GOLDEN_APPLE("flake.active.golden_apple.title", "flake.active.golden_apple.desc"),
		SPLASH_HEALING("flake.active.splash_healing.title", "flake.active.splash_healing.desc"),
		ENDER_PEARL("flake.active.ender_pearl.title", "flake.active.ender_pearl.desc"),
		SHIELD("flake.active.shield.title", "flake.active.shield.desc");

		private final String titleKey;
		private final String descriptionKey;

		UtilityItem(String titleKey, String descriptionKey) {
			this.titleKey = titleKey;
			this.descriptionKey = descriptionKey;
		}

		public String title() { return FlakeI18n.tr(titleKey); }
		public String description() { return FlakeI18n.tr(descriptionKey); }
	}

	public record Stats(
			float maxHealth,
			float absorption,
			int armorPoints,
			float armorToughness,
			float damageReductionPercent,
			float attackDamage,
			int speedPercent,
			boolean fullArmorSet,
			List<String> passiveBonuses,
			List<String> activeBonuses
	) {}
}
