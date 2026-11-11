package dev.flake.features.concussed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShockwaveAnimationTest {
	@Test
	void expansionStaysInsideAbilityRadiusAndHasRecoil() {
		for (int frame = 0; frame <= 150; frame++) {
			float radius = ShockwaveAnimation.radius(frame / 120.0f, 5);
			assertTrue(Float.isFinite(radius) && radius >= 0.045f && radius <= 5);
		}
		assertTrue(ShockwaveAnimation.radius(0.12f, 5) > 4.9f);
		assertTrue(ShockwaveAnimation.radius(0.25f, 5) < ShockwaveAnimation.radius(0.12f, 5));
		assertEquals(5, ShockwaveAnimation.radius(0.8f, 5), 0.001f);
	}

	@Test
	void hasReadablePeakAndSmoothlyDisappears() {
		assertEquals(0, ShockwaveAnimation.opacity(0));
		assertEquals(1, ShockwaveAnimation.opacity(0.1f));
		assertTrue(ShockwaveAnimation.opacity(0.5f) > ShockwaveAnimation.opacity(0.8f));
		assertEquals(0, ShockwaveAnimation.opacity(ShockwaveAnimation.DURATION_SECONDS));
		assertEquals(0, ShockwaveAnimation.opacity(-1));
		for (int frame = 0; frame <= 150; frame++) {
			float alpha = ShockwaveAnimation.opacity(frame / 120.0f);
			assertTrue(Float.isFinite(alpha) && alpha >= 0 && alpha <= 1);
		}
	}

	@Test
	void gathersAndCompressesBeforeRelease() {
		float release = Shockwave.CHARGE_TICKS / 20.0f;
		assertEquals(0, ShockwaveAnimation.chargeProgress(0));
		assertEquals(1, ShockwaveAnimation.chargeProgress(release));
		assertTrue(ShockwaveAnimation.chargeRadius(0.4f) > ShockwaveAnimation.chargeRadius(0));
		assertTrue(ShockwaveAnimation.chargeRadius(0.8f) > ShockwaveAnimation.chargeRadius(1.0f));
		assertTrue(ShockwaveAnimation.chargeRadius(release) < 0.05f);
		assertEquals(ShockwaveAnimation.chargeRadius(release), ShockwaveAnimation.radius(0, 5), 0.0001f);
		assertTrue(ShockwaveAnimation.radius(0.1f, 5) > ShockwaveAnimation.chargeRadius(release) * 80);
	}
}
