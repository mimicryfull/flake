package dev.flake.features.loadout.editor;

import dev.flake.features.loadout.Loadout;
import dev.flake.text.FlakeI18n;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Real selectable content and search/filter semantics, shared with focused headless tests. */
public final class EditorCatalog {
	private EditorCatalog() {}
	public enum Slot {
		WEAPON("flake.slot.weapon"), PASSIVE("flake.slot.passive"), ACTIVE_1("flake.slot.active_1"), ACTIVE_2("flake.slot.active_2"),
		HELMET("flake.slot.helmet"), CHESTPLATE("flake.slot.chestplate"), LEGGINGS("flake.slot.leggings"), BOOTS("flake.slot.boots"), CLASS("flake.slot.class");
		private final String titleKey;
		Slot(String titleKey) { this.titleKey = titleKey; }
		public String titleKey() { return titleKey; }
		public String title() { return FlakeI18n.tr(titleKey); }
		public boolean armor() { return ordinal() >= HELMET.ordinal() && ordinal() <= BOOTS.ordinal(); }
		public Loadout.ArmorSlot armorSlot() { return Loadout.ArmorSlot.valueOf(name()); }
	}

	public record Choice(String id, String title, String category, String description,
			List<String> tags, Loadout candidate, boolean selected) {
		public boolean matches(String query, String filter) {
			String haystack = (title + " " + category + " " + description + " " + id + " " + id.replace('_', ' ') + " " + String.join(" ", tags)).toLowerCase(Locale.ROOT);
			boolean allFilter = filter == null || filter.isEmpty() || FlakeI18n.isTranslationOf("flake.filter.all", filter);
			return (allFilter || tags.contains(filter) || category.equalsIgnoreCase(filter))
					&& haystack.contains(query.strip().toLowerCase(Locale.ROOT));
		}
		public String eligibility() {
			return selected ? FlakeI18n.tr("flake.eligibility.selected")
					: candidate.errors().isEmpty() ? FlakeI18n.tr("flake.eligibility.available")
					: String.join(" · ", candidate.errors());
		}
	}

	public enum Stat {
		MAX_HEALTH("flake.stat.max_health.title", "flake.stat.max_health.unit"),
		ABSORPTION("flake.stat.absorption.title", "flake.stat.absorption.unit"),
		ARMOR("flake.stat.armor.title", "flake.stat.armor.unit"),
		TOUGHNESS("flake.stat.toughness.title", "flake.stat.toughness.unit"),
		MELEE_DAMAGE("flake.stat.melee_damage.title", "flake.stat.melee_damage.unit"),
		SPEED("flake.stat.speed.title", "flake.stat.speed.unit");

		private final String titleKey, unitKey;
		Stat(String titleKey, String unitKey) { this.titleKey = titleKey; this.unitKey = unitKey; }
		public String title() { return FlakeI18n.tr(titleKey); }
		public String unit() { return FlakeI18n.tr(unitKey); }
	}

	public record StatDelta(Stat stat, float current, float candidate) {
		public float delta() { return candidate - current; }
		public boolean improvement() { return delta() > 0; }
	}

	/** Only changed, directly supported stats. No reduction/projectile estimates. */
	public static List<StatDelta> comparison(Loadout current, Loadout candidate) {
		var before = current.stats(); var after = candidate.stats();
		var result = new ArrayList<StatDelta>();
		addDelta(result, Stat.MAX_HEALTH, before.maxHealth(), after.maxHealth());
		addDelta(result, Stat.ABSORPTION, before.absorption(), after.absorption());
		addDelta(result, Stat.ARMOR, before.armorPoints(), after.armorPoints());
		addDelta(result, Stat.TOUGHNESS, before.armorToughness(), after.armorToughness());
		if (!current.weapon().ranged() && !candidate.weapon().ranged())
			addDelta(result, Stat.MELEE_DAMAGE, before.attackDamage(), after.attackDamage());
		addDelta(result, Stat.SPEED, before.speedPercent(), after.speedPercent());
		return List.copyOf(result);
	}
	private static void addDelta(List<StatDelta> result, Stat stat, float current, float candidate) {
		if (Float.isFinite(current) && Float.isFinite(candidate) && Float.compare(current, candidate) != 0)
			result.add(new StatDelta(stat, current, candidate));
	}
	public static String equippedTitle(Slot slot, Loadout build) {
		if (slot.armor()) return build.armorAt(slot.armorSlot()).title();
		return switch (slot) {
			case WEAPON -> build.weapon().title(); case PASSIVE -> build.passiveItem().title();
			case ACTIVE_1 -> build.activeItem1().title(); case ACTIVE_2 -> build.activeItem2().title();
			case CLASS -> build.fighterClass().title(); default -> throw new IllegalArgumentException("Unsupported slot");
		};
	}
	public static List<Choice> choices(Slot slot, Loadout build) {
		var result = new ArrayList<Choice>();
		if (slot.armor()) {
			var armorSlot = slot.armorSlot();
			for (var item : Loadout.ArmorMaterial.values()) {
				var candidate = build.withArmorSlot(armorSlot, item);
				String setSuffix;
				if (item == Loadout.ArmorMaterial.OBSIDIAN) {
					setSuffix = FlakeI18n.tr(candidate.hasFullObsidianSet()
							? "flake.armor.obsidian.desc_set"
							: "flake.armor.obsidian.desc_piece");
				} else {
					setSuffix = FlakeI18n.tr(candidate.hasFullArmorSet() ? "flake.armor.desc.full_set" : "flake.armor.desc.single");
				}
				String description = FlakeI18n.tr("flake.armor.choice_desc", item.armorFor(armorSlot), item.toughness(), setSuffix);
				result.add(new Choice(item.name(), item.title(), armorSlot.title(), description,
						List.of(item.title(), FlakeI18n.tr(candidate.hasFullArmorSet() ? "flake.tag.full_set" : "flake.tag.single_armor")),
						candidate, item == build.armorAt(armorSlot)));
			}
		} else switch (slot) {
			case WEAPON -> {
				for (var item : Loadout.Weapon.values()) {
					var tags = new ArrayList<String>(); tags.add(FlakeI18n.tr(item.ranged() ? "flake.tag.ranged" : "flake.tag.melee"));
					if (item.name().contains("SWORD")) tags.add(FlakeI18n.tr("flake.tag.sword"));
					if (item.name().contains("AXE")) tags.add(FlakeI18n.tr("flake.tag.axe"));
					result.add(new Choice(item.name(), item.title(), item.category(), item.activeInfo(), List.copyOf(tags), build.withWeapon(item), item == build.weapon()));
				}
			}
			case PASSIVE -> {
				for (var item : Loadout.PassiveItem.values()) {
					var spec = dev.flake.content.ContentCatalog.passive(item);
					String category = spec != null ? spec.category() : FlakeI18n.tr("flake.category.defense");
					result.add(new Choice(item.name(), item.title(), category, item.description(), List.of(category), build.withPassive(item), item == build.passiveItem()));
				}
			}
			case ACTIVE_1, ACTIVE_2 -> {
				int index = slot == Slot.ACTIVE_1 ? 1 : 2;
				for (var item : Loadout.ActiveItem.values()) result.add(new Choice(item.name(), item.title(), item.category(), item.description(), List.of(item.category()), build.withActiveItem(index, item), item == build.activeItemAt(index)));
			}
			case CLASS -> {
				for (var item : Loadout.FighterClass.values()) result.add(new Choice(item.name(), item.title(), FlakeI18n.tr("flake.category.class"), item.description(), List.of(), build.withClass(item), item == build.fighterClass()));
			}
			default -> throw new IllegalArgumentException("Unsupported slot: " + slot);
		}
		return List.copyOf(result);
	}
	public static String command(Slot slot, Choice choice) {
		String prefix = slot.armor() ? "slot " + slot.name().toLowerCase(Locale.ROOT) : switch (slot) {
			case WEAPON -> "weapon"; case PASSIVE -> "passive"; case ACTIVE_1 -> "item 1"; case ACTIVE_2 -> "item 2"; case CLASS -> "class";
			default -> throw new IllegalArgumentException("Unsupported slot");
		};
		return "flake " + prefix + " " + choice.id().toLowerCase(Locale.ROOT);
	}
}
