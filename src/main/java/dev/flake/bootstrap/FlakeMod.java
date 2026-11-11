package dev.flake.bootstrap;

import net.fabricmc.api.ModInitializer;
import dev.flake.features.loadout.LoadoutCommands;
import dev.flake.features.arena.ArenaCommands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlakeMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// Item registration must happen before the registry freezes, and must not run in plain unit tests.
		dev.flake.bootstrap.Features.installDefinitions();
		dev.flake.bootstrap.Features.registerItems();
		dev.flake.features.loadout.LoadoutService.install();
		dev.flake.features.duel.DuelRuntime.install();
		dev.flake.features.lobby.LobbyManager.install();
		dev.flake.combat.ability.AbilityInputServer.install();
		LoadoutCommands.register();
		ArenaCommands.register();
		dev.flake.render.vfx.CombatVisuals.register();
		dev.flake.bootstrap.Features.registerServerHooks();
	}
}
