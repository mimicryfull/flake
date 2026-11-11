package dev.flake.content;

import dev.flake.features.loadout.Loadout;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.ClassSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.PassiveSpec;
import dev.flake.content.ContentSpec.WeaponSpec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Central VSA registry: collects all vertical slices so UI, Editor, HUD, and DuelRuntime
 * never hardcode item/class switch statements.
 */
public final class ContentCatalog {
	private static final Map<String, ContentSpec.StatusSpec> STATUSES = new java.util.LinkedHashMap<>();
	public static void registerStatus(ContentSpec.StatusSpec spec) { unique(STATUSES, spec.id(), spec); }
	public static ContentSpec.StatusSpec status(String id) { return STATUSES.get(id); }
	private static final Map<Loadout.Weapon, WeaponSpec> WEAPONS = new EnumMap<>(Loadout.Weapon.class);
	private static final Map<Loadout.ActiveItem, ActiveItemSpec> ACTIVES = new EnumMap<>(Loadout.ActiveItem.class);
	private static final Map<Loadout.PassiveItem, PassiveSpec> PASSIVES = new EnumMap<>(Loadout.PassiveItem.class);
	private static final Map<Loadout.FighterClass, ClassSpec> CLASSES = new EnumMap<>(Loadout.FighterClass.class);
	private static final List<Lifecycle> LIFECYCLES = new ArrayList<>();
	private ContentCatalog() {}

	public static void registerWeapon(WeaponSpec spec) { unique(WEAPONS, spec.id(), spec); }
	public static void registerActive(ActiveItemSpec spec) { unique(ACTIVES, spec.id(), spec); }
	public static void registerPassive(PassiveSpec spec) { unique(PASSIVES, spec.id(), spec); }
	public static void registerClass(ClassSpec spec) { unique(CLASSES, spec.id(), spec); }
	private static <K, V> void unique(Map<K, V> entries, K id, V value) {
		if (entries.putIfAbsent(id, value) != null) throw new IllegalStateException("Duplicate content definition: " + id);
	}
	public static void registerLifecycle(Lifecycle lifecycle) { LIFECYCLES.add(lifecycle); }

	public static WeaponSpec weapon(Loadout.Weapon id) { return WEAPONS.get(id); }
	public static ActiveItemSpec active(Loadout.ActiveItem id) { return ACTIVES.get(id); }
	public static PassiveSpec passive(Loadout.PassiveItem id) { return PASSIVES.get(id); }
	public static ClassSpec fighterClass(Loadout.FighterClass id) { return CLASSES.get(id); }

	public static void registerServerHooks() {
		for (var lifecycle : LIFECYCLES) lifecycle.register();
	}

	public static void resetPlayer(ServerPlayer player) {
		dev.flake.combat.Stuns.reset(player);
		for (var lifecycle : LIFECYCLES) lifecycle.reset(player);
	}

	/** Resolves total cooldown in seconds for any weapon or active item stack on the hotbar. */
	public static float cooldownSeconds(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0f;
		for (var weapon : WEAPONS.values()) {
			if (weapon.cooldownTicks() > 0 && weapon.matches(stack)) return weapon.cooldownTicks() / 20f;
		}
		for (var active : ACTIVES.values()) {
			if (active.cooldownTicks() > 0 && active.matches(stack)) return active.cooldownTicks() / 20f;
		}
		return 0f;
	}

	/** Resolves active buff duration in seconds for an item stack (e.g. MatadoraRuntime), or 0 if instantaneous. */
	public static float buffSeconds(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0f;
		for (var active : ACTIVES.values()) {
			if (active.buffTicks() > 0 && active.matches(stack)) return active.buffTicks() / 20f;
		}
		return 0f;
	}

	public static String buffStatusId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		for (var active : ACTIVES.values()) if (active.matches(stack)) return active.buffStatusId();
		return null;
	}
}
