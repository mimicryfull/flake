package dev.flake;

import net.fabricmc.api.ModInitializer;
import dev.flake.build.LoadoutCommands;
import dev.flake.arena.ArenaCommands;
import dev.flake.duel.ShockwaveVisuals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "flake";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LoadoutCommands.register();
		ArenaCommands.register();
		ShockwaveVisuals.register();
		dev.flake.shared.SliceRegistry.registerServerHooks();
	}
}
