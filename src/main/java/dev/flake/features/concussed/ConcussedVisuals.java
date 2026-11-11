package dev.flake.features.concussed;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class ConcussedVisuals {
	private static final int DURATION = 80;

	private ConcussedVisuals() {}

	public static void charge(ServerPlayer source) {
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.45f, 0.7f);
		send(source, CombatVfxPayload.Phase.CHARGE, 0.0f);
	}

	public static void play(ServerPlayer source) {
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.75f, 0.7f);
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.22f, 1.65f);
		send(source, CombatVfxPayload.Phase.BURST, 0.0f);
	}

	public static void playQuake(ServerPlayer source, double dirX, double dirZ) {
		float yaw = (float) Math.atan2(dirZ, dirX);
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 0.32f, 0.58f);
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.20f, 1.75f);
		send(source, CombatVfxPayload.Phase.QUAKE, yaw);
	}

	/** Called only after the server actually applied a shockwave impulse to this victim. */
	public static void impact(ServerPlayer victim, ServerPlayer source) {
		if (victim == source) return;
		CombatVisuals.send(victim, new CombatVfxPayload(source.position().add(0, 1, 0),
				source.getId(), CombatVfxPayload.Phase.IMPACT), DURATION);
	}

	private static void send(ServerPlayer source, CombatVfxPayload.Phase phase, float yaw) {
		var origin = source.position();
		var waveOrigin = phase == CombatVfxPayload.Phase.QUAKE ? origin : origin.add(0, 1, 0);
		CombatVisuals.broadcast(source, new CombatVfxPayload(waveOrigin, source.getId(), phase, yaw), origin, DURATION);
	}
}
