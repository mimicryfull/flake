package dev.flake.bootstrap;

import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.VfxRenderState;
import dev.flake.render.vfx.VfxRenderState.Admission;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.features.bastion.client.BulwarkActor;
import dev.flake.features.bastion.client.ShieldRamActor;
import dev.flake.features.concussed.client.ChargeActor;
import dev.flake.features.concussed.client.QuakeActor;
import dev.flake.features.concussed.client.ShockwaveActor;
import dev.flake.features.controlhammer.client.HammerStunActor;
import dev.flake.features.chaosbroadsword.client.BroadswordSweepActor;
import dev.flake.features.crystalclaw.client.CrystalClawActor;
import dev.flake.features.darkcube.client.DarkBoltActor;
import dev.flake.features.matadora.client.MatadoraActor;
import dev.flake.features.vampire.client.GhoulRenderState;
import dev.flake.features.voidpaladin.client.CritActor;
import dev.flake.render.vfx.WeaponStreakActor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Composition root only: no animation math, geometry, or feature state. */
public final class EffectActors {
	public static void install() { VfxRenderState.configure(EffectActors::admission, EffectActors::create); }

	private EffectActors() {}

	public static Admission admission(CombatVfxPayload.Phase phase) {
		return switch (phase) {
			case BLOOD_LUNGE, BLOODLUST_ON, BLOODLUST_OFF, BLOOD_SIPHON -> Admission.COALESCE;
			case MASK_OFF, CRYSTAL_OFF, BULWARK_OFF -> Admission.UPDATE_ALL;
			case BULWARK_HIT -> Admission.UPDATE_FIRST;
			default -> Admission.REPLACE;
		};
	}

	public static EffectActor create(CombatVfxPayload payload, ClientLevel level) {
		return switch (payload.phase()) {
			case CHARGE -> new ChargeActor(payload);
			case QUAKE -> new QuakeActor(payload, level);
			case MASK_ON -> new MatadoraActor(payload);
			case CRYSTAL_ON -> new CrystalClawActor(payload);
			case BULWARK_ON -> new BulwarkActor(payload);
			case SHIELD_RAM, SHIELD_RAM_SHIELD -> new ShieldRamActor(payload);
			case BOLT -> new DarkBoltActor(payload, level);
			case CRIT -> new CritActor(payload);
			case BLEED_CRIT -> new CritActor(payload, true);
			case AXE_THROW -> new WeaponStreakActor(payload, new Vec3(1.0, 0.55, 0.12), 0.34f, 0.040f);
			case SPEAR_DASH -> new WeaponStreakActor(payload, new Vec3(0.35, 0.95, 0.45), 0.26f, 0.050f);
			case BROADSWORD_SWEEP -> new BroadswordSweepActor(payload);
			case HAMMER_STUN -> new HammerStunActor(payload);
			case BLOOD_LUNGE, BLOODLUST_ON, BLOOD_SIPHON -> new GhoulRenderState.Actor(payload);
			default -> new ShockwaveActor(payload);
		};
	}
}
