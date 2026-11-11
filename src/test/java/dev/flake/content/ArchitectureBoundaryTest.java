package dev.flake.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureBoundaryTest {
	@Test void sharedMechanismsDoNotImportConcreteCombatFeaturesOrBootstrap() throws Exception {
		var concreteFeature = Pattern.compile("dev\\.flake\\.features\\.(matadora|crystalclaw|controlhammer|darkcube|voidpaladin|bastion|vampire|demolitionist|obsidianarmor|concussed)\\.");
		for (var file : List.of("src/main/java/dev/flake/content/ContentCatalog.java",
				"src/main/java/dev/flake/combat/CombatModifiers.java", "src/main/java/dev/flake/combat/CombatStatuses.java",
				"src/main/java/dev/flake/combat/Stuns.java", "src/main/java/dev/flake/combat/ability/ClassAbilityRuntimes.java",
				"src/main/java/dev/flake/combat/CombatFeedback.java", "src/main/java/dev/flake/runtime/ServerState.java",
				"src/main/java/dev/flake/content/ItemRegistration.java", "src/main/java/dev/flake/render/vfx/CombatVisuals.java",
				"src/client/java/dev/flake/render/vfx/VfxRenderState.java", "src/client/java/dev/flake/render/vfx/FocusAuraActor.java",
				"src/client/java/dev/flake/render/vfx/VfxBuffer.java")) {
			String source = Files.readString(Path.of(file));
			assertFalse(concreteFeature.matcher(source).find(), file);
			assertFalse(source.contains("import dev.flake.bootstrap."), file);
		}
	}

	@Test void javaPackagesMatchTheirSourceDirectories() throws Exception {
		var declaration = Pattern.compile("(?m)^package ([\\w.]+);");
		for (var root : List.of(Path.of("src/main/java"), Path.of("src/client/java"), Path.of("src/test/java"))) {
			try (var files = Files.walk(root)) {
				for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
					var matcher = declaration.matcher(Files.readString(file));
					assertTrue(matcher.find(), "Missing package: " + file);
					assertEquals(root.resolve(matcher.group(1).replace('.', '/')), file.getParent(), file.toString());
				}
			}
		}
	}

	@Test void commonSourcesNeverLoadClientOnlyMinecraftClasses() throws Exception {
		try (var files = Files.walk(Path.of("src/main/java"))) {
			for (var file : files.filter(path -> path.toString().endsWith(".java")).toList())
				assertFalse(Files.readString(file).contains("net.minecraft.client."), file.toString());
		}
	}
}
