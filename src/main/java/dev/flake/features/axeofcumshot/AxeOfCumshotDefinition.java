package dev.flake.features.axeofcumshot;

import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Сокира Кумшоту» (Axe of Cumshot): thrown artifact that stuns on hit. */
public final class AxeOfCumshotDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.AXE_OF_CUMSHOT,
			"flake.active.axe_of_cumshot.short",
			AxeOfCumshotRuntime.COOLDOWN_TICKS,
			0,
			false,
			AxeOfCumshotRuntime::stack
	);

	private AxeOfCumshotDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new AxeOfCumshotDefinition());
		dev.flake.combat.CombatModifiers.itemCooldown((player, stack, base) ->
				AxeOfCumshotRuntime.isAxe(stack) ? dev.flake.combat.Cooldowns.ticks(player, base) : base);
	}

	public static void registerItems() {
		AxeOfCumshotRuntime.bind(dev.flake.content.ItemRegistration.register(AxeOfCumshotRuntime.ID, key ->
				new ActiveCombatItem(new net.minecraft.world.item.Item.Properties().setId(key).stacksTo(1), AxeOfCumshotRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, net.minecraft.world.entity.Entity target) {
						return AxeOfCumshotRuntime.throwAxe(player);
					}
				}));
		AxeOfCumshotRuntime.bindSound(dev.flake.content.ItemRegistration.sound(AxeOfCumshotRuntime.SOUND_ID));
	}

	@Override public void register() { AxeOfCumshotRuntime.register(); }
	@Override public void reset(ServerPlayer player) { AxeOfCumshotRuntime.reset(player); }
}
