package dev.flake.features.chaosbroadsword;

import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.features.loadout.Loadout.Weapon;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Хаотичний палаш»: heavy 9-damage sword with a weakening sweep. */
public final class ChaosBroadswordDefinition implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.CHAOS_BROADSWORD,
			"flake.weapon.chaos_broadsword.short",
			ChaosBroadswordRuntime.COOLDOWN_TICKS,
			0,
			ChaosBroadswordRuntime::stack
	);

	private ChaosBroadswordDefinition() {}

	public static void install() {
		ContentCatalog.registerWeapon(SPEC);
		ContentCatalog.registerLifecycle(new ChaosBroadswordDefinition());
		ContentCatalog.registerStatus(new dev.flake.content.ContentSpec.StatusSpec(
				"broadsword_weakness", "flake.status.broadsword_weakness", ChaosBroadswordRuntime::stack));
		dev.flake.combat.CombatModifiers.itemCooldown((player, stack, base) ->
				ChaosBroadswordRuntime.isBroadsword(stack) ? dev.flake.combat.Cooldowns.ticks(player, base) : base);
		dev.flake.combat.CombatModifiers.outgoingSkillDamage((attacker, target, damage) ->
				ChaosBroadswordRuntime.scaleOutgoing(attacker, damage));
		dev.flake.combat.CombatStatuses.register((player, now, statuses) -> {
			int remaining = ChaosBroadswordRuntime.remaining(player, now);
			if (remaining > 0) statuses.add(new dev.flake.combat.CombatStatuses.Status(
					"broadsword_weakness", remaining, ChaosBroadswordRuntime.WEAKNESS_TICKS, 0));
		});
	}

	public static void registerItems() {
		ChaosBroadswordRuntime.bind(dev.flake.content.ItemRegistration.register(ChaosBroadswordRuntime.ID, key ->
				new ActiveCombatItem(new net.minecraft.world.item.Item.Properties().setId(key)
						.sword(net.minecraft.world.item.ToolMaterial.IRON, 6.0f, -3.2f), ChaosBroadswordRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, net.minecraft.world.entity.Entity target) {
						return ChaosBroadswordRuntime.sweep(player);
					}
				}));
		ChaosBroadswordRuntime.bindSound(dev.flake.content.ItemRegistration.sound(ChaosBroadswordRuntime.SOUND_ID));
	}

	@Override public void register() { }
	@Override public void reset(ServerPlayer player) { ChaosBroadswordRuntime.reset(player); }
}
