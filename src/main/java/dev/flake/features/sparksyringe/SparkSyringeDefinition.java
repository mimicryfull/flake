package dev.flake.features.sparksyringe;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.CombatStatuses;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.Stuns;
import dev.flake.combat.item.ActiveCombatItem;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.StatusSpec;
import dev.flake.content.ItemRegistration;
import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/** Vertical slice for «Іскровий шприц» (Spark Syringe): cleanse harmful effects + 3s CC/debuff immunity. */
public final class SparkSyringeDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.SPARK_SYRINGE,
			"flake.active.spark_syringe.short",
			SparkSyringeRuntime.COOLDOWN_TICKS,
			SparkSyringeRuntime.BUFF_TICKS,
			false,
			SparkSyringeRuntime::stack,
			"spark_syringe"
	);

	private SparkSyringeDefinition() {}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new SparkSyringeDefinition());
		ContentCatalog.registerStatus(new StatusSpec("spark_syringe", "flake.active.spark_syringe.short", SparkSyringeRuntime::stack));
		CombatModifiers.itemCooldown((player, stack, base) ->
				SparkSyringeRuntime.isItem(stack) ? Cooldowns.ticks(player, base) : base);
		CombatModifiers.effectGate((entity, effect) ->
				!(entity instanceof ServerPlayer sp && SparkSyringeRuntime.isImmune(sp)
						&& effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL));
		Stuns.registerImmunity(entity -> entity instanceof ServerPlayer sp && SparkSyringeRuntime.isImmune(sp));
		CombatStatuses.register((player, now, statuses) -> {
			int remaining = SparkSyringeRuntime.remaining(player, now);
			if (remaining > 0) statuses.add(new CombatStatuses.Status("spark_syringe", remaining, SparkSyringeRuntime.BUFF_TICKS, 1));
		});
	}

	public static void registerItems() {
		SparkSyringeRuntime.bind(ItemRegistration.register(SparkSyringeRuntime.ID, key ->
				new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), SparkSyringeRuntime.ID) {
					@Override protected boolean activateServer(ServerPlayer player, Entity target) {
						return SparkSyringeRuntime.activate(player);
					}
				}));
	}

	@Override public void register() { SparkSyringeRuntime.register(); }
	@Override public void reset(ServerPlayer player) { SparkSyringeRuntime.reset(player); }
}
