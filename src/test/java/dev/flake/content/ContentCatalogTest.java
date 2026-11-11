package dev.flake.content;

import dev.flake.text.FlakeI18n;
import dev.flake.combat.ability.AbilitySlots;
import dev.flake.features.loadout.Loadout;
import dev.flake.features.loadout.editor.EditorCatalog;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContentCatalogTest {
	@org.junit.jupiter.api.BeforeAll static void installContent() { dev.flake.bootstrap.Features.installDefinitions(); }
	@Test void everyWeaponActivePassiveAndClassHasCompleteVerticalSlice() {
		dev.flake.bootstrap.Features.installDefinitions();
		for (var weapon : Loadout.Weapon.values()) {
			var spec = ContentCatalog.weapon(weapon);
			assertNotNull(spec, "Missing weapon slice for " + weapon);
			assertEquals(weapon, spec.id());
			assertFalse(spec.compactTitle().isBlank());
		}
		for (var active : Loadout.ActiveItem.values()) {
			var spec = ContentCatalog.active(active);
			assertNotNull(spec, "Missing active item slice for " + active);
			assertEquals(active, spec.id());
			assertFalse(spec.compactTitle().isBlank());
		}
		for (var passive : Loadout.PassiveItem.values()) {
			var spec = ContentCatalog.passive(passive);
			assertNotNull(spec, "Missing passive slice for " + passive);
			assertEquals(passive, spec.id());
			assertFalse(spec.compactTitle().isBlank());
			assertFalse(spec.category().isBlank());
		}
		for (var cls : Loadout.FighterClass.values()) {
			var spec = ContentCatalog.fighterClass(cls);
			assertNotNull(spec, "Missing class slice for " + cls);
			assertEquals(cls, spec.id());
			assertFalse(spec.roleTitle().isBlank());
			assertFalse(spec.skillSummary().isBlank());
			assertTrue(spec.slot1().implemented());
			assertTrue(spec.slot2().implemented());
		}
	}

	@Test void threeLanguageTranslationKeysAreCompleteAndSwitchDynamically() {
		var ukKeys = FlakeI18n.keysForLang(FlakeI18n.UK_UA);
		var ruKeys = FlakeI18n.keysForLang(FlakeI18n.RU_RU);
		var enKeys = FlakeI18n.keysForLang(FlakeI18n.EN_US);
		assertFalse(ukKeys.isEmpty());
		assertEquals(ukKeys, ruKeys, "uk_ua and ru_ru must have identical translation keys");
		assertEquals(ukKeys, enKeys, "uk_ua and en_us must have identical translation keys");

		try {
			FlakeI18n.setLanguageSupplier(() -> "uk_ua");
			assertEquals("Контужений", Loadout.FighterClass.CONCUSSED.title());
			assertEquals("Молот контролю", Loadout.Weapon.CONTROL_HAMMER.title());
			assertEquals("Гравітаційний дисонанс", AbilitySlots.title("CONCUSSED", 1));
			assertEquals("Емблема Живучості", EditorCatalog.equippedTitle(EditorCatalog.Slot.PASSIVE, Loadout.defaultLoadout().withPassive(Loadout.PassiveItem.VITALITY)));

			FlakeI18n.setLanguageSupplier(() -> "ru_ru");
			assertEquals("Контуженный", Loadout.FighterClass.CONCUSSED.title());
			assertEquals("Молот контроля", Loadout.Weapon.CONTROL_HAMMER.title());
			assertEquals("Гравитационный диссонанс", AbilitySlots.title("CONCUSSED", 1));
			assertEquals("Эмблема Живучести", EditorCatalog.equippedTitle(EditorCatalog.Slot.PASSIVE, Loadout.defaultLoadout().withPassive(Loadout.PassiveItem.VITALITY)));

			FlakeI18n.setLanguageSupplier(() -> "en_us");
			assertEquals("Concussed", Loadout.FighterClass.CONCUSSED.title());
			assertEquals("Control Hammer", Loadout.Weapon.CONTROL_HAMMER.title());
			assertEquals("Gravitational Dissonance", AbilitySlots.title("CONCUSSED", 1));
			assertEquals("Vitality Emblem", EditorCatalog.equippedTitle(EditorCatalog.Slot.PASSIVE, Loadout.defaultLoadout().withPassive(Loadout.PassiveItem.VITALITY)));
		} finally {
			FlakeI18n.setLanguageSupplier(() -> FlakeI18n.UK_UA);
		}
	}
}
