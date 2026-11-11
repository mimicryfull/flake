package dev.flake.bootstrap;

import dev.flake.content.ContentCatalog;
import dev.flake.features.obsidianarmor.ObsidianArmor;
import dev.flake.features.controlhammer.ControlHammerDefinition;
import dev.flake.features.crystalclaw.CrystalClawDefinition;
import dev.flake.features.darkcube.DarkCubeDefinition;
import dev.flake.features.utilities.UtilityEquipment;
import dev.flake.features.matadora.MatadoraDefinition;
import dev.flake.features.voidpaladin.VoidPaladinDefinition;
import dev.flake.features.passives.PassivesDefinition;

/** The explicit composition root. Only this package needs to know every installed feature. */
public final class Features {
	private static boolean definitionsInstalled;
	private static boolean itemsRegistered;
	private static boolean serverHooksRegistered;
	private record Feature(Runnable definitions, Runnable items) {
		Feature(Runnable definitions) { this(definitions, () -> {}); }
	}
	private static final java.util.List<Feature> CONTENT = java.util.List.of(
			new Feature(ControlHammerDefinition::install, ControlHammerDefinition::registerItems),
			new Feature(VoidPaladinDefinition::install, VoidPaladinDefinition::registerItems),
			new Feature(MatadoraDefinition::install, MatadoraDefinition::registerItems),
			new Feature(CrystalClawDefinition::install, CrystalClawDefinition::registerItems),
			new Feature(DarkCubeDefinition::install, DarkCubeDefinition::registerItems),
			new Feature(UtilityEquipment::install, UtilityEquipment::registerItems),
			new Feature(PassivesDefinition::install, PassivesDefinition::registerItems),
			new Feature(dev.flake.features.concussed.ConcussedDefinition::install),
			new Feature(dev.flake.features.bastion.BastionRuntime::install),
			new Feature(dev.flake.features.vampire.VampireRuntime::install),
			new Feature(dev.flake.features.demolitionist.DemolitionistRuntime::install),
			new Feature(ObsidianArmor::install));

	private Features() {}

	/** Safe for metadata-only unit tests: does not register Minecraft items or run server hooks. */
	public static synchronized void installDefinitions() {
		if (definitionsInstalled) return;
		definitionsInstalled = true;
		for (var feature : CONTENT) feature.definitions().run();
	}

	/** Called only by the mod entrypoint, before Minecraft freezes its registries. */
	public static synchronized void registerItems() {
		if (itemsRegistered) return;
		itemsRegistered = true;
		for (var feature : CONTENT) feature.items().run();
	}

	public static synchronized void registerServerHooks() {
		if (serverHooksRegistered) return;
		serverHooksRegistered = true;
		dev.flake.combat.Stuns.install();
		dev.flake.combat.ability.ClassAbilityRuntimes.registerHooks();
		ContentCatalog.registerLifecycle(new dev.flake.content.ContentSpec.Lifecycle() {
			@Override public void register() {}
			@Override public void reset(net.minecraft.server.level.ServerPlayer player) { dev.flake.combat.ability.ClassAbilityRuntimes.resetPlayer(player); }
		});
		ContentCatalog.registerServerHooks();
	}
}
