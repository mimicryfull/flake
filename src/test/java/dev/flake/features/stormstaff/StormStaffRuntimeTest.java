package dev.flake.features.stormstaff;

import dev.flake.features.loadout.Loadout.Weapon;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StormStaffRuntimeTest {
	@Test void specAndTimingConstantsMatchDesign() {
		assertEquals(Weapon.STORM_STAFF, StormStaffDefinition.SPEC.id());
		assertEquals("flake.weapon.storm_staff.short", StormStaffDefinition.SPEC.compactTitleKey());
		assertEquals(20, StormStaffRuntime.COOLDOWN_TICKS);
		assertEquals(120, StormStaffRuntime.TARGET_COOLDOWN_TICKS);
		assertEquals(8.0, StormStaffRuntime.RANGE, 1e-5);
		assertEquals(0.9, StormStaffRuntime.BEAM_RADIUS, 1e-5);
		assertEquals(4.0f, StormStaffRuntime.BEAM_DAMAGE, 1e-5f);
		assertEquals(6, StormStaffRuntime.STUN_TICKS);
	}

	@Test void capsuleHitDetectionMatchesRadiusAndRange() {
		Vec3 rayStart = new Vec3(0.0, 1.6, 0.0);
		Vec3 rayEnd = new Vec3(0.0, 1.6, 8.0);
		double maxDistSq = StormStaffRuntime.BEAM_RADIUS * StormStaffRuntime.BEAM_RADIUS;

		// Direct hit at z = 5
		double direct = StormStaffRuntime.distanceSqSegmentToSegment(
				rayStart, rayEnd, new Vec3(0.0, 0.2, 5.0), new Vec3(0.0, 1.8, 5.0));
		assertEquals(0.0, direct, 1e-6);

		// Lateral offset 0.85m -> inside 0.9m capsule
		double grazing = StormStaffRuntime.distanceSqSegmentToSegment(
				rayStart, rayEnd, new Vec3(0.85, 0.2, 4.0), new Vec3(0.85, 1.8, 4.0));
		assertTrue(grazing <= maxDistSq);

		// Lateral offset 1.05m -> outside 0.9m capsule
		double wide = StormStaffRuntime.distanceSqSegmentToSegment(
				rayStart, rayEnd, new Vec3(1.05, 0.2, 4.0), new Vec3(1.05, 1.8, 4.0));
		assertTrue(wide > maxDistSq);

		// Target 1.5m past clipped ray end -> outside capsule
		double pastEnd = StormStaffRuntime.distanceSqSegmentToSegment(
				rayStart, rayEnd, new Vec3(0.0, 0.2, 9.5), new Vec3(0.0, 1.8, 9.5));
		assertTrue(pastEnd > maxDistSq);
	}
}
