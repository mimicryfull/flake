package dev.flake.render.vfx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VfxInfrastructureTest {
	@Test void animationTimeDoesNotDependOnFpsAndDoesNotClampLongFrames() {
		for (int fps : new int[] {5, 20, 60, 240}) {
			var clock = new VfxTimeline();
			clock.sample(0, false);
			float age = 0;
			for (int i = 1; i <= fps; i++) age = clock.sample(i * 1_000_000_000L / fps, false);
			assertEquals(1f, age, 0.00001);
		}
		var clock = new VfxTimeline();
		assertEquals(0, clock.sample(100, false));
		assertEquals(0, clock.sample(1_000_000_100, true));
		assertEquals(0.5f, clock.sample(1_500_000_100, false));
		clock.reset();
		assertEquals(0, clock.sample(9_000_000_000L, false));
	}
	@Test void impactIsBoundedHasRecoilAndFullyDisappears() {
		assertEquals(0, ImpactAnimation.envelope(-1));
		assertEquals(0, ImpactAnimation.envelope(0));
		assertTrue(ImpactAnimation.envelope(0.06f) > 0.9f);
		boolean recoil = false;
		for (int i = 0; i <= 1000; i++) {
			float age = i / 1000f;
			assertTrue(ImpactAnimation.envelope(age) >= 0 && ImpactAnimation.envelope(age) <= 1);
			assertTrue(Math.abs(ImpactAnimation.displacement(age)) <= 1);
			recoil |= ImpactAnimation.displacement(age) < -0.01;
		}
		assertTrue(recoil);
		assertEquals(0, ImpactAnimation.envelope(ImpactAnimation.DURATION));
		assertEquals(0, ImpactAnimation.displacement(ImpactAnimation.DURATION));
	}

	@Test void modularShaderIncludesExistAndAssembleCompletePostShader() throws Exception {
		var root = java.nio.file.Path.of("src/client/resources/assets/flake/shaders");
		var fshPath = root.resolve("post/shockwave.fsh");
		assertTrue(java.nio.file.Files.exists(fshPath), "Missing post/shockwave.fsh");
		String fsh = java.nio.file.Files.readString(fshPath);

		var includePattern = java.util.regex.Pattern.compile("#include\\s+<flake:([^>]+)>");
		var matcher = includePattern.matcher(fsh);
		var assembled = new StringBuilder();
		int includeCount = 0;
		int lastEnd = 0;
		while (matcher.find()) {
			assembled.append(fsh, lastEnd, matcher.start());
			String relPath = matcher.group(1);
			var includeFile = root.resolve("include").resolve(relPath);
			assertTrue(java.nio.file.Files.exists(includeFile), "Missing shader include: " + includeFile);
			String content = java.nio.file.Files.readString(includeFile);
			assertFalse(content.isBlank(), "Empty shader include: " + includeFile);
			assembled.append(content).append('\n');
			lastEnd = matcher.end();
			includeCount++;
		}
		assembled.append(fsh.substring(lastEnd));
		assertEquals(16, includeCount, "Expected 16 modular shader includes in shockwave.fsh");

		String full = assembled.toString();
		for (String requiredSymbol : new String[] {
				"void gravitationalDissonanceWave(",
				"void electricQuake(",
				"void darkBolt(",
				"void maskAura(",
				"void crystalAura(",
				"void critBurst(",
				"void bastionPlate(",
				"void shieldRam(",
				"void bulwarkAura(",
				"void ghoulRibbon(",
				"void ghoulBloodLunge(",
				"void ghoulBloodlustMistAndDroplets(",
				"void ghoulBlood(",
				"void applyBloodlustScreenOverlay(",
				"void applyBulwarkScreenOverlay(",
				"void applyMaskAndCrystalOverlay(",
				"void weaponStreak(",
				"void broadswordSweep("
		}) {
			assertTrue(full.contains(requiredSymbol), "Missing GLSL symbol in assembled shader: " + requiredSymbol);
		}
	}
}
