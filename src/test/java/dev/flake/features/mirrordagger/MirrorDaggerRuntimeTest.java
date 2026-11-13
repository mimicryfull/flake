package dev.flake.features.mirrordagger;

import dev.flake.features.loadout.Loadout.Weapon;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MirrorDaggerRuntimeTest {
	@Test void specAndConstantsMatchDesign() {
		assertEquals(Weapon.MIRROR_DAGGER, MirrorDaggerDefinition.SPEC.id());
		assertEquals("flake.weapon.mirror_dagger.short", MirrorDaggerDefinition.SPEC.compactTitleKey());
		assertEquals(3, MirrorDaggerRuntime.COMBO_HITS);
		assertEquals(100, MirrorDaggerRuntime.PIERCE_DURATION_TICKS);
		assertEquals(0.30f, MirrorDaggerRuntime.REDUCTION_IGNORE_FRACTION, 1e-5f);
	}

	@Test void pierceMultiplierIgnoresThirtyPercentOfDamageReduction() {
		// Unarmored, no Resistance -> 1.0x
		assertEquals(1.0f, MirrorDaggerRuntime.pierceMultiplier(0.0f, 0.0f, -1, 5.5f), 1e-5f);

		// Resistance II (amp=1 => 40% reduction, m=0.60):
		// Desired post-mitigation = 1 - 0.70 * 0.40 = 0.72 => scale = 0.72 / 0.60 = 1.20
		float res2Scale = MirrorDaggerRuntime.pierceMultiplier(0.0f, 0.0f, 1, 5.5f);
		assertEquals(1.20f, res2Scale, 1e-5f);
		assertEquals(5.5f * 0.72f, 5.5f * res2Scale * 0.60f, 1e-4f);

		// Full Obsidian armor (20 armor, 12 toughness) + Resistance I (amp=0)
		float obsidianScale = MirrorDaggerRuntime.pierceMultiplier(20.0f, 12.0f, 0, 5.5f);
		assertTrue(obsidianScale > 1.8f && obsidianScale <= MirrorDaggerRuntime.MAX_PIERCE_MULTIPLIER);

		// Extreme mitigation is safely capped
		float capped = MirrorDaggerRuntime.pierceMultiplier(20.0f, 12.0f, 4, 5.5f);
		assertEquals(MirrorDaggerRuntime.MAX_PIERCE_MULTIPLIER, capped, 1e-5f);
	}
}
