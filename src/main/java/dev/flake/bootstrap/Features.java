package dev.flake.bootstrap;

import dev.flake.content.ContentCatalog;
import dev.flake.features.obsidianarmor.ObsidianArmor;
import dev.flake.features.controlhammer.ControlHammerDefinition;
import dev.flake.features.crystalclaw.CrystalClawDefinition;
import dev.flake.features.darkcube.DarkCubeDefinition;
import dev.flake.features.utilities.UtilityEquipment;
import dev.flake.features.matadora.MatadoraDefinition;
import dev.flake.features.voidpaladin.VoidPaladinDefinition;
import dev.flake.features.axeofcumshot.AxeOfCumshotDefinition;
import dev.flake.features.chronotrident.ChronoTridentDefinition;
import dev.flake.features.deadlybleed.DeadlyBleedDefinition;
import dev.flake.features.junglespear.JungleSpearDefinition;
import dev.flake.features.chaosbroadsword.ChaosBroadswordDefinition;
import dev.flake.features.passives.PassivesDefinition;
import dev.flake.features.chainblade.ChainBladeDefinition;
import dev.flake.features.stormstaff.StormStaffDefinition;
import dev.flake.features.mirrordagger.MirrorDaggerDefinition;
import dev.flake.features.voidanchor.VoidAnchorDefinition;
import dev.flake.features.mirrortrap.MirrorTrapDefinition;
import dev.flake.features.sparksyringe.SparkSyringeDefinition;
import dev.flake.features.stormharpoon.StormHarpoonDefinition;
import dev.flake.features.phasecrystal.PhaseCrystalDefinition;
import dev.flake.features.combatdrone.CombatDroneDefinition;
import dev.flake.features.kolobok.KolobokDefinition;

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
			new Feature(AxeOfCumshotDefinition::install, AxeOfCumshotDefinition::registerItems),
			new Feature(ChronoTridentDefinition::install, ChronoTridentDefinition::registerItems),
			new Feature(DeadlyBleedDefinition::install, DeadlyBleedDefinition::registerItems),
			new Feature(JungleSpearDefinition::install, JungleSpearDefinition::registerItems),
			new Feature(ChaosBroadswordDefinition::install, ChaosBroadswordDefinition::registerItems),
			new Feature(MatadoraDefinition::install, MatadoraDefinition::registerItems),
			new Feature(CrystalClawDefinition::install, CrystalClawDefinition::registerItems),
			new Feature(DarkCubeDefinition::install, DarkCubeDefinition::registerItems),
			new Feature(UtilityEquipment::install, UtilityEquipment::registerItems),
			new Feature(PassivesDefinition::install, PassivesDefinition::registerItems),
			new Feature(dev.flake.features.concussed.ConcussedDefinition::install),
			new Feature(dev.flake.features.bastion.BastionRuntime::install),
			new Feature(dev.flake.features.vampire.VampireRuntime::install),
			new Feature(dev.flake.features.demolitionist.DemolitionistRuntime::install),
			new Feature(ObsidianArmor::install, ObsidianArmor::registerItems),
			new Feature(ChainBladeDefinition::install, ChainBladeDefinition::registerItems),
			new Feature(StormStaffDefinition::install, StormStaffDefinition::registerItems),
			new Feature(MirrorDaggerDefinition::install, MirrorDaggerDefinition::registerItems),
			new Feature(VoidAnchorDefinition::install, VoidAnchorDefinition::registerItems),
			new Feature(MirrorTrapDefinition::install, MirrorTrapDefinition::registerItems),
			new Feature(SparkSyringeDefinition::install, SparkSyringeDefinition::registerItems),
			new Feature(StormHarpoonDefinition::install, StormHarpoonDefinition::registerItems),
			new Feature(PhaseCrystalDefinition::install, PhaseCrystalDefinition::registerItems),
			new Feature(CombatDroneDefinition::install, CombatDroneDefinition::registerItems),
			new Feature(KolobokDefinition::install, KolobokDefinition::registerItems),
			new Feature(dev.flake.features.shaman.ShamanRuntime::install),
			new Feature(dev.flake.features.technician.TechnicianRuntime::install),
			new Feature(dev.flake.features.stormberserker.StormBerserkerArmor::install));

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
		FlakeCreativeTab.register();
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
