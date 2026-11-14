package dev.flake.features.kolobok;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.CombatStatuses;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.StatusSpec;
import dev.flake.content.ItemRegistration;
import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/** Vertical slice for «Колобок-стрілець»: smooth non-voxel Dual-AK kolobok ranged gunner artifact. */
public final class KolobokDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.KOLOBOK_GUNNER,
			"flake.active.kolobok.short",
			KolobokRuntime.COOLDOWN_TICKS,
			KolobokRuntime.DURATION_TICKS,
			false,
			KolobokRuntime::stack,
			"kolobok"
	);

	private KolobokDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new KolobokDefinition());
		ContentCatalog.registerStatus(new StatusSpec("kolobok", "flake.active.kolobok.short", KolobokRuntime::stack));
		CombatModifiers.itemCooldown((player, stack, base) ->
				KolobokRuntime.isItem(stack) ? Cooldowns.ticks(player, base) : base);
		CombatStatuses.register((player, now, statuses) -> {
			int remaining = KolobokRuntime.remaining(player, now);
			if (remaining > 0) statuses.add(new CombatStatuses.Status("kolobok", remaining, KolobokRuntime.DURATION_TICKS, 1));
		});
	}

	public static void registerItems() {
		KolobokRuntime.bind(ItemRegistration.register(KolobokRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), KolobokRuntime.ID) {
					@Override
					protected boolean activateServer(ServerPlayer player, Entity directTarget) {
						return KolobokRuntime.activate(player);
					}
				}));
	}

	@Override public void register() { KolobokRuntime.register(); }
	@Override public void reset(ServerPlayer player) { KolobokRuntime.reset(player); }
}
