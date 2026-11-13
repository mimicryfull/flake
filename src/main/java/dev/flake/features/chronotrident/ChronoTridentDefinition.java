package dev.flake.features.chronotrident;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ItemRegistration;
import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/** Vertical slice for «Хроно-тризуб» (Chrono Trident): purple Chronosphere dome artifact. */
public final class ChronoTridentDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.CHRONO_TRIDENT,
			"flake.active.chrono_trident.short",
			ChronoTridentRuntime.COOLDOWN_TICKS,
			0,
			false,
			ChronoTridentRuntime::stack
	);

	private ChronoTridentDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new ChronoTridentDefinition());
		CombatModifiers.itemCooldown((player, stack, base) ->
				ChronoTridentRuntime.isTrident(stack) ? Cooldowns.ticks(player, base) : base);
	}

	public static void registerItems() {
		ChronoTridentRuntime.bind(ItemRegistration.register(ChronoTridentRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), ChronoTridentRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, Entity target) {
						return ChronoTridentRuntime.castChronosphere(player);
					}
				}));
	}

	@Override public void register() { ChronoTridentRuntime.register(); }
	@Override public void reset(ServerPlayer player) { ChronoTridentRuntime.reset(player); }
}
