package dev.flake.features.loadout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LoadoutTest {
	@Test void loadoutSurvivesWorldRestart(@TempDir Path world) {
		var id = UUID.randomUUID();
		var build = Loadout.defaultLoadout().withClass(Loadout.FighterClass.BASTION)
				.withWeapon(Loadout.Weapon.VOID_PALADIN).withPassive(Loadout.PassiveItem.MELTING_AMETHYST)
				.withActiveItem(1, Loadout.ActiveItem.CRYSTAL_CLAW).withArmorSlot(Loadout.ArmorSlot.HELMET, Loadout.ArmorMaterial.GOLD);
		var first = new LoadoutManager();
		first.bind(world);
		first.update(id, ignored -> build);
		var restarted = new LoadoutManager();
		restarted.bind(world);
		assertEquals(build, restarted.get(id));
	}

	@Test void classAndArmorAreIndependentAndClassPreservesAllEquipment() {
		var build = Loadout.defaultLoadout().withActiveItem(1, Loadout.ActiveItem.WIND_CHARGE)
				.withActiveItem(2, Loadout.ActiveItem.SHIELD).withPassive(Loadout.PassiveItem.VITALITY)
				.withArmorSlot(Loadout.ArmorSlot.HELMET, Loadout.ArmorMaterial.GOLD);
		for (var fighter : Loadout.FighterClass.values()) {
			var changed = build.withClass(fighter);
			assertTrue(changed.errors().isEmpty()); assertEquals(build.helmet(), changed.helmet());
			assertEquals(build.weapon(), changed.weapon()); assertEquals(build.passiveItem(), changed.passiveItem());
			assertEquals(build.activeItem1(), changed.activeItem1()); assertEquals(build.activeItem2(), changed.activeItem2());
		}
	}
	@Test void exactlyTwoActiveItemsWithIndependentOneBasedMutation() {
		var base = Loadout.defaultLoadout();
		var changed = base.withActiveItem(1, Loadout.ActiveItem.SHIELD).withActiveItem(2, Loadout.ActiveItem.WIND_CHARGE);
		assertEquals(Loadout.ActiveItem.SHIELD, changed.activeItemAt(1));
		assertEquals(Loadout.ActiveItem.WIND_CHARGE, changed.activeItemAt(2));
		assertEquals(Loadout.ActiveItem.WIND_CHARGE, changed.withActiveItem(1, Loadout.ActiveItem.NONE).activeItem2());
		assertThrows(IllegalArgumentException.class, () -> base.withActiveItem(0, Loadout.ActiveItem.NONE));
		assertThrows(IllegalArgumentException.class, () -> base.withActiveItem(3, Loadout.ActiveItem.NONE));
		assertThrows(NullPointerException.class, () -> base.withActiveItem(1, null));
		assertFalse(changed.withActiveItem(2, Loadout.ActiveItem.SHIELD).errors().isEmpty());
	}
	@Test void authoritativeValidationCoversEveryWeaponPassiveAndTwoItemCombination() {
		var manager = new LoadoutManager(); var id = UUID.randomUUID();
		for (var weapon : Loadout.Weapon.values()) for (var passive : Loadout.PassiveItem.values())
			for (var first : Loadout.ActiveItem.values()) for (var second : Loadout.ActiveItem.values()) {
				var candidate = Loadout.defaultLoadout().withWeapon(weapon).withPassive(passive).withActiveItem(1, first).withActiveItem(2, second);
				boolean valid = first == Loadout.ActiveItem.NONE || first != second;
				assertEquals(valid, candidate.errors().isEmpty(), candidate.toString());
				var previous = manager.get(id);
				manager.update(id, ignored -> candidate);
				assertEquals(valid ? candidate : previous, manager.get(id));
			}
	}
	@Test void serverManagerRejectsDuplicateItemsWithoutChangingPreviousState() {
		var manager = new LoadoutManager(); var player = UUID.randomUUID(); var original = manager.get(player);
		var rejected = manager.update(player, old -> old.withActiveItem(1, Loadout.ActiveItem.SHIELD).withActiveItem(2, Loadout.ActiveItem.SHIELD));
		assertFalse(rejected.errors().isEmpty()); assertEquals(original, manager.get(player));
	}
	@Test void losslessLegacyMigrationPreservesContentAndIndependentArmor() {
		var migrated = new Loadout(Loadout.FighterClass.CONCUSSED, Loadout.ArmorMaterial.GOLD, Loadout.ArmorMaterial.IRON,
				Loadout.ArmorMaterial.NONE, Loadout.ArmorMaterial.DIAMOND, Loadout.Weapon.IRON_SWORD,
				List.of(Loadout.Accessory.VITALITY, Loadout.Accessory.WIND_CHARGE), Loadout.PassiveItem.NONE, Loadout.UtilityItem.SHIELD);
		assertTrue(migrated.errors().isEmpty());
		assertEquals(Loadout.PassiveItem.VITALITY, migrated.passiveItem());
		assertEquals(Loadout.ActiveItem.WIND_CHARGE, migrated.activeItem1()); assertEquals(Loadout.ActiveItem.SHIELD, migrated.activeItem2());
		assertEquals(Loadout.ArmorMaterial.NONE, migrated.leggings()); assertEquals(Loadout.ArmorMaterial.GOLD, migrated.helmet());
	}
	@Test void lossyLegacyMigrationRetainsConflictDetailsAndCannotBeEquipped() {
		var source = new ArrayList<>(List.of(Loadout.Accessory.VITALITY, Loadout.Accessory.MOBILITY));
		var migrated = new Loadout(Loadout.FighterClass.BASTION, Loadout.Armor.IRON, Loadout.Weapon.IRON_SWORD, source);
		source.clear();
		assertFalse(migrated.errors().isEmpty()); assertTrue(migrated.migrationConflicts().getFirst().contains("MOBILITY"));
		assertFalse(migrated.withClass(Loadout.FighterClass.VAMPIRE).errors().isEmpty());
		assertThrows(UnsupportedOperationException.class, () -> migrated.migrationConflicts().clear());
		var manager = new LoadoutManager(); var id = UUID.randomUUID();
		manager.update(id, old -> migrated); assertEquals(Loadout.defaultLoadout(), manager.get(id));
	}
	@Test void independentArmorAndHonestStats() {
		var base = Loadout.defaultLoadout(); assertTrue(base.hasFullArmorSet()); assertEquals(15, base.stats().armorPoints());
		assertEquals(60f, base.stats().damageReductionPercent()); assertTrue(base.stats().passiveBonuses().isEmpty());
		var mixed = base.withArmorSlot(Loadout.ArmorSlot.HELMET, Loadout.ArmorMaterial.DIAMOND)
				.withArmorSlot(Loadout.ArmorSlot.BOOTS, Loadout.ArmorMaterial.NONE).withPassive(Loadout.PassiveItem.VITALITY);
		assertFalse(mixed.hasFullArmorSet()); assertEquals(14, mixed.stats().armorPoints()); assertEquals(54f, mixed.stats().maxHealth());
		assertEquals(Loadout.ArmorMaterial.IRON, mixed.chestplate());
		assertEquals(120, base.withPassive(Loadout.PassiveItem.MOBILITY).stats().speedPercent());
		assertTrue(Float.isNaN(base.withWeapon(Loadout.Weapon.BOW).stats().attackDamage()));
		assertEquals(9f, base.withPassive(Loadout.PassiveItem.CRIMSON_FANG).stats().attackDamage());
		assertEquals(base.stats().damageReductionPercent(), base.withPassive(Loadout.PassiveItem.STONE_PLATE).stats().damageReductionPercent());
	}

	@Test void obsidianArmorSetMechanicsAndEditorStatTradeoffs() {
		var base = Loadout.defaultLoadout();
		var onePiece = base.withArmorSlot(Loadout.ArmorSlot.CHESTPLATE, Loadout.ArmorMaterial.OBSIDIAN);
		assertEquals(1, onePiece.obsidianPieces());
		assertFalse(onePiece.hasFullObsidianSet());
		assertEquals(97, onePiece.stats().speedPercent());
		assertTrue(onePiece.stats().passiveBonuses().isEmpty());

		var deltas = dev.flake.features.loadout.editor.EditorCatalog.comparison(base, onePiece);
		assertTrue(deltas.stream().anyMatch(d -> d.stat() == dev.flake.features.loadout.editor.EditorCatalog.Stat.ARMOR && d.delta() == 2f));
		assertTrue(deltas.stream().anyMatch(d -> d.stat() == dev.flake.features.loadout.editor.EditorCatalog.Stat.TOUGHNESS && d.delta() == 3f));
		assertTrue(deltas.stream().anyMatch(d -> d.stat() == dev.flake.features.loadout.editor.EditorCatalog.Stat.SPEED && d.delta() == -3f));

		var fullObsidian = base
				.withArmorSlot(Loadout.ArmorSlot.HELMET, Loadout.ArmorMaterial.OBSIDIAN)
				.withArmorSlot(Loadout.ArmorSlot.CHESTPLATE, Loadout.ArmorMaterial.OBSIDIAN)
				.withArmorSlot(Loadout.ArmorSlot.LEGGINGS, Loadout.ArmorMaterial.OBSIDIAN)
				.withArmorSlot(Loadout.ArmorSlot.BOOTS, Loadout.ArmorMaterial.OBSIDIAN);
		assertEquals(4, fullObsidian.obsidianPieces());
		assertTrue(fullObsidian.hasFullArmorSet());
		assertTrue(fullObsidian.hasFullObsidianSet());
		assertEquals(20, fullObsidian.stats().armorPoints());
		assertEquals(12.0f, fullObsidian.stats().armorToughness());
		assertEquals(88, fullObsidian.stats().speedPercent());
		assertEquals(108, fullObsidian.withPassive(Loadout.PassiveItem.MOBILITY).stats().speedPercent());
		assertEquals(1, fullObsidian.stats().passiveBonuses().size());

		assertEquals(-0.12, dev.flake.features.obsidianarmor.ObsidianArmor.speedModifierForPieces(4), 1e-6);
		assertEquals(4.8f, dev.flake.features.obsidianarmor.ObsidianArmor.dampenSkillDamage(true, 6.0f), 1e-4f);
		assertEquals(6.0f, dev.flake.features.obsidianarmor.ObsidianArmor.dampenSkillDamage(false, 6.0f), 1e-4f);
		assertFalse(dev.flake.features.obsidianarmor.ObsidianArmor.shouldTriggerPulse(10.0f, 5.0f, 100, 0));
		assertTrue(dev.flake.features.obsidianarmor.ObsidianArmor.shouldTriggerPulse(11.5f, 5.0f, 100, 0));
		assertFalse(dev.flake.features.obsidianarmor.ObsidianArmor.shouldTriggerPulse(11.5f, 5.0f, 100, 150));
	}
	@Test void atomicApplyRejectsStaleRevisionAndKeepsPreviousBuild() {
		var manager = new LoadoutManager(); var id = UUID.randomUUID();
		var base = manager.get(id);
		long revision = manager.revision(id);
		var desired = base.withWeapon(Loadout.Weapon.DIAMOND_SWORD);
		var applied = manager.apply(id, revision, desired);
		assertTrue(applied.ok(), applied.status().name());
		assertEquals(desired, manager.get(id));
		assertTrue(manager.revision(id) > revision);
		var stale = manager.apply(id, revision, base.withPassive(Loadout.PassiveItem.MOBILITY));
		assertEquals(LoadoutManager.ApplyStatus.STALE, stale.status());
		assertEquals(desired, manager.get(id));
		assertEquals(LoadoutManager.ApplyStatus.INVALID, manager.apply(id, manager.revision(id),
				base.withActiveItem(1, Loadout.ActiveItem.SHIELD).withActiveItem(2, Loadout.ActiveItem.SHIELD)).status());
		assertEquals(desired, manager.get(id));
	}

	@Test void storeDistinguishesMissingFromCorruptBuild(@TempDir Path world) throws Exception {
		var store = new LoadoutStore(world);
		var id = UUID.randomUUID();
		assertEquals(LoadoutStore.State.MISSING, store.loadResult(id).state());
		Files.createDirectories(world);
		Files.writeString(world.resolve(id + ".properties"), "version=1\nclass=NOT_A_CLASS\n");
		assertEquals(LoadoutStore.State.CORRUPT, store.loadResult(id).state());
		store.save(id, Loadout.defaultLoadout());
		assertEquals(LoadoutStore.State.LOADED, store.loadResult(id).state());
	}

	@Test void failedDiskWriteKeepsPreviousBuildAuthoritative(@TempDir Path world) throws Exception {
		var blocked = world.resolve("builds-is-a-file");
		Files.writeString(blocked, "not a directory");
		var manager = new LoadoutManager();
		manager.bind(blocked);
		var id = UUID.randomUUID();
		var base = manager.get(id);
		long revision = manager.revision(id);
		var result = manager.updateChecked(id, old -> base.withWeapon(Loadout.Weapon.DIAMOND_SWORD));
		assertEquals(LoadoutManager.ApplyStatus.SAVE_FAILED, result.status());
		assertEquals(base, result.build());
		assertEquals(base, manager.get(id));
		assertEquals(revision, manager.revision(id));
		var applied = manager.apply(id, revision, base.withPassive(Loadout.PassiveItem.MOBILITY));
		assertEquals(LoadoutManager.ApplyStatus.SAVE_FAILED, applied.status());
		assertEquals(base, applied.build());
		assertEquals(base, manager.get(id));
	}

	@Test void managerRetainsFullLegacySourceUntilExplicitConflictResolution() {
		var manager = new LoadoutManager(); var id = UUID.randomUUID();
		var source = new LoadoutManager.LegacyBuild(Loadout.FighterClass.BASTION,
				Loadout.ArmorMaterial.IRON, Loadout.ArmorMaterial.IRON, Loadout.ArmorMaterial.IRON, Loadout.ArmorMaterial.IRON,
				Loadout.Weapon.IRON_SWORD, List.of(Loadout.Accessory.VITALITY, Loadout.Accessory.MOBILITY),
				Loadout.PassiveItem.IRON_AEGIS, Loadout.UtilityItem.SHIELD);
		var imported = manager.importLegacy(id, source);
		assertFalse(imported.accepted()); assertEquals(source, manager.pendingMigration(id).orElseThrow().original());
		assertEquals(Loadout.defaultLoadout(), manager.get(id));
		assertFalse(manager.resolveMigration(id, imported.candidate())); assertTrue(manager.pendingMigration(id).isPresent());
		assertTrue(manager.resolveMigration(id, Loadout.defaultLoadout().withPassive(Loadout.PassiveItem.MOBILITY)));
		assertTrue(manager.pendingMigration(id).isEmpty());
		manager.remove(id); assertEquals(Loadout.defaultLoadout(), manager.get(id));
	}
}
