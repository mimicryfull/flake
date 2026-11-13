package dev.flake.features.mirrordagger;

import dev.flake.combat.CombatModifiers;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.WeaponSpec;
import dev.flake.content.ItemRegistration;
import dev.flake.features.loadout.Loadout.Weapon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/** Vertical slice for «Дзеркальний кинджал» (Mirror Dagger): fast 5.5-damage dagger with 3-hit reduction-piercing combo. */
public final class MirrorDaggerDefinition implements Lifecycle {
	public static final WeaponSpec SPEC = new WeaponSpec(
			Weapon.MIRROR_DAGGER,
			"flake.weapon.mirror_dagger.short",
			0,
			0,
			MirrorDaggerRuntime::stack
	);

	private MirrorDaggerDefinition() {}

	public static void install() {
		ContentCatalog.registerWeapon(SPEC);
		ContentCatalog.registerLifecycle(new MirrorDaggerDefinition());
		CombatModifiers.afterDamage(MirrorDaggerRuntime::afterMeleeDamage);
		CombatModifiers.damageScale(MirrorDaggerRuntime::scaleMeleeDamage);
	}

	public static void registerItems() {
		// Base 1.0 + ToolMaterial.IRON (2.0) + 2.5 = 5.5 total melee damage.
		// Base 4.0 - 2.0 = 2.0 attack speed (+25% faster than standard 1.6 sword speed).
		MirrorDaggerRuntime.bind(ItemRegistration.register(MirrorDaggerRuntime.ID, key ->
				new Item(new Item.Properties().setId(key)
						.sword(ToolMaterial.IRON, 2.5f, -2.0f))));
	}

	@Override public void register() { MirrorDaggerRuntime.register(); }
	@Override public void reset(ServerPlayer player) { MirrorDaggerRuntime.reset(player); }
}
