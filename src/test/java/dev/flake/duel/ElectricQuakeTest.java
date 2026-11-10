package dev.flake.duel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElectricQuakeTest {
	@Test
	void damageGrowsWithDistanceButKnockbackStillFalls() {
		var closeHit = ElectricQuake.evaluate(0.0, 1.0, 0.0, 0.0, 1.0).orElseThrow();
		var midHit = ElectricQuake.evaluate(0.0, 1.0, 0.4, 0.0, 4.5).orElseThrow();
		var farHit = ElectricQuake.evaluate(0.0, 1.0, 0.0, 0.0, 8.8).orElseThrow();

		assertTrue(closeHit.damage() < midHit.damage());
		assertTrue(midHit.damage() < farHit.damage());
		assertTrue(closeHit.damage() >= ElectricQuake.MIN_DAMAGE && closeHit.damage() < 3.2f);
		assertTrue(farHit.damage() <= ElectricQuake.MAX_DAMAGE && farHit.damage() > 8.0f);
		assertTrue(closeHit.impulseY() > farHit.impulseY(), "Close target should receive stronger upward jolt");
	}

	@Test
	void damageIsMonotonicAcrossEntireCorridorAndHasExactEndpoints() {
		float previous = 0;
		for (int i = 0; i <= 1000; i++) {
			double distance = ElectricQuake.MIN_FORWARD + (ElectricQuake.RANGE - ElectricQuake.MIN_FORWARD) * i / 1000.0;
			var hit = ElectricQuake.evaluate(1, 0, distance, 0, 0).orElseThrow();
			assertTrue(hit.damage() >= previous);
			assertEquals(2.5 + 6.5 * Math.pow(i / 1000.0, 1.5), hit.damage(), 0.00001);
			previous = hit.damage();
		}
		assertEquals(2.5f, ElectricQuake.evaluate(1, 0, ElectricQuake.MIN_FORWARD, 0, 0).orElseThrow().damage());
		assertEquals(9f, ElectricQuake.evaluate(1, 0, 9, 0, 0).orElseThrow().damage());
	}

	@Test
	void rotatedCorridorKeepsWidthAndKnockbackEndpoints() {
		assertTrue(ElectricQuake.evaluate(0, 2, 1.85, 2.2, 9).isPresent());
		assertTrue(ElectricQuake.evaluate(0, 2, 1.851, 0, 9).isEmpty());
		var near = ElectricQuake.evaluate(1, 0, ElectricQuake.MIN_FORWARD, 0, 0).orElseThrow();
		var far = ElectricQuake.evaluate(1, 0, 9, 0, 0).orElseThrow();
		assertEquals(ElectricQuake.MAX_STRENGTH, near.impulseX(), 0.00001);
		assertEquals(ElectricQuake.MIN_STRENGTH, far.impulseX(), 0.00001);
	}

	@Test
	void rejectsTargetsBehindOutsideCorridorOrTooFar() {
		assertTrue(ElectricQuake.evaluate(1.0, 0.0, -1.5, 0.0, 0.0).isEmpty());
		assertTrue(ElectricQuake.evaluate(1.0, 0.0, 9.5, 0.0, 0.0).isEmpty());
		assertTrue(ElectricQuake.evaluate(1.0, 0.0, 3.0, 0.0, 2.2).isEmpty());
		assertTrue(ElectricQuake.evaluate(1.0, 0.0, 3.0, 2.6, 0.0).isEmpty());
		assertTrue(ElectricQuake.evaluate(Double.NaN, 1.0, 1.0, 0.0, 1.0).isEmpty());
	}

	@Test
	void wavefrontAndShaderOpacityBehaveSmoothly() {
		assertEquals((float) ElectricQuake.MIN_FORWARD, ElectricQuake.wavefrontDistance(0.0f), 0.001f);
		assertEquals((float) ElectricQuake.RANGE, ElectricQuake.wavefrontDistance(0.6f), 0.001f);
		assertEquals(0.0f, ElectricQuake.shaderOpacity(-0.1f));
		assertTrue(ElectricQuake.shaderOpacity(0.15f) > 0.8f);
		assertEquals(0.0f, ElectricQuake.shaderOpacity(ElectricQuake.SHADER_DURATION_SECONDS));
	}
}
