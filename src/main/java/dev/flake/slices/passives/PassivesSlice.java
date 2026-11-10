package dev.flake.slices.passives;

import dev.flake.build.Loadout.PassiveItem;
import dev.flake.duel.MeltingAmethyst;
import dev.flake.duel.PvpVitals;
import dev.flake.shared.CombatSlice.Lifecycle;
import dev.flake.shared.CombatSlice.PassiveHandler;
import dev.flake.shared.CombatSlice.PassiveSpec;
import dev.flake.shared.SliceRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vertical slice for all Passive Items: metadata, editor icons, categories, kit application, and fight-tick logic. */
public final class PassivesSlice implements Lifecycle {
	/** Per-player schedule for periodic passives; independent of global tick alignment and call rate. */
	private static final java.util.Map<java.util.UUID, Integer> LAST_REGEN = new java.util.HashMap<>();
	private static final int REGEN_INTERVAL_TICKS = 60;
	private static final float REGEN_HEAL = 0.5f;

	private PassivesSlice() {}

	/**
	 * Called every fight tick for every participant. Must be idempotent within a tick and must not
	 * depend on how often it is invoked: the interval is tracked per player instead of using
	 * {@code nowTick % N}, which drifts with match start and breaks with several concurrent matches.
	 */
	static boolean regenerationDue(java.util.UUID id, int nowTick) {
		Integer last = LAST_REGEN.get(id);
		if (last != null && nowTick - last < REGEN_INTERVAL_TICKS) return false;
		LAST_REGEN.put(id, nowTick);
		return true;
	}

	/** Drops per-player schedules so a new match starts from a clean interval. */
	public static void resetSchedule(java.util.UUID id) { LAST_REGEN.remove(id); }

	public static void install() {
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.NONE, "flake.passive.none.short", "flake.category.utility",
				() -> ItemStack.EMPTY, new PassiveHandler() {}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.IRON_AEGIS, "flake.passive.iron_aegis.short", "flake.category.defense",
				() -> new ItemStack(Items.HEART_OF_THE_SEA), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, -1, 0, false, false, true));
				player.setAbsorptionAmount(2.0f);
			}
		}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.CRIMSON_FANG, "flake.passive.crimson_fang.short", "flake.category.attack",
				() -> new ItemStack(Items.BLAZE_POWDER), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, -1, 0, false, false, true));
			}
		}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.STONE_PLATE, "flake.passive.stone_plate.short", "flake.category.defense",
				() -> new ItemStack(Items.NETHERITE_SCRAP), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 0, false, false, true));
			}
		}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.VITALITY, "flake.passive.vitality.short", "flake.category.defense",
				() -> new ItemStack(Items.GLISTERING_MELON_SLICE), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, -1, 0, false, false, true));
			}
		}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.MOBILITY, "flake.passive.mobility.short", "flake.category.mobility",
				() -> new ItemStack(Items.FEATHER), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, -1, 0, false, false, true));
			}
		}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.REGENERATION, "flake.passive.regeneration.short", "flake.category.defense",
				() -> new ItemStack(Items.GHAST_TEAR), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) { resetSchedule(player.getUUID()); }
			@Override public void onFightTick(ServerPlayer player, int nowTick) {
				if (!player.isAlive() || player.getHealth() >= player.getMaxHealth()) return;
				if (regenerationDue(player.getUUID(), nowTick)) player.heal(REGEN_HEAL);
			}
		}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.LEECH, "flake.passive.leech.short", "flake.category.attack",
				() -> new ItemStack(Items.FERMENTED_SPIDER_EYE), new PassiveHandler() {}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.THORNS, "flake.passive.thorns.short", "flake.category.defense",
				() -> new ItemStack(Items.CACTUS), new PassiveHandler() {}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.FURY, "flake.passive.fury.short", "flake.category.attack",
				() -> new ItemStack(Items.CLOCK), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				PvpVitals.fury(player, true);
			}
		}));
		SliceRegistry.registerPassive(new PassiveSpec(PassiveItem.MELTING_AMETHYST, "flake.passive.melting_amethyst.short", "flake.category.utility",
				() -> MeltingAmethyst.stack(), new PassiveHandler() {}));
		SliceRegistry.registerLifecycle(new PassivesSlice());
	}

	@Override public void register() { MeltingAmethyst.register(); }
	@Override public void reset(ServerPlayer player) { PvpVitals.remove(player); resetSchedule(player.getUUID()); }
}
