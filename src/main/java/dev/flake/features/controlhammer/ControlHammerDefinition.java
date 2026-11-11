package dev.flake.features.controlhammer;

import dev.flake.combat.item.ActiveCombatItem;

import dev.flake.features.loadout.Loadout.Weapon;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.content.ContentCatalog;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Молот контролю» (Control Hammer): weapon metadata, kit stack, cooldown, and server lifecycle. */
public final class ControlHammerDefinition implements Lifecycle {

    public static final WeaponSpec SPEC = new WeaponSpec(
        Weapon.CONTROL_HAMMER,
        "flake.weapon.control_hammer.short",
        ControlHammerRuntime.COOLDOWN_TICKS,
        0,
        () -> ControlHammerRuntime.stack()
    );

    private ControlHammerDefinition() {}

    public static void install() {
		ContentCatalog.registerStatus(new dev.flake.content.ContentSpec.StatusSpec("stun", "flake.status.stun", ControlHammerRuntime::stack));
        ContentCatalog.registerWeapon(SPEC);
        ContentCatalog.registerLifecycle(new ControlHammerDefinition());
		dev.flake.combat.CombatModifiers.itemCooldown((player, stack, base) -> ControlHammerRuntime.isHammer(stack) ? ControlHammerRuntime.cooldownTotalTicks(player) : base);
    }

	public static void registerItems() {
		ControlHammerRuntime.bind(dev.flake.combat.ItemRegistration.register(ControlHammerRuntime.MODEL, key -> new ActiveCombatItem(
				new net.minecraft.world.item.Item.Properties().setId(key).sword(net.minecraft.world.item.ToolMaterial.IRON, 3f, -2.4f), ControlHammerRuntime.MODEL) {
			@Override protected boolean canConsumeOnClient(net.minecraft.world.entity.player.Player player) { return ControlHammerRuntime.hasClientTarget(player); }
			@Override protected boolean activateServer(ServerPlayer player, net.minecraft.world.entity.Entity target) { return ControlHammerRuntime.use(player, target); }
		}));
	}

    @Override
    public void register() {
        ControlHammerRuntime.register();
    }

    @Override
    public void reset(ServerPlayer player) {
        ControlHammerRuntime.reset(player);
    }
}
