package dev.flake.features.dispatcher;

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

class DispatcherTest {
	@BeforeAll
	static void install() {
		Features.installDefinitions();
	}

	@Test
	void registersDispatcherClassAndBothSkills() {
		var spec = ContentCatalog.fighterClass(FighterClass.DISPATCHER);
		assertNotNull(spec);
		assertEquals(Ability.DISPATCH, AbilitySlots.resolve(FighterClass.DISPATCHER, 1));
		assertEquals(Ability.DESERT_SQUAD, AbilitySlots.resolve(FighterClass.DISPATCHER, 2));
		assertTrue(Ability.DISPATCH.implemented());
		assertTrue(Ability.DESERT_SQUAD.implemented());
		assertEquals(8.0, DispatcherRuntime.DISPATCH_LENGTH);
		assertEquals(0.50f, DispatcherRuntime.DISPATCH_HP_FRACTION);
		assertEquals(4, DispatcherRuntime.SQUAD_COUNT);
		assertEquals(100, Ability.DESERT_SQUAD.activeTicks());
	}
}
