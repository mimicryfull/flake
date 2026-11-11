package dev.flake.combat;

import dev.flake.features.loadout.Loadout;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** PvP health pool (50 HP, temporary balance baseline). A transient modifier: never persisted, removed after the round. */
public final class PvpVitals {
	private static final Identifier MODIFIER = Identifier.fromNamespaceAndPath("flake", "pvp_health");

	private PvpVitals() {}

	public static void apply(ServerPlayer player) {
		var attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null) return;
		attribute.removeModifier(MODIFIER);
		attribute.addTransientModifier(new AttributeModifier(MODIFIER, Loadout.BASE_HEALTH - 20.0, AttributeModifier.Operation.ADD_VALUE));
	}

	private static final Identifier FURY = Identifier.fromNamespaceAndPath("flake", "fury");

	/** «Нестримна лють»: +15% attack speed, transient. */
	public static void fury(ServerPlayer player, boolean on) {
		var speed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (speed == null) return;
		speed.removeModifier(FURY);
		if (on) speed.addTransientModifier(new AttributeModifier(FURY, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	public static void remove(ServerPlayer player) {
		fury(player, false);
		var attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute != null) attribute.removeModifier(MODIFIER);
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
	}
}
