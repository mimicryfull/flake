package dev.flake.features.mirrortrap;

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

/** Vertical slice for «Дзеркальна пастка» (Mirror Trap): 4s projectile reflection shield. */
public final class MirrorTrapDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.MIRROR_TRAP,
			"flake.active.mirror_trap.short",
			MirrorTrapRuntime.COOLDOWN_TICKS,
			MirrorTrapRuntime.BUFF_TICKS,
			false,
			MirrorTrapRuntime::stack,
			"mirror_trap"
	);

	private MirrorTrapDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new MirrorTrapDefinition());
		ContentCatalog.registerStatus(new StatusSpec("mirror_trap", "flake.active.mirror_trap.short", MirrorTrapRuntime::stack));
		CombatModifiers.itemCooldown((player, stack, base) ->
				MirrorTrapRuntime.isItem(stack) ? Cooldowns.ticks(player, base) : base);
		CombatModifiers.projectileTrap(MirrorTrapRuntime::interceptProjectile);
		CombatModifiers.customProjectileTrap(MirrorTrapRuntime::interceptCustomProjectile);
		CombatStatuses.register((player, now, statuses) -> {
			int remaining = MirrorTrapRuntime.remaining(player, now);
			if (remaining > 0) statuses.add(new CombatStatuses.Status("mirror_trap", remaining, MirrorTrapRuntime.BUFF_TICKS, 1));
		});
	}

	public static void registerItems() {
		MirrorTrapRuntime.bind(ItemRegistration.register(MirrorTrapRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), MirrorTrapRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, Entity target) {
						return MirrorTrapRuntime.activate(player);
					}
				}));
	}

	@Override public void register() { MirrorTrapRuntime.register(); }
	@Override public void reset(ServerPlayer player) { MirrorTrapRuntime.reset(player); }
}
