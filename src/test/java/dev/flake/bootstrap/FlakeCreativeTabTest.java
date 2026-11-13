package dev.flake.bootstrap;

import dev.flake.content.ContentCatalog;
import dev.flake.features.loadout.Loadout.ActiveItem;
import dev.flake.features.loadout.Loadout.Weapon;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlakeCreativeTabTest {
	@BeforeAll
	static void install() { Features.installDefinitions(); }

	@Test
	void orderedCategoriesContainNoDuplicatesAndCoverAllCustomContent() {
		assertEquals(FlakeCreativeTab.WEAPONS.size(), new HashSet<>(FlakeCreativeTab.WEAPONS).size());
		assertEquals(FlakeCreativeTab.ARMOR_SETS.size(), new HashSet<>(FlakeCreativeTab.ARMOR_SETS).size());
		assertEquals(FlakeCreativeTab.ARMOR_SLOTS.size(), new HashSet<>(FlakeCreativeTab.ARMOR_SLOTS).size());
		assertEquals(FlakeCreativeTab.ACTIVES.size(), new HashSet<>(FlakeCreativeTab.ACTIVES).size());
		assertEquals(FlakeCreativeTab.PASSIVES.size(), new HashSet<>(FlakeCreativeTab.PASSIVES).size());

		Set<Weapon> vanillaWeapons = EnumSet.of(
				Weapon.IRON_SWORD, Weapon.STONE_AXE, Weapon.DIAMOND_SWORD, Weapon.BOW, Weapon.CROSSBOW);
		for (Weapon weapon : Weapon.values()) {
			if (!vanillaWeapons.contains(weapon)) {
				assertTrue(FlakeCreativeTab.WEAPONS.contains(weapon), "Missing custom weapon in tab: " + weapon);
			}
			assertNotNull(ContentCatalog.weapon(weapon));
		}

		Set<ActiveItem> nonCustomActives = EnumSet.of(
				ActiveItem.NONE, ActiveItem.GOLDEN_APPLE, ActiveItem.SPLASH_HEALING,
				ActiveItem.ENDER_PEARL, ActiveItem.SHIELD, ActiveItem.WIND_CHARGE);
		for (ActiveItem active : ActiveItem.values()) {
			if (!nonCustomActives.contains(active)) {
				assertTrue(FlakeCreativeTab.ACTIVES.contains(active), "Missing custom active item in tab: " + active);
			}
			assertNotNull(ContentCatalog.active(active));
		}

		for (var passive : FlakeCreativeTab.PASSIVES) {
			assertNotNull(ContentCatalog.passive(passive));
		}
	}
}
