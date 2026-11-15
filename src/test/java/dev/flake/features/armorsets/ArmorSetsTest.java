package dev.flake.features.armorsets;

import static org.junit.jupiter.api.Assertions.*;

import dev.flake.combat.ability.SkillChargePool;
import dev.flake.features.loadout.Loadout;
import dev.flake.features.loadout.Loadout.ActiveItem;
import dev.flake.features.loadout.Loadout.ArmorMaterial;
import dev.flake.features.loadout.Loadout.FighterClass;
import dev.flake.features.loadout.Loadout.PassiveItem;
import dev.flake.features.loadout.Loadout.Weapon;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArmorSetsTest {
	private static Loadout loadoutWith(ArmorMaterial armor) {
		return new Loadout(FighterClass.BASTION, armor, armor, armor, armor,
				Weapon.IRON_SWORD, PassiveItem.NONE, ActiveItem.NONE, ActiveItem.NONE);
	}

	@Test void allFourSetsShareIronArmorStatsAndReportSetBonuses() {
		var iron = ArmorMaterial.IRON;
		for (var mat : List.of(ArmorMaterial.PHOENIX, ArmorMaterial.BARREL, ArmorMaterial.WIZARD, ArmorMaterial.HYDRA)) {
			assertEquals(iron.helmetArmor(), mat.helmetArmor());
			assertEquals(iron.chestArmor(), mat.chestArmor());
			assertEquals(iron.legsArmor(), mat.legsArmor());
			assertEquals(iron.bootsArmor(), mat.bootsArmor());
			assertEquals(iron.toughness(), mat.toughness());
			var build = loadoutWith(mat);
			assertTrue(build.hasFullSet(mat));
			assertFalse(build.stats().passiveBonuses().isEmpty());
			assertEquals(15, build.stats().armorPoints());
			assertEquals(0.0f, build.stats().armorToughness());
		}
	}

	@Test void phoenixAuraDoublesDamageBelowThirtyPercentHp() {
		assertEquals(2.0f, PhoenixArmor.auraDamage(50.0f, 50.0f), 1e-4f);
		assertEquals(2.0f, PhoenixArmor.auraDamage(15.0f, 50.0f), 1e-4f);
		assertEquals(4.0f, PhoenixArmor.auraDamage(14.9f, 50.0f), 1e-4f);
	}

	@Test void barrelGrantsTwentyPercentSpeedAndTriggersAtThirtyPercentHpLoss() {
		var stats = loadoutWith(ArmorMaterial.BARREL).stats();
		assertEquals(120, stats.speedPercent());
		assertEquals(15.0f, BarrelArmor.thresholdDamage(50.0f), 1e-4f);
		assertEquals(21.0f, BarrelArmor.thresholdDamage(70.0f), 1e-4f);
	}

	@Test void wizardGrantsThirtyPercentSkillDamageAndChargesTwoStacks() {
		assertEquals(13.0f, WizardArmor.scaleSkillDamage(true, 10.0f), 1e-4f);
		assertEquals(10.0f, WizardArmor.scaleSkillDamage(false, 10.0f), 1e-4f);

		var pool = new SkillChargePool();
		int cd = 100;
		// Starts at 1 charge ready; with maxCharges=2 it immediately starts recharging stack #2.
		assertEquals(1, pool.charges(0, 2, cd));
		assertEquals(100, pool.remaining(0, 2, cd));
		assertEquals(1, pool.charges(99, 2, cd));
		assertEquals(2, pool.charges(100, 2, cd));
		assertEquals(0, pool.remaining(100, 2, cd));

		// At 2 stacks, both casts succeed back-to-back with no cooldown; 3rd cast fails cleanly.
		assertTrue(pool.consume(100, 2, cd));
		assertEquals(1, pool.charges(100, 2, cd));
		assertTrue(pool.consume(101, 2, cd));
		assertEquals(0, pool.charges(101, 2, cd));
		assertFalse(pool.consume(102, 2, cd));

		// Recharges back to 1 stack at t=200 and 2 stacks at t=300.
		assertEquals(1, pool.charges(200, 2, cd));
		assertEquals(2, pool.charges(300, 2, cd));
	}

	@Test void hydraAddsTenHeartsAndEscalatesRegenAtOrBelowTenPercentHp() {
		var stats = loadoutWith(ArmorMaterial.HYDRA).stats();
		assertEquals(Loadout.BASE_HEALTH + 20.0f, stats.maxHealth(), 1e-4f);

		assertEquals(1.20f, HydraArmor.regenScale(70.0f, 70.0f), 1e-4f);
		assertEquals(1.20f, HydraArmor.regenScale(7.1f, 70.0f), 1e-4f);
		assertEquals(1.40f, HydraArmor.regenScale(7.0f, 70.0f), 1e-4f);
		assertEquals(1.40f, HydraArmor.regenScale(3.0f, 70.0f), 1e-4f);
	}
}
