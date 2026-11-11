package dev.flake.features.bastion;

import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.features.loadout.Loadout.FighterClass;
import dev.flake.combat.CombatModifiers;
import dev.flake.combat.CombatStatuses;
import dev.flake.combat.ability.AbilityEffects;
import dev.flake.combat.ability.AbilityMovement;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.combat.ability.ClassAbilityRuntime;
import dev.flake.combat.ability.ClassAbilityRuntimes;
import dev.flake.combat.ability.RuntimeClassSpec;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.content.ContentCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Bastion owns Shield Ram, Bulwark, its status projection and confirmed shield-hit feedback. */
public final class BastionRuntime implements ClassAbilityRuntime {
	public static final int BULWARK_TICKS = 80;
	private static final Identifier KB_MOD = Identifier.fromNamespaceAndPath("flake", "bulwark_kb");
	private static boolean installed;
	private final MinecraftServer server;
	private final Map<UUID, Integer> bulwarkUntil = new HashMap<>();

	public BastionRuntime(MinecraftServer server) { this.server = server; }

	public static void install() {
		if (installed) return;
		installed = true;
		ContentCatalog.registerStatus(new dev.flake.content.ContentSpec.StatusSpec("bulwark", "flake.status.bulwark", () -> new net.minecraft.world.item.ItemStack(Items.SHIELD)));
		ClassAbilityRuntimes.registerRuntime(FighterClass.BASTION, BastionRuntime::new);
		ContentCatalog.registerClass(new RuntimeClassSpec(FighterClass.BASTION,
				"flake.class.bastion.role", "flake.class.bastion.summary", Ability.SHIELD_RAM, Ability.BULWARK));
		CombatStatuses.register((player, now, statuses) -> {
			var state = ClassAbilityRuntimes.find(player.level().getServer());
			int remaining = state == null ? 0 : state.buffRemaining(player.getUUID(), Ability.BULWARK, now);
			if (remaining > 0) statuses.add(new CombatStatuses.Status("bulwark", remaining, BULWARK_TICKS, 1));
		});
	}

	@Override public List<Ability> abilities() { return List.of(Ability.SHIELD_RAM, Ability.BULWARK); }

	@Override public boolean cast(ServerPlayer player, Ability ability, int now) {
		ServerLevel level = (ServerLevel) player.level();
		return switch (ability) {
			case SHIELD_RAM -> shieldRam(player, level);
			case BULWARK -> bulwark(player, level, now);
			default -> false;
		};
	}

	private boolean shieldRam(ServerPlayer player, ServerLevel level) {
		Vec3 dir = AbilityMovement.flat(player);
		boolean shield = player.getOffhandItem().is(Items.SHIELD);
		AbilityMovement.launch(player, dir.scale(1.15).add(0, 0.18, 0));
		var target = AbilityTargeting.target(player, 4.5, 0.65, false);
		AbilityEffects.sound(level, player, shield ? SoundEvents.MACE_SMASH_GROUND : SoundEvents.ANVIL_LAND,
				shield ? 0.9f : 0.6f, shield ? 0.72f : 0.8f);
		LivingEntity confirmed = null;
		if (target != null) {
			float damage = CombatModifiers.skillDamage(player, target, shield ? 6.0f : 4.0f);
			if (target.hurtServer(level, player.damageSources().playerAttack(player), damage)) confirmed = target;
			// Ram suppression and movement historically apply even when damage is rejected.
			AbilityMovement.launch(target, dir.scale(1.2).add(0, 0.35, 0));
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 2, false, false, true));
		}
		dev.flake.features.bastion.BastionVisuals.shieldRam(player, confirmed, dir, shield);
		return true;
	}

	private boolean bulwark(ServerPlayer player, ServerLevel level, int now) {
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, BULWARK_TICKS, 1, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BULWARK_TICKS, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BULWARK_TICKS, 0, false, false, true));
		player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 4.0f));
		var kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) {
			kb.removeModifier(KB_MOD);
			kb.addTransientModifier(new AttributeModifier(KB_MOD, 1.0, AttributeModifier.Operation.ADD_VALUE));
		}
		bulwarkUntil.put(player.getUUID(), now + BULWARK_TICKS);
		dev.flake.features.bastion.BastionVisuals.bulwark(player, true);
		AbilityEffects.sound(level, player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35f, 0.6f);
		return true;
	}

	@Override public int buffRemaining(UUID player, Ability ability, int now) {
		if (ability != Ability.BULWARK) return 0;
		Integer until = bulwarkUntil.get(player);
		return until == null ? 0 : Math.max(0, until - now);
	}

	@Override public int activeElapsed(UUID player, int slot, int now) {
		int remaining = buffRemaining(player, Ability.BULWARK, now);
		return slot != 2 || remaining <= 0 ? -1 : Math.clamp(BULWARK_TICKS - remaining, 0, BULWARK_TICKS);
	}

	@Override public void afterDamage(LivingEntity victim, DamageSource source, int now) {
		if (victim instanceof ServerPlayer hit && buffRemaining(hit.getUUID(), Ability.BULWARK, now) > 0)
			dev.flake.features.bastion.BastionVisuals.bulwarkHit(hit, source.getSourcePosition());
	}

	@Override public void cancelBuffs(ServerPlayer player, int now) {
		int remaining = buffRemaining(player.getUUID(), Ability.BULWARK, now);
		if (bulwarkUntil.remove(player.getUUID()) == null) return;
		endBulwark(player);
		AbilityEffects.removeOwnEffect(player, MobEffects.RESISTANCE, 1, remaining);
		AbilityEffects.removeOwnEffect(player, MobEffects.SLOWNESS, 0, remaining);
		boolean ownAbsorption = AbilityEffects.removeOwnEffect(player, MobEffects.ABSORPTION, 0, remaining);
		if (ownAbsorption && player.getAbsorptionAmount() <= 4.01f) player.setAbsorptionAmount(0f);
	}

	@Override public void tick(int now) {
		for (var entry : new ArrayList<>(bulwarkUntil.entrySet())) {
			var player = server.getPlayerList().getPlayer(entry.getKey());
			if (entry.getValue() > now && player != null && player.isAlive() && !DuelRuntime.blocksAbility(entry.getKey())) continue;
			bulwarkUntil.remove(entry.getKey());
			if (player != null) endBulwark(player);
		}
	}

	private void endBulwark(ServerPlayer player) {
		dev.flake.features.bastion.BastionVisuals.bulwark(player, false);
		removeKnockbackModifier(player);
	}

	private void removeKnockbackModifier(ServerPlayer player) {
		var kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) kb.removeModifier(KB_MOD);
	}

	/** Reset retains the old contract: duel/kit restoration owns vanilla potion effect cleanup. */
	@Override public void reset(ServerPlayer player) {
		if (bulwarkUntil.remove(player.getUUID()) != null) dev.flake.features.bastion.BastionVisuals.bulwark(player, false);
		removeKnockbackModifier(player);
	}

	@Override public void clear() { bulwarkUntil.clear(); }
}
