package dev.flake.features.chainblade;

import dev.flake.features.loadout.Loadout.Weapon;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChainBladeRuntimeTest {
	@Test void specAndTimingConstantsMatchDesign() {
		assertEquals(Weapon.CHAIN_BLADE, ChainBladeDefinition.SPEC.id());
		assertEquals("flake.weapon.chain_blade.short", ChainBladeDefinition.SPEC.compactTitleKey());
		assertEquals(20, ChainBladeRuntime.COOLDOWN_TICKS);
		assertEquals(160, ChainBladeRuntime.TARGET_COOLDOWN_TICKS);
		assertEquals(50, ChainBladeRuntime.MARK_TICKS);
		assertEquals(1.30f, ChainBladeRuntime.FOLLOWUP_MULTIPLIER, 1e-5f);
		assertEquals(9.0, ChainBladeRuntime.PULL_RANGE, 1e-5);
		assertEquals(3.0, ChainBladeRuntime.PULL_BLOCKS, 1e-5);
	}

	@Test void pullVelocityClampsCloseAndFarDistances() {
		Vec3 dir = new Vec3(1, 0, 0);
		Vec3 far = ChainBladeRuntime.computePullVelocity(dir, 7.0, 0.0);
		assertEquals(0.75, Math.hypot(far.x, far.z), 1e-4);
		assertTrue(far.y >= 0.20 && far.y <= 0.36);

		Vec3 close = ChainBladeRuntime.computePullVelocity(dir, 1.8, 0.0);
		assertTrue(Math.hypot(close.x, close.z) < Math.hypot(far.x, far.z));
		assertTrue(Math.hypot(close.x, close.z) >= 0.15);
	}
}
