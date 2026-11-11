package dev.flake;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod identity, shared without importing either bootstrap or a gameplay feature. */
public final class Flake {
	public static final String MOD_ID = "flake";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private Flake() {}
}
