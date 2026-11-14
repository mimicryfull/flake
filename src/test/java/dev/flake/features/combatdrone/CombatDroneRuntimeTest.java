package dev.flake.features.combatdrone;

import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatDroneRuntimeTest {
	@Test void constantsMatchArtifactSpecification() {
		assertEquals(80, CombatDroneRuntime.DURATION_TICKS);
		assertEquals(20, CombatDroneRuntime.ATTACK_INTERVAL_TICKS);
		assertEquals(2.0f, CombatDroneRuntime.DAMAGE);
		assertEquals(20, CombatDroneRuntime.COOLDOWN_TICKS);
		assertEquals(ActiveItem.COMBAT_DRONE, CombatDroneDefinition.SPEC.id());
		assertEquals("combat_drone", CombatDroneDefinition.SPEC.buffStatusId());
	}

	@Test void stepTowardsClampsHorizontalMovementAndFacesTarget() {
		Vec3 from = new Vec3(0.0, 64.0, 0.0);
		Vec3 to = new Vec3(3.0, 65.0, 4.0);
		Vec3 moved = CombatDroneRuntime.stepTowards(from, to, 1.0);
		assertEquals(0.6, moved.x, 1e-6);
		assertEquals(64.0, moved.y, 1e-6);
		assertEquals(0.8, moved.z, 1e-6);

		Vec3 reached = CombatDroneRuntime.stepTowards(from, to, 10.0);
		assertEquals(3.0, reached.x, 1e-6);
		assertEquals(4.0, reached.z, 1e-6);

		assertEquals(0.0f, CombatDroneRuntime.yawTowards(from, new Vec3(0.0, 64.0, 5.0)), 1e-4f);
		assertEquals(-90.0f, CombatDroneRuntime.yawTowards(from, new Vec3(5.0, 64.0, 0.0)), 1e-4f);
	}
}
