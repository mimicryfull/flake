package dev.flake.features.armorsets;

import dev.flake.combat.CombatEquipment;
import dev.flake.combat.CombatModifiers;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.features.loadout.Loadout.ArmorMaterial;
import dev.flake.features.loadout.Loadout.ArmorSlot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Wizard Armor (Iron tier, 4/4 set bonus):
 * Increases outgoing class skill damage by +30% and lets all class skills store up to 2 charges.
 */
public final class WizardArmor implements Lifecycle {
	public static final float SKILL_DAMAGE_MULTIPLIER = 1.30f;
	public static final int MAX_SKILL_CHARGES = 2;

	public static final CustomArmorSet SET = new CustomArmorSet("wizard", ArmorMaterial.WIZARD);
	private static boolean installed;

	private WizardArmor() {}

	public static void registerItems() { SET.registerItems(); }
	public static Item itemFor(ArmorSlot slot) { return SET.itemFor(slot); }

	/** Pure calculation of skill damage with Wizard Armor active. */
	public static float scaleSkillDamage(boolean active, float damage) {
		if (!active || damage <= 0f || !Float.isFinite(damage)) return damage;
		return damage * SKILL_DAMAGE_MULTIPLIER;
	}

	public static void install() {
		if (installed) return;
		installed = true;
		ContentCatalog.registerLifecycle(new WizardArmor());
		CombatEquipment.register(SET::applyKit);
		CombatModifiers.outgoingSkillDamage((attacker, target, damage) ->
				scaleSkillDamage(SET.hasActiveFullSet(attacker), damage));
		CombatModifiers.skillMaxCharges((player, slot) ->
				SET.hasActiveFullSet(player) ? MAX_SKILL_CHARGES : 1);
	}

	@Override
	public void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SET.resetPlayer(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(SET::clearServer);
	}

	@Override
	public void reset(ServerPlayer player) { SET.resetPlayer(player); }
}
