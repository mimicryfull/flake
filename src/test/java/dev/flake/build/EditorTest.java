package dev.flake.build;

import dev.flake.editor.EditorCatalog;
import dev.flake.editor.EditorCatalog.Slot;
import dev.flake.editor.EditorLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorTest {
	@Test void everyCatalogIncludesRealContentAndPreservesUnrelatedSlots() {
		var build = Loadout.defaultLoadout().withActiveItem(2, Loadout.ActiveItem.SHIELD);
		assertEquals(Loadout.PassiveItem.values().length, EditorCatalog.choices(Slot.PASSIVE, build).size());
		assertEquals(Loadout.ActiveItem.values().length, EditorCatalog.choices(Slot.ACTIVE_1, build).size());
		for (var choice : EditorCatalog.choices(Slot.CLASS, build)) assertEquals(build.activeItem2(), choice.candidate().activeItem2());
		for (var choice : EditorCatalog.choices(Slot.HELMET, build)) assertEquals(build.chestplate(), choice.candidate().chestplate());
		assertEquals("flake item 2 wind_charge", EditorCatalog.command(Slot.ACTIVE_2,
				EditorCatalog.choices(Slot.ACTIVE_2, build).stream().filter(c -> c.id().equals("WIND_CHARGE")).findFirst().orElseThrow()));
	}
	@Test void localizedCaseInsensitiveSearchAndCategoryFilters() {
		var weapons = EditorCatalog.choices(Slot.WEAPON, Loadout.defaultLoadout());
		assertEquals(3, weapons.stream().filter(c -> c.matches("МЕЧ", "Усі")).count());
		assertEquals(2, weapons.stream().filter(c -> c.matches("", "Дальній бій")).count());
		assertEquals(1, weapons.stream().filter(c -> c.matches("DIAMOND SWORD", "Усі")).count());
		assertEquals(1, weapons.stream().filter(c -> c.matches("diamond_sword", "Усі")).count());
		var passive = EditorCatalog.choices(Slot.PASSIVE, Loadout.defaultLoadout());
		assertEquals(1, passive.stream().filter(c -> c.matches("РЕГЕНЕРАЦІЯ", "Захист")).count());
		assertEquals(1, passive.stream().filter(c -> c.matches("20%", "Мобільність")).count());
	}
	@Test void eligibilityIncludesDuplicateActiveItemConstraints() {
		var build = Loadout.defaultLoadout().withActiveItem(1, Loadout.ActiveItem.SHIELD).withPassive(Loadout.PassiveItem.REGENERATION);
		var choices = EditorCatalog.choices(Slot.ACTIVE_2, build);
		assertFalse(choices.stream().filter(c -> c.id().equals("SHIELD")).findFirst().orElseThrow().candidate().errors().isEmpty());
		assertTrue(choices.stream().filter(c -> c.id().equals("WIND_CHARGE")).findFirst().orElseThrow().candidate().errors().isEmpty());
	}
	@Test void comparisonReportsRealStatsWithCorrectDirections() {
		var current = Loadout.defaultLoadout().withPassive(Loadout.PassiveItem.VITALITY)
				.withWeapon(Loadout.Weapon.DIAMOND_SWORD).withActiveItem(1, Loadout.ActiveItem.SHIELD);
		var candidate = current.withPassive(Loadout.PassiveItem.IRON_AEGIS)
				.withArmorSlot(Loadout.ArmorSlot.HELMET, Loadout.ArmorMaterial.DIAMOND);
		var changes = EditorCatalog.comparison(current, candidate);
		var hp = changes.stream().filter(c -> c.stat() == EditorCatalog.Stat.MAX_HEALTH).findFirst().orElseThrow();
		assertEquals(54, hp.current()); assertEquals(50, hp.candidate()); assertEquals(-4, hp.delta()); assertFalse(hp.improvement());
		var absorption = changes.stream().filter(c -> c.stat() == EditorCatalog.Stat.ABSORPTION).findFirst().orElseThrow();
		assertEquals(2, absorption.delta()); assertTrue(absorption.improvement());
		assertEquals(1, changes.stream().filter(c -> c.stat() == EditorCatalog.Stat.ARMOR).findFirst().orElseThrow().delta());
		assertEquals(2, changes.stream().filter(c -> c.stat() == EditorCatalog.Stat.TOUGHNESS).findFirst().orElseThrow().delta());
		assertEquals("Емблема Живучості", EditorCatalog.equippedTitle(Slot.PASSIVE, current));
		assertEquals("Залізна", EditorCatalog.equippedTitle(Slot.HELMET, current));
		assertThrows(UnsupportedOperationException.class, () -> changes.clear());
	}
	@Test void comparisonOmitsUnchangedStatsAndAllRangedDamage() {
		var current = Loadout.defaultLoadout();
		var unchanged = EditorCatalog.comparison(current, current);
		assertTrue(unchanged.isEmpty());
		var sword = current.withWeapon(Loadout.Weapon.DIAMOND_SWORD);
		assertEquals(1, EditorCatalog.comparison(current, sword).stream().filter(c -> c.stat() == EditorCatalog.Stat.MELEE_DAMAGE).findFirst().orElseThrow().delta());
		var bow = current.withWeapon(Loadout.Weapon.BOW);
		assertTrue(EditorCatalog.comparison(current, bow).stream().noneMatch(c -> c.stat() == EditorCatalog.Stat.MELEE_DAMAGE));
		assertTrue(EditorCatalog.comparison(bow, sword).stream().noneMatch(c -> c.stat() == EditorCatalog.Stat.MELEE_DAMAGE));
		assertTrue(EditorCatalog.comparison(bow, bow.withPassive(Loadout.PassiveItem.CRIMSON_FANG)).stream().noneMatch(c -> c.stat() == EditorCatalog.Stat.MELEE_DAMAGE));
		var speed = EditorCatalog.comparison(current, current.withPassive(Loadout.PassiveItem.MOBILITY)).stream().filter(c -> c.stat() == EditorCatalog.Stat.SPEED).findFirst().orElseThrow();
		assertEquals(20, speed.delta()); assertTrue(speed.improvement());
	}
	@Test void layoutsFitSmallGui1080pAnd1440pScalesWithoutClipping() {
		for (int[] size : new int[][] {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1280, 720}, {1920, 1080}, {2560, 1440}}) {
			var l = EditorLayout.fit(size[0], size[1]);
			assertTrue(l.x() >= 0 && l.y() >= 0); assertTrue(l.x() + l.width() <= size[0]);
			assertTrue(l.y() + l.height() <= size[1]); assertTrue(l.slotHeight() >= 24);
			assertTrue(l.slotY(3) + l.slotHeight() <= l.statsY());
			assertTrue(l.centerWidth() >= 80); assertTrue(l.statsY() + l.statsHeight() <= size[1]);
		}
	}
}
