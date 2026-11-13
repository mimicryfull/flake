package dev.flake.features.huntermark;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/** Vertical slice for «Мітка мисливця» (Hunter's Mark): 6s mark (+15% dmg, blocks invisibility & smoke blindness). */
public final class HunterMarkDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.HUNTER_MARK,
			"flake.active.hunter_mark.short",
			HunterMarkRuntime.COOLDOWN_TICKS,
			HunterMarkRuntime.MARK_TICKS,
			false,
			HunterMarkRuntime::stack,
			"hunter_mark"
	);

	private HunterMarkDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new HunterMarkDefinition());
		ContentCatalog.registerStatus(new StatusSpec("hunter_mark", "flake.active.hunter_mark.short", HunterMarkRuntime::stack));
		CombatModifiers.itemCooldown((player, stack, base) ->
				HunterMarkRuntime.isItem(stack) ? Cooldowns.ticks(player, base) : base);
		CombatModifiers.damageScale(HunterMarkRuntime::scaleDamage);
		CombatModifiers.effectGate(HunterMarkRuntime::allowEffect);
		CombatStatuses.register((player, now, statuses) -> {
			int remaining = HunterMarkRuntime.remaining(player, now);
			if (remaining > 0) statuses.add(new CombatStatuses.Status("hunter_mark", remaining, HunterMarkRuntime.MARK_TICKS, 1));
		});
	}

	public static void registerItems() {
		HunterMarkRuntime.bind(ItemRegistration.register(HunterMarkRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), HunterMarkRuntime.ID) {
					@Override protected boolean canConsumeOnClient(Player player) {
						return HunterMarkRuntime.hasClientTarget(player);
					}
					@Override protected boolean activateServer(ServerPlayer player, Entity target) {
						return HunterMarkRuntime.activate(player, target);
					}
				}));
	}

	@Override public void register() { HunterMarkRuntime.register(); }
	@Override public void reset(ServerPlayer player) { HunterMarkRuntime.reset(player); }
}
