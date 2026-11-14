package dev.flake.features.technician;

import dev.flake.combat.ability.AbilitySlots;
import dev.flake.features.loadout.Loadout.FighterClass;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechnicianRuntimeTest {
	@Test void classAndAbilitiesMatchSpecification() {
		assertEquals(AbilitySlots.Ability.HOMING_MISSILE, AbilitySlots.resolve(FighterClass.TECHNICIAN, 1));
		assertEquals(AbilitySlots.Ability.ATTACK_UAV, AbilitySlots.resolve(FighterClass.TECHNICIAN, 2));
		assertTrue(AbilitySlots.Ability.HOMING_MISSILE.implemented());
		assertTrue(AbilitySlots.Ability.ATTACK_UAV.implemented());
		assertEquals(TechnicianRuntime.MISSILE_FLIGHT_TICKS, AbilitySlots.Ability.HOMING_MISSILE.activeTicks());
		assertEquals(TechnicianRuntime.UAV_FLIGHT_TICKS, AbilitySlots.Ability.ATTACK_UAV.activeTicks());
	}

	@Test void missileSteeringBlastFalloffAndUavOrbitBehavePredictably() {
		Vec3 initial = new Vec3(0.0, 0.0, 1.0);
		Vec3 steered = TechnicianRuntime.steerDirection(initial, new Vec3(1.0, 0.0, 0.0), 0.5);
		assertTrue(steered.x > 0.3 && steered.z > 0.3);
		assertEquals(1.0, steered.length(), 1e-6);

		assertEquals(6.5f, TechnicianRuntime.computeFalloffDamage(0.0, 4.0, 6.5f, 3.2f), 1e-4f);
		assertEquals(4.85f, TechnicianRuntime.computeFalloffDamage(2.0, 4.0, 6.5f, 3.2f), 1e-4f);
		assertEquals(3.2f, TechnicianRuntime.computeFalloffDamage(4.0, 4.0, 6.5f, 3.2f), 1e-4f);

		Vec3 patrol = TechnicianRuntime.computeUavPatrolPos(new Vec3(10.0, 64.0, -5.0), 0.0, 3.0, 64.0, 3.5);
		assertEquals(13.0, patrol.x, 1e-6);
		assertEquals(67.5, patrol.y, 1e-6);
		assertEquals(-5.0, patrol.z, 1e-6);
	}
}
