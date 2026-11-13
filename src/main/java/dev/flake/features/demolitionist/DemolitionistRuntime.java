package dev.flake.features.demolitionist;

import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.features.loadout.Loadout.FighterClass;
import dev.flake.combat.CombatModifiers;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** Demolitionist owns its one-mine-per-player lifecycle and Blast Jump mechanics. */
public final class DemolitionistRuntime implements ClassAbilityRuntime {
	public static final int MINE_ARM_TICKS = 20;
	public static final int MINE_LIFE_TICKS = 400;
	public static final double MINE_TRIGGER_RADIUS = 1.7;
	private static boolean installed;
	private final MinecraftServer server;
	private final Map<UUID, Mine> mines = new HashMap<>();

	private record Mine(ServerLevel level, Vec3 pos, int ownerId, int armedAt, int expiresAt) {}

	public DemolitionistRuntime(MinecraftServer server) { this.server = server; }

	public static void install() {
		if (installed) return;
		installed = true;
		ClassAbilityRuntimes.registerRuntime(FighterClass.DEMOLITIONIST, DemolitionistRuntime::new);
		ContentCatalog.registerClass(new RuntimeClassSpec(FighterClass.DEMOLITIONIST,
				"flake.class.demolitionist.role", "flake.class.demolitionist.summary", Ability.PROXIMITY_MINE, Ability.BLAST_JUMP));
	}

	@Override public List<Ability> abilities() { return List.of(Ability.PROXIMITY_MINE, Ability.BLAST_JUMP); }

	@Override public boolean cast(ServerPlayer player, Ability ability, int now) {
		ServerLevel level = (ServerLevel) player.level();
		return switch (ability) {
			case PROXIMITY_MINE -> mine(player, level, now);
			case BLAST_JUMP -> blastJump(player, level);
			default -> false;
		};
	}

	private boolean mine(ServerPlayer player, ServerLevel level, int now) {
		Vec3 pos = player.position();
		mines.put(player.getUUID(), new Mine(level, pos, player.getId(), now + MINE_ARM_TICKS, now + MINE_LIFE_TICKS));
		DemolitionistVisuals.placeMine(player, pos);
		AbilityEffects.sound(level, player, SoundEvents.ANVIL_LAND, 0.4f, 1.6f);
		return true;
	}

	private boolean blastJump(ServerPlayer player, ServerLevel level) {
		Vec3 origin = player.position();
		Vec3 launch = AbilityMovement.flat(player).scale(1.1).add(0, 1.0, 0);
		AbilityMovement.launch(player, launch);
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, true));
		player.fallDistance = 0;
		for (var other : AbilityTargeting.candidates(player, level, origin, 3.5)) {
			if (other.position().distanceToSqr(origin) > 3.5 * 3.5) continue;
			other.hurtServer(level, player.damageSources().explosion(player, player), CombatModifiers.skillDamage(player, other, 3.0f));
			AbilityMovement.launch(other, AbilityMovement.horizontalDir(origin, other.position(), player).scale(1.2).add(0, 0.5, 0));
		}
		DemolitionistVisuals.blastJump(player, origin, launch);
		AbilityEffects.sound(level, player, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.3f);
		return true;
	}

	@Override public void tick(int now) {
		for (var entry : new ArrayList<>(mines.entrySet())) {
			var mine = entry.getValue();
			var owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || now >= mine.expiresAt || DuelRuntime.blocksAbility(entry.getKey())) {
				DemolitionistVisuals.disarmMine(mine.level, owner, mine.ownerId, mine.pos);
				mines.remove(entry.getKey());
				continue;
			}
			if (now < mine.armedAt) continue;
			for (var victim : AbilityTargeting.candidates(owner, mine.level, mine.pos, 2.0)) {
				if (victim.position().distanceToSqr(mine.pos) > MINE_TRIGGER_RADIUS * MINE_TRIGGER_RADIUS) continue;
				victim.hurtServer(mine.level, owner.damageSources().explosion(owner, owner), CombatModifiers.skillDamage(owner, victim, 6.0f));
				AbilityMovement.launch(victim, AbilityMovement.horizontalDir(mine.pos, victim.position(), owner).scale(0.9).add(0, 0.7, 0));
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1, false, true, true));
				DemolitionistVisuals.detonateMine(owner, mine.pos, victim.getId());
				mine.level.playSound(null, mine.pos.x, mine.pos.y, mine.pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
				mines.remove(entry.getKey());
				break;
			}
		}
	}

	@Override public void reset(ServerPlayer player) {
		var removed = mines.remove(player.getUUID());
		if (removed != null) DemolitionistVisuals.disarmMine(removed.level, player, removed.ownerId, removed.pos);
	}

	@Override public void clear() {
		for (var entry : mines.entrySet()) {
			var mine = entry.getValue();
			DemolitionistVisuals.disarmMine(mine.level, server.getPlayerList().getPlayer(entry.getKey()), mine.ownerId, mine.pos);
		}
		mines.clear();
	}
}
