package dev.flake.features.passives;

/** Immutable preview contributions; no runtime hooks or Minecraft state are needed to read a build. */
public record PassiveBonuses(float health, float absorption, float meleeDamage, int speedPercent) {
	public static final PassiveBonuses NONE = new PassiveBonuses(0, 0, 0, 0);
}
