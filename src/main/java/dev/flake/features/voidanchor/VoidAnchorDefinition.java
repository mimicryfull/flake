package dev.flake.features.voidanchor;

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

/** Vertical slice for «Якір пустоти» (Void Anchor): delayed ground anchor that pulls and damages nearby enemies. */
public final class VoidAnchorDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.VOID_ANCHOR,
			"flake.active.void_anchor.short",
			VoidAnchorRuntime.COOLDOWN_TICKS,
			0,
			false,
			VoidAnchorRuntime::stack
	);

	private VoidAnchorDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new VoidAnchorDefinition());
		CombatModifiers.itemCooldown((player, stack, base) ->
				VoidAnchorRuntime.isItem(stack) ? Cooldowns.ticks(player, base) : base);
	}

	public static void registerItems() {
		VoidAnchorRuntime.bind(ItemRegistration.register(VoidAnchorRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), VoidAnchorRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, Entity target) {
						return VoidAnchorRuntime.activate(player);
					}
				}));
	}

	@Override public void register() { VoidAnchorRuntime.register(); }
	@Override public void reset(ServerPlayer player) { VoidAnchorRuntime.reset(player); }
}
