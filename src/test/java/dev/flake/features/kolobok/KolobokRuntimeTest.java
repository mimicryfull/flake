package dev.flake.features.kolobok;

import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KolobokRuntimeTest {
	@Test void specAndTimingMatchArtifactDesign() {
		assertEquals(80, KolobokRuntime.DURATION_TICKS);
		assertEquals(12, KolobokRuntime.FIRE_INTERVAL_TICKS);
		assertEquals(1.0f, KolobokRuntime.SHOT_DAMAGE);
		assertEquals(ActiveItem.KOLOBOK_GUNNER, KolobokDefinition.SPEC.id());
		assertEquals("kolobok", KolobokDefinition.SPEC.buffStatusId());
	}

	@Test void movementClampsStepAndFacesTarget() {
		Vec3 from = new Vec3(0.0, 64.0, 0.0);
		Vec3 to = new Vec3(6.0, 64.0, 8.0);
		Vec3 stepped = KolobokRuntime.stepTowards(from, to, 2.0);
		assertEquals(1.2, stepped.x, 1e-6);
		assertEquals(1.6, stepped.z, 1e-6);

		assertEquals(0.0f, KolobokRuntime.yawTowards(from, new Vec3(0.0, 64.0, 5.0)), 1e-4f);
		assertEquals(-90.0f, KolobokRuntime.yawTowards(from, new Vec3(5.0, 64.0, 0.0)), 1e-4f);
	}
}
