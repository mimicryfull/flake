package dev.flake.features.passives;

import dev.flake.features.loadout.Loadout.PassiveItem;
import dev.flake.combat.PvpVitals;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentSpec.PassiveHandler;
import dev.flake.content.ContentSpec.PassiveSpec;
import dev.flake.content.ContentCatalog;
import dev.flake.features.duel.DuelRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vertical slice for all Passive Items: metadata, editor icons, categories, kit application, and fight-tick logic. */
public final class PassivesDefinition implements Lifecycle {
	/** Per-player schedule for periodic passives; independent of global tick alignment and call rate. */
	private static final dev.flake.runtime.ServerState<dev.flake.features.passives.RegenerationSchedule> REGEN =
			new dev.flake.runtime.ServerState<>(dev.flake.features.passives.RegenerationSchedule::new);
	private static final float REGEN_HEAL = 0.5f;

	private PassivesDefinition() {}

	public static void registerItems() {
		MeltingAmethyst.bind(dev.flake.content.ItemRegistration.register(MeltingAmethyst.ID,
				key -> new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties().setId(key))));
		NewPassivesRuntime.registerItems();
	}

	public static void install() {
		dev.flake.combat.CombatModifiers.meleeHealing((attacker, taken) ->
				DuelRuntime.passiveOf(attacker.getUUID()) == PassiveItem.LEECH ? taken * 0.08f : 0f);
		dev.flake.combat.CombatModifiers.reflectedDamage((victim, attacker, taken) -> {
			if (!DuelRuntime.opponents(victim.getUUID(), attacker.getUUID())
					|| DuelRuntime.passiveOf(victim.getUUID()) != PassiveItem.THORNS) return null;
			return new dev.flake.combat.CombatModifiers.Reflection(victim.damageSources().thorns(victim), Math.max(0.5f, taken * 0.2f));
		});
		dev.flake.combat.CombatModifiers.cooldown(player ->
				DuelRuntime.passiveOf(player.getUUID()) == PassiveItem.MELTING_AMETHYST ? 0.8f : 1f);
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.NONE, "flake.passive.none.short", "flake.category.utility",
				() -> ItemStack.EMPTY, new PassiveHandler() {}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.IRON_AEGIS, "flake.passive.iron_aegis.short", "flake.category.defense",
				() -> new ItemStack(Items.HEART_OF_THE_SEA), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, -1, 0, false, false, true));
				player.setAbsorptionAmount(2.0f);
			}
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.CRIMSON_FANG, "flake.passive.crimson_fang.short", "flake.category.attack",
				() -> new ItemStack(Items.BLAZE_POWDER), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, -1, 0, false, false, true));
			}
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.STONE_PLATE, "flake.passive.stone_plate.short", "flake.category.defense",
				() -> new ItemStack(Items.NETHERITE_SCRAP), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 0, false, false, true));
			}
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.VITALITY, "flake.passive.vitality.short", "flake.category.defense",
				() -> new ItemStack(Items.GLISTERING_MELON_SLICE), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, -1, 0, false, false, true));
			}
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.MOBILITY, "flake.passive.mobility.short", "flake.category.mobility",
				() -> new ItemStack(Items.FEATHER), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, -1, 0, false, false, true));
			}
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.REGENERATION, "flake.passive.regeneration.short", "flake.category.defense",
				() -> new ItemStack(Items.GHAST_TEAR), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) { REGEN.of(player).reset(player.getUUID()); }
			@Override public void onFightTick(ServerPlayer player, int nowTick) {
				if (!player.isAlive() || player.getHealth() >= player.getMaxHealth()) return;
				if (REGEN.of(player).due(player.getUUID(), nowTick)) player.heal(REGEN_HEAL);
			}
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.LEECH, "flake.passive.leech.short", "flake.category.attack",
				() -> new ItemStack(Items.FERMENTED_SPIDER_EYE), new PassiveHandler() {}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.THORNS, "flake.passive.thorns.short", "flake.category.defense",
				() -> new ItemStack(Items.CACTUS), new PassiveHandler() {}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.FURY, "flake.passive.fury.short", "flake.category.attack",
				() -> new ItemStack(Items.CLOCK), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) {
				PvpVitals.fury(player, true);
			}
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.MELTING_AMETHYST, "flake.passive.melting_amethyst.short", "flake.category.utility",
				() -> MeltingAmethyst.stack(), new PassiveHandler() {}));
		NewPassivesRuntime.install();
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.BLOODY_SEAL, "flake.passive.bloody_seal.short", "flake.category.attack",
				NewPassivesRuntime::bloodyHandStack, new PassiveHandler() {}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.MIRROR_SCALE, "flake.passive.mirror_scale.short", "flake.category.defense",
				NewPassivesRuntime::mirrorScaleStack, new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) { NewPassivesRuntime.resetMirrorScale(player); }
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.HUNTER_COMPASS, "flake.passive.hunter_compass.short", "flake.category.utility",
				() -> new ItemStack(Items.RECOVERY_COMPASS), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) { NewPassivesRuntime.resetHunterCompass(player); }
			@Override public void onFightTick(ServerPlayer player, int nowTick) { NewPassivesRuntime.tickHunterCompass(player, nowTick); }
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.STORM_CHARGE, "flake.passive.storm_charge.short", "flake.category.attack",
				NewPassivesRuntime::stormBoltStack, new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) { NewPassivesRuntime.resetStormCharge(player); }
		}));
		ContentCatalog.registerPassive(new PassiveSpec(PassiveItem.SILENT_STEP, "flake.passive.silent_step.short", "flake.category.mobility",
				() -> new ItemStack(Items.PHANTOM_MEMBRANE), new PassiveHandler() {
			@Override public void onApplyKit(ServerPlayer player) { NewPassivesRuntime.initSilentStep(player); }
			@Override public void onFightTick(ServerPlayer player, int nowTick) { NewPassivesRuntime.tickSilentStep(player, nowTick); }
		}));
		ContentCatalog.registerLifecycle(new PassivesDefinition());
	}

	@Override public void register() { NewPassivesRuntime.register(); }
	@Override public void reset(ServerPlayer player) {
		PvpVitals.remove(player);
		REGEN.of(player).reset(player.getUUID());
		NewPassivesRuntime.reset(player);
	}
}
