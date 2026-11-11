package dev.flake.combat.ability;

import dev.flake.combat.ability.AbilitySlots.Ability;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** One fighter class's mechanics and mutable state, owned by one MinecraftServer. */
public interface ClassAbilityRuntime {
	List<Ability> abilities();
	boolean cast(ServerPlayer player, Ability ability, int now);
	default void tick(int now) {}
	default void reset(ServerPlayer player) {}
	default void cancelBuffs(ServerPlayer player, int now) {}
	default void clear() {}
	default int activeElapsed(UUID player, int slot, int now) { return -1; }
	default int buffRemaining(UUID player, Ability ability, int now) { return 0; }

	/** Confirmed, unblocked damage only; reflected damage deliberately does not re-enter this hook. */
	default void afterDamage(LivingEntity victim, DamageSource source, int now) {}
	/** Contribution to the one combined class + passive melee heal. */
	default float meleeHealing(ServerPlayer attacker, float taken) { return 0; }
	/** Returns true if this class owns healing presentation, even when health was already full. */
	default boolean afterMeleeHeal(ServerPlayer attacker, LivingEntity victim, float taken, float before, int now) { return false; }
}
