package dev.flake.shared;

import dev.flake.build.Loadout;
import dev.flake.shared.CombatSlice.ActiveItemSpec;
import dev.flake.shared.CombatSlice.ClassSpec;
import dev.flake.shared.CombatSlice.Lifecycle;
import dev.flake.shared.CombatSlice.PassiveSpec;
import dev.flake.shared.CombatSlice.WeaponSpec;
import dev.flake.slices.armor.ObsidianArmorSlice;
import dev.flake.slices.classes.ClassSlices;
import dev.flake.slices.controlhammer.ControlHammerSlice;
import dev.flake.slices.crystalclaw.CrystalClawSlice;
import dev.flake.slices.darkcube.DarkCubeSlice;
import dev.flake.slices.gadgets.GadgetsSlice;
import dev.flake.slices.matadora.MatadoraSlice;
import dev.flake.slices.passives.PassivesSlice;
import dev.flake.slices.voidpaladin.VoidPaladinSlice;
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
public final class SliceRegistry {
	private static final Map<Loadout.Weapon, WeaponSpec> WEAPONS = new EnumMap<>(Loadout.Weapon.class);
	private static final Map<Loadout.ActiveItem, ActiveItemSpec> ACTIVES = new EnumMap<>(Loadout.ActiveItem.class);
	private static final Map<Loadout.PassiveItem, PassiveSpec> PASSIVES = new EnumMap<>(Loadout.PassiveItem.class);
	private static final Map<Loadout.FighterClass, ClassSpec> CLASSES = new EnumMap<>(Loadout.FighterClass.class);
	private static final List<Lifecycle> LIFECYCLES = new ArrayList<>();
	private static boolean bootstrapped;

	static {
		bootstrap();
	}

	private SliceRegistry() {}

	public static synchronized void bootstrap() {
		if (bootstrapped) return;
		bootstrapped = true;
		ControlHammerSlice.install();
		VoidPaladinSlice.install();
		MatadoraSlice.install();
		CrystalClawSlice.install();
		DarkCubeSlice.install();
		GadgetsSlice.install();
		PassivesSlice.install();
		ClassSlices.install();
		ObsidianArmorSlice.install();
	}

	public static void registerWeapon(WeaponSpec spec) { WEAPONS.put(spec.id(), spec); }
	public static void registerActive(ActiveItemSpec spec) { ACTIVES.put(spec.id(), spec); }
	public static void registerPassive(PassiveSpec spec) { PASSIVES.put(spec.id(), spec); }
	public static void registerClass(ClassSpec spec) { CLASSES.put(spec.id(), spec); }
	public static void registerLifecycle(Lifecycle lifecycle) { LIFECYCLES.add(lifecycle); }

	public static WeaponSpec weapon(Loadout.Weapon id) { return WEAPONS.get(id); }
	public static ActiveItemSpec active(Loadout.ActiveItem id) { return ACTIVES.get(id); }
	public static PassiveSpec passive(Loadout.PassiveItem id) { return PASSIVES.get(id); }
	public static ClassSpec fighterClass(Loadout.FighterClass id) { return CLASSES.get(id); }

	public static void registerServerHooks() {
		bootstrap();
		for (var lifecycle : LIFECYCLES) lifecycle.register();
	}

	public static void resetPlayer(ServerPlayer player) {
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

	/** Resolves active buff duration in seconds for an item stack (e.g. Matadora), or 0 if instantaneous. */
	public static float buffSeconds(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0f;
		for (var active : ACTIVES.values()) {
			if (active.buffTicks() > 0 && active.matches(stack)) return active.buffTicks() / 20f;
		}
		return 0f;
	}
}
