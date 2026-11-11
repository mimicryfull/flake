package dev.flake.combat.ability;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;

/** Small shared effect operations, including ownership-aware stun cleanup for class buffs. */
public final class AbilityEffects {
	private AbilityEffects() {}

	public static void sound(ServerLevel level, ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Keep stronger or longer effects granted by another source, including the original two-tick tolerance. */
	public static boolean removeOwnEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int buffTicksLeft) {
		if (buffTicksLeft <= 0) return false;
		var active = player.getEffect(effect);
		if (active == null || active.getAmplifier() != amplifier || active.getDuration() > buffTicksLeft + 2) return false;
		return player.removeEffect(effect);
	}
}
