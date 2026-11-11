package dev.flake.features.vampire;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Vampire owns Blood Lunge, Bloodlust, its healing contribution and confirmed siphon presentation. */
public final class VampireRuntime implements ClassAbilityRuntime {
	public static final float BLOODLUST_STEAL = 0.18f;
	public static final int BLOODLUST_TICKS = 100;
	private static boolean installed;
	private final MinecraftServer server;
	private final Map<UUID, Integer> bloodlustUntil = new HashMap<>();

	public VampireRuntime(MinecraftServer server) { this.server = server; }

	public static void install() {
		if (installed) return;
		installed = true;
		ContentCatalog.registerStatus(new dev.flake.content.ContentSpec.StatusSpec("bloodlust", "flake.status.bloodlust", () -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE)));
		ClassAbilityRuntimes.registerRuntime(FighterClass.VAMPIRE, VampireRuntime::new);
		ContentCatalog.registerClass(new RuntimeClassSpec(FighterClass.VAMPIRE,
				"flake.class.vampire.role", "flake.class.vampire.summary", Ability.BLOOD_LUNGE, Ability.BLOODLUST));
		CombatStatuses.register((player, now, statuses) -> {
			var state = ClassAbilityRuntimes.find(player.level().getServer());
			int remaining = state == null ? 0 : state.buffRemaining(player.getUUID(), Ability.BLOODLUST, now);
			if (remaining > 0) statuses.add(new CombatStatuses.Status("bloodlust", remaining, BLOODLUST_TICKS, 1));
		});
	}

	@Override public List<Ability> abilities() { return List.of(Ability.BLOOD_LUNGE, Ability.BLOODLUST); }

	@Override public boolean cast(ServerPlayer player, Ability ability, int now) {
		ServerLevel level = (ServerLevel) player.level();
		return switch (ability) {
			case BLOOD_LUNGE -> bloodLunge(player, level);
			case BLOODLUST -> bloodlust(player, level, now);
			default -> false;
		};
	}

	private boolean bloodLunge(ServerPlayer player, ServerLevel level) {
		var target = AbilityTargeting.target(player, 9.0, 0.82, true);
		if (target == null) return false;
		Vec3 dir = AbilityMovement.horizontalDir(player.position(), target.position(), player);
		AbilityMovement.launch(player, dir.scale(1.7).add(0, 0.22, 0));
		boolean hit = target.hurtServer(level, player.damageSources().playerAttack(player), CombatModifiers.skillDamage(player, target, 4.0f));
		player.heal(1.5f); // Independent of damage acceptance; not part of the combined melee lifesteal heal.
		dev.flake.features.vampire.VampireVisuals.bloodLunge(player, target, dir, hit);
		AbilityEffects.sound(level, player, SoundEvents.MACE_SMASH_AIR, 0.65f, 1.35f);
		if (hit) {
			AbilityEffects.sound(level, player, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 0.78f);
			AbilityEffects.sound(level, player, SoundEvents.PHANTOM_BITE, 0.7f, 1.18f);
		}
		return true;
	}

	private boolean bloodlust(ServerPlayer player, ServerLevel level, int now) {
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, BLOODLUST_TICKS, 0, false, false, true));
		bloodlustUntil.put(player.getUUID(), now + BLOODLUST_TICKS);
		dev.flake.features.vampire.VampireVisuals.bloodlust(player, true);
		AbilityEffects.sound(level, player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.32f, 1.75f);
		AbilityEffects.sound(level, player, SoundEvents.WARDEN_HEARTBEAT, 0.85f, 1.1f);
		return true;
	}

	@Override public int buffRemaining(UUID player, Ability ability, int now) {
		if (ability != Ability.BLOODLUST) return 0;
		Integer until = bloodlustUntil.get(player);
		return until == null ? 0 : Math.max(0, until - now);
	}

	@Override public int activeElapsed(UUID player, int slot, int now) {
		int remaining = buffRemaining(player, Ability.BLOODLUST, now);
		return slot != 2 || remaining <= 0 ? -1 : Math.clamp(BLOODLUST_TICKS - remaining, 0, BLOODLUST_TICKS);
	}

	@Override public float meleeHealing(ServerPlayer attacker, float taken) {
		// Membership, rather than a timer check, preserves the pre-expiry-tick healing boundary.
		return bloodlustUntil.containsKey(attacker.getUUID()) ? taken * BLOODLUST_STEAL : 0;
	}

	@Override public boolean afterMeleeHeal(ServerPlayer attacker, LivingEntity victim, float taken, float before, int now) {
		boolean bloodlust = buffRemaining(attacker.getUUID(), Ability.BLOODLUST, now) > 0;
		if (GhoulAnimation.confirmedHealing(bloodlust, taken, before, attacker.getHealth()))
			dev.flake.features.vampire.VampireVisuals.bloodSiphon(attacker, victim, attacker.getHealth() - before);
		return bloodlust;
	}

	@Override public void cancelBuffs(ServerPlayer player, int now) {
		int remaining = buffRemaining(player.getUUID(), Ability.BLOODLUST, now);
		if (bloodlustUntil.remove(player.getUUID()) == null) return;
		dev.flake.features.vampire.VampireVisuals.bloodlust(player, false);
		AbilityEffects.removeOwnEffect(player, MobEffects.SPEED, 0, remaining);
	}

	@Override public void tick(int now) {
		bloodlustUntil.entrySet().removeIf(entry -> {
			boolean end = entry.getValue() <= now || DuelRuntime.blocksAbility(entry.getKey());
			var player = end ? server.getPlayerList().getPlayer(entry.getKey()) : null;
			if (player != null) dev.flake.features.vampire.VampireVisuals.bloodlust(player, false);
			return end;
		});
	}

	@Override public void reset(ServerPlayer player) {
		if (bloodlustUntil.remove(player.getUUID()) != null) dev.flake.features.vampire.VampireVisuals.bloodlust(player, false);
	}

	@Override public void clear() { bloodlustUntil.clear(); }
}
