package dev.flake.features.werewolf;

import dev.flake.Flake;
import dev.flake.combat.CombatModifiers;
import dev.flake.combat.CombatStatuses;
import dev.flake.combat.ability.AbilityEffects;
import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.combat.ability.ClassAbilityRuntime;
import dev.flake.combat.ability.ClassAbilityRuntimes;
import dev.flake.combat.ability.RuntimeClassSpec;
import dev.flake.content.ContentCatalog;
import dev.flake.content.ContentSpec;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.Loadout.FighterClass;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Werewolf class runtime:
 * 1) Claws and Paws (5s): +8% damage and +8% movement speed.
 * 2) Nature's Call (5s): 1.5x model scale, status resistance / Resistance III, and +5 max HP + heal.
 */
public final class WerewolfRuntime implements ClassAbilityRuntime {
	public static final int CLAWS_TICKS = 100;
	public static final double CLAWS_DAMAGE_BONUS = 0.08;
	public static final double CLAWS_SPEED_BONUS = 0.08;

	public static final int NATURES_CALL_TICKS = 100;
	public static final double NATURES_SCALE_BONUS = 0.50;
	public static final double NATURES_HEALTH_BONUS = 5.0;
	public static final int NATURES_RESIST_AMPLIFIER = 2;

	private static final Identifier CLAWS_DMG_MOD = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "werewolf_claws_dmg");
	private static final Identifier CLAWS_SPD_MOD = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "werewolf_claws_spd");
	private static final Identifier NATURES_SCALE_MOD = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "werewolf_natures_scale");
	private static final Identifier NATURES_HP_MOD = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "werewolf_natures_hp");

	private static final Map<MinecraftServer, WerewolfRuntime> INSTANCES = new IdentityHashMap<>();
	private static boolean installed;

	private final MinecraftServer server;
	private final Map<UUID, Integer> clawsUntil = new HashMap<>();
	private final Map<UUID, Integer> naturesUntil = new HashMap<>();

	public WerewolfRuntime(MinecraftServer server) {
		this.server = server;
		INSTANCES.put(server, this);
	}

	public static void install() {
		if (installed) return;
		installed = true;
		ContentCatalog.registerStatus(new ContentSpec.StatusSpec(
				"claws_and_paws", "flake.status.claws_and_paws", () -> new ItemStack(Items.BONE)));
		ContentCatalog.registerStatus(new ContentSpec.StatusSpec(
				"natures_call", "flake.status.natures_call", () -> new ItemStack(Items.TURTLE_SCUTE)));

		ClassAbilityRuntimes.registerRuntime(FighterClass.WEREWOLF, WerewolfRuntime::new);
		ContentCatalog.registerClass(new RuntimeClassSpec(
				FighterClass.WEREWOLF,
				"flake.class.werewolf.role",
				"flake.class.werewolf.summary",
				Ability.CLAWS_AND_PAWS,
				Ability.NATURES_CALL));

		CombatModifiers.outgoingSkillDamage((attacker, target, damage) -> {
			WerewolfRuntime rt = INSTANCES.get(attacker.level().getServer());
			if (rt != null && rt.buffRemaining(attacker.getUUID(), Ability.CLAWS_AND_PAWS, attacker.level().getServer().getTickCount()) > 0) {
				return damage * (float) (1.0 + CLAWS_DAMAGE_BONUS);
			}
			return damage;
		});

		CombatModifiers.effectGate((entity, effect) -> {
			if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return true;
			MinecraftServer srv = level.getServer();
			WerewolfRuntime rt = INSTANCES.get(srv);
			if (rt != null && rt.buffRemaining(player.getUUID(), Ability.NATURES_CALL, srv.getTickCount()) > 0) {
				return effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL;
			}
			return true;
		});

		CombatStatuses.register((player, now, statuses) -> {
			var state = ClassAbilityRuntimes.find(player.level().getServer());
			if (state == null) return;
			int clawsRem = state.buffRemaining(player.getUUID(), Ability.CLAWS_AND_PAWS, now);
			if (clawsRem > 0) statuses.add(new CombatStatuses.Status("claws_and_paws", clawsRem, CLAWS_TICKS, 0));
			int callRem = state.buffRemaining(player.getUUID(), Ability.NATURES_CALL, now);
			if (callRem > 0) statuses.add(new CombatStatuses.Status("natures_call", callRem, NATURES_CALL_TICKS, 1));
		});
	}

	@Override public List<Ability> abilities() {
		return List.of(Ability.CLAWS_AND_PAWS, Ability.NATURES_CALL);
	}

	@Override public boolean cast(ServerPlayer player, Ability ability, int now) {
		ServerLevel level = (ServerLevel) player.level();
		return switch (ability) {
			case CLAWS_AND_PAWS -> activateClaws(player, level, now);
			case NATURES_CALL -> activateNaturesCall(player, level, now);
			default -> false;
		};
	}

	private boolean activateClaws(ServerPlayer player, ServerLevel level, int now) {
		clawsUntil.put(player.getUUID(), now + CLAWS_TICKS);
		setModifier(player, Attributes.ATTACK_DAMAGE, CLAWS_DMG_MOD, CLAWS_DAMAGE_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		setModifier(player, Attributes.MOVEMENT_SPEED, CLAWS_SPD_MOD, CLAWS_SPEED_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

		WerewolfVisuals.clawsOn(player);
		AbilityEffects.sound(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.85f, 1.35f);
		AbilityEffects.sound(level, player, SoundEvents.WOLF_SHAKE, 0.90f, 0.88f);
		return true;
	}

	private boolean activateNaturesCall(ServerPlayer player, ServerLevel level, int now) {
		boolean alreadyActive = naturesUntil.containsKey(player.getUUID());
		naturesUntil.put(player.getUUID(), now + NATURES_CALL_TICKS);

		setModifier(player, Attributes.SCALE, NATURES_SCALE_MOD, NATURES_SCALE_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(player, Attributes.MAX_HEALTH, NATURES_HP_MOD, NATURES_HEALTH_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		if (!alreadyActive) {
			player.heal(CombatModifiers.scaleHealing(player, (float) NATURES_HEALTH_BONUS));
		}

		// Cleanse active harmful effects on cast + apply Turtle-Master-grade damage resistance
		player.getActiveEffects().stream()
				.filter(inst -> inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
				.map(MobEffectInstance::getEffect)
				.toList()
				.forEach(player::removeEffect);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, NATURES_CALL_TICKS, NATURES_RESIST_AMPLIFIER, false, false, true));

		WerewolfVisuals.naturesCallOn(player);
		AbilityEffects.sound(level, player, SoundEvents.RAVAGER_ROAR, 0.72f, 1.18f);
		AbilityEffects.sound(level, player, SoundEvents.BEACON_ACTIVATE, 0.55f, 0.75f);
		return true;
	}

	@Override public int buffRemaining(UUID player, Ability ability, int now) {
		Integer until = switch (ability) {
			case CLAWS_AND_PAWS -> clawsUntil.get(player);
			case NATURES_CALL -> naturesUntil.get(player);
			default -> null;
		};
		return until == null ? 0 : Math.max(0, until - now);
	}

	@Override public int activeElapsed(UUID player, int slot, int now) {
		if (slot == 1) {
			int rem = buffRemaining(player, Ability.CLAWS_AND_PAWS, now);
			return rem <= 0 ? -1 : Math.clamp(CLAWS_TICKS - rem, 0, CLAWS_TICKS);
		}
		if (slot == 2) {
			int rem = buffRemaining(player, Ability.NATURES_CALL, now);
			return rem <= 0 ? -1 : Math.clamp(NATURES_CALL_TICKS - rem, 0, NATURES_CALL_TICKS);
		}
		return -1;
	}

	@Override public void cancelBuffs(ServerPlayer player, int now) {
		clearClaws(player);
		clearNaturesCall(player, now);
	}

	@Override public void tick(int now) {
		clawsUntil.entrySet().removeIf(entry -> {
			boolean end = entry.getValue() <= now || DuelRuntime.blocksAbility(entry.getKey());
			if (end) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					removeModifier(player, Attributes.ATTACK_DAMAGE, CLAWS_DMG_MOD);
					removeModifier(player, Attributes.MOVEMENT_SPEED, CLAWS_SPD_MOD);
					WerewolfVisuals.clawsOff(player);
				}
			}
			return end;
		});

		naturesUntil.entrySet().removeIf(entry -> {
			boolean end = entry.getValue() <= now || DuelRuntime.blocksAbility(entry.getKey());
			if (end) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					removeModifier(player, Attributes.SCALE, NATURES_SCALE_MOD);
					removeModifier(player, Attributes.MAX_HEALTH, NATURES_HP_MOD);
					clampHealth(player);
					WerewolfVisuals.naturesCallOff(player);
				}
			}
			return end;
		});
	}

	@Override public void reset(ServerPlayer player) {
		clearClaws(player);
		clearNaturesCall(player, server.getTickCount());
	}

	@Override public void clear() {
		for (UUID id : List.copyOf(clawsUntil.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				removeModifier(player, Attributes.ATTACK_DAMAGE, CLAWS_DMG_MOD);
				removeModifier(player, Attributes.MOVEMENT_SPEED, CLAWS_SPD_MOD);
			}
		}
		for (UUID id : List.copyOf(naturesUntil.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				removeModifier(player, Attributes.SCALE, NATURES_SCALE_MOD);
				removeModifier(player, Attributes.MAX_HEALTH, NATURES_HP_MOD);
				clampHealth(player);
			}
		}
		clawsUntil.clear();
		naturesUntil.clear();
		INSTANCES.remove(server);
	}

	private void clearClaws(ServerPlayer player) {
		if (clawsUntil.remove(player.getUUID()) == null) return;
		removeModifier(player, Attributes.ATTACK_DAMAGE, CLAWS_DMG_MOD);
		removeModifier(player, Attributes.MOVEMENT_SPEED, CLAWS_SPD_MOD);
		WerewolfVisuals.clawsOff(player);
	}

	private void clearNaturesCall(ServerPlayer player, int now) {
		Integer until = naturesUntil.remove(player.getUUID());
		if (until == null) return;
		removeModifier(player, Attributes.SCALE, NATURES_SCALE_MOD);
		removeModifier(player, Attributes.MAX_HEALTH, NATURES_HP_MOD);
		clampHealth(player);
		AbilityEffects.removeOwnEffect(player, MobEffects.RESISTANCE, NATURES_RESIST_AMPLIFIER, Math.max(0, until - now));
		WerewolfVisuals.naturesCallOff(player);
	}

	private static void clampHealth(ServerPlayer player) {
		float max = player.getMaxHealth();
		if (player.getHealth() > max) player.setHealth(max);
	}

	private static void setModifier(ServerPlayer player, Holder<Attribute> attr, Identifier id,
			double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = player.getAttribute(attr);
		if (inst == null) return;
		inst.removeModifier(id);
		inst.addTransientModifier(new AttributeModifier(id, amount, op));
	}

	private static void removeModifier(ServerPlayer player, Holder<Attribute> attr, Identifier id) {
		AttributeInstance inst = player.getAttribute(attr);
		if (inst != null) inst.removeModifier(id);
	}
}
