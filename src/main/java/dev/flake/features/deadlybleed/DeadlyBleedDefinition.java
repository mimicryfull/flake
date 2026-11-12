package dev.flake.features.deadlybleed;

import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.features.loadout.Loadout.Weapon;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Смертельна кровотеча»: 7-damage sword with a 200% right-click crit. */
public final class DeadlyBleedDefinition implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.DEADLY_BLEED,
			"flake.weapon.deadly_bleed.short",
			DeadlyBleedRuntime.COOLDOWN_TICKS,
			0,
			DeadlyBleedRuntime::stack
	);

	private DeadlyBleedDefinition() {}

	public static void install() {
		ContentCatalog.registerWeapon(SPEC);
		ContentCatalog.registerLifecycle(new DeadlyBleedDefinition());
		dev.flake.combat.CombatModifiers.itemCooldown((player, stack, base) ->
				DeadlyBleedRuntime.isBlade(stack) ? dev.flake.combat.Cooldowns.ticks(player, base) : base);
	}

	public static void registerItems() {
		DeadlyBleedRuntime.bind(dev.flake.content.ItemRegistration.register(DeadlyBleedRuntime.ID, key ->
				new ActiveCombatItem(new net.minecraft.world.item.Item.Properties().setId(key)
						.sword(net.minecraft.world.item.ToolMaterial.IRON, 4.0f, -2.4f), DeadlyBleedRuntime.ID) {
					@Override protected boolean canConsumeOnClient(net.minecraft.world.entity.player.Player player) {
						return DeadlyBleedRuntime.hasClientTarget(player);
					}
					@Override protected boolean activateServer(ServerPlayer player, net.minecraft.world.entity.Entity target) {
						return DeadlyBleedRuntime.crit(player, target);
					}
				}));
		DeadlyBleedRuntime.bindSound(dev.flake.content.ItemRegistration.sound(DeadlyBleedRuntime.SOUND_ID));
	}

	@Override public void register() { }
	@Override public void reset(ServerPlayer player) { DeadlyBleedRuntime.reset(player); }
}
