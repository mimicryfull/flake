package dev.flake.features.shaman;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShamanRuntimeTest {
	@Test void totemRequiresGroundAndSnapsToSurface() {
		Vec3 standing = new Vec3(10.0, 64.08, -4.0);
		Vec3 placed = ShamanRuntime.resolveTotemGroundPos(standing, true, true, 64.0);
		assertNotNull(placed);
		assertEquals(64.0, placed.y, 1e-6);
		assertEquals(10.0, placed.x, 1e-6);

		// Tiny step tolerance while touching uneven block seam still allowed
		assertNotNull(ShamanRuntime.resolveTotemGroundPos(new Vec3(0, 64.35, 0), false, true, 64.0));

		// Mid-air jump or over void must reject pedestal spawn
		assertNull(ShamanRuntime.resolveTotemGroundPos(new Vec3(0, 65.25, 0), false, true, 64.0));
		assertNull(ShamanRuntime.resolveTotemGroundPos(new Vec3(0, 64.0, 0), false, false, 64.0));
	}
}
