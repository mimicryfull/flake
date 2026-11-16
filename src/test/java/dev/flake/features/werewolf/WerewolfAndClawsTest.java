package dev.flake.features.werewolf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.flake.bootstrap.Features;
import dev.flake.combat.ability.AbilitySlots;
import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.content.ContentCatalog;
import dev.flake.features.loadout.Loadout.FighterClass;
import dev.flake.features.loadout.Loadout.Weapon;
import dev.flake.features.shadowclaws.ShadowClawsRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WerewolfAndClawsTest {
	@BeforeAll
	static void install() {
		Features.installDefinitions();
	}

	@Test
	void registersWerewolfClassAndShadowClawsWeapon() {
		var spec = ContentCatalog.fighterClass(FighterClass.WEREWOLF);
		assertNotNull(spec);
		assertEquals(Ability.CLAWS_AND_PAWS, AbilitySlots.resolve(FighterClass.WEREWOLF, 1));
		assertEquals(Ability.NATURES_CALL, AbilitySlots.resolve(FighterClass.WEREWOLF, 2));
		assertTrue(Ability.CLAWS_AND_PAWS.implemented());
		assertTrue(Ability.NATURES_CALL.implemented());
		assertEquals(100, Ability.CLAWS_AND_PAWS.activeTicks());
		assertEquals(100, Ability.NATURES_CALL.activeTicks());
		assertEquals(0.50, WerewolfRuntime.NATURES_SCALE_BONUS);
		assertEquals(5.0, WerewolfRuntime.NATURES_HEALTH_BONUS);

		var weaponSpec = ContentCatalog.weapon(Weapon.SHADOW_CLAWS);
		assertNotNull(weaponSpec);
		assertEquals(3.0f, Weapon.SHADOW_CLAWS.attackDamage());
		assertTrue(ShadowClawsRuntime.BLEED_CHANCE > 0.0f && ShadowClawsRuntime.BLEED_CHANCE < 1.0f);
	}
}
