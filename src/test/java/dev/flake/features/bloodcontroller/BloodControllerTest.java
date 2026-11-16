package dev.flake.features.bloodcontroller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.flake.bootstrap.Features;
import dev.flake.combat.ability.AbilitySlots;
import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.content.ContentCatalog;
import dev.flake.features.loadout.Loadout.FighterClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BloodControllerTest {
	@BeforeAll
	static void install() {
		Features.installDefinitions();
	}

	@Test
	void registersClassSpecAndBothAbilities() {
		var spec = ContentCatalog.fighterClass(FighterClass.BLOOD_CONTROLLER);
		assertNotNull(spec);
		assertEquals(Ability.VOODOO_DOLL, AbilitySlots.resolve(FighterClass.BLOOD_CONTROLLER, 1));
		assertEquals(Ability.BLOOD_DRAIN, AbilitySlots.resolve(FighterClass.BLOOD_CONTROLLER, 2));
		assertTrue(Ability.VOODOO_DOLL.implemented());
		assertTrue(Ability.BLOOD_DRAIN.implemented());
		assertEquals(80, Ability.VOODOO_DOLL.activeTicks());
		assertEquals(5.0f, BloodControllerRuntime.DRAIN_DAMAGE);
	}
}
