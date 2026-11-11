package dev.flake.combat.ability;

import dev.flake.combat.CombatModifiers;
import dev.flake.features.duel.DuelRuntime;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Server-scoped confirmed damage support: one combined lifesteal heal and guarded passive reflection. */
public final class ConfirmedDamageHooks {

	private final ClassAbilityRuntimes abilities;
	private boolean reflecting;

	ConfirmedDamageHooks(ClassAbilityRuntimes abilities) { this.abilities = abilities; }

	void afterDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
		if (blocked || taken <= 0 || reflecting) return;
		int now = victim.level().getServer().getTickCount();
		for (var runtime : abilities.runtimes()) runtime.afterDamage(victim, source, now);
		if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
				&& !DuelRuntime.blocksAbility(attacker.getUUID())) {
			float heal = 0;
			for (var runtime : abilities.runtimes()) heal += runtime.meleeHealing(attacker, taken);
			heal += CombatModifiers.meleeHealing(attacker, taken);
			if (heal > 0) {
				float before = attacker.getHealth();
				attacker.heal(heal); // Bloodlust + Leech intentionally share this single call.
				boolean classPresentation = false;
				for (var runtime : abilities.runtimes())
					classPresentation |= runtime.afterMeleeHeal(attacker, victim, taken, before, now);
				if (!classPresentation)
					((ServerLevel) attacker.level()).sendParticles(ParticleTypes.CRIMSON_SPORE,
							attacker.getX(), attacker.getY() + 1.2, attacker.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
			}
		}
		if (victim instanceof ServerPlayer hit && source.getEntity() instanceof ServerPlayer attacker && attacker != hit) {
			var reflections = CombatModifiers.reflections(hit, attacker, taken);
			if (reflections.isEmpty()) return;
			reflecting = true;
			try {
				for (var reflection : reflections) attacker.hurtServer((ServerLevel) attacker.level(), reflection.source(), reflection.damage());
			} finally {
				reflecting = false;
			}
		}
	}

	void clear() { reflecting = false; }
}
