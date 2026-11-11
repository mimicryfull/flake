package dev.flake.features.voidpaladin;

import dev.flake.features.loadout.Loadout.Weapon;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.content.ContentCatalog;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Порожній Паладин» (Void Paladin): weapon metadata, kit stack, and server lifecycle. */
public final class VoidPaladinDefinition implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.VOID_PALADIN,
			"flake.weapon.void_paladin.short",
			0,
			0,
			VoidPaladinRuntime::stack
	);

	private VoidPaladinDefinition() {}

	public static void registerItems() {
		VoidPaladinRuntime.bind(dev.flake.content.ItemRegistration.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("flake", "void_paladin"),
				key -> new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties().setId(key)
						.sword(net.minecraft.world.item.ToolMaterial.DIAMOND, 3.5f, -2.4f))));
	}

	public static void install() {
		ContentCatalog.registerWeapon(SPEC);
		ContentCatalog.registerLifecycle(new VoidPaladinDefinition());
	}

	@Override public void register() { VoidPaladinRuntime.register(); }
	@Override public void reset(ServerPlayer player) { VoidPaladinRuntime.reset(player); }
}
