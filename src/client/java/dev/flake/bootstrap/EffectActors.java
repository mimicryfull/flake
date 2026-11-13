package dev.flake.bootstrap;

import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.VfxRenderState;
import dev.flake.render.vfx.VfxRenderState.Admission;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.features.axeofcumshot.client.AxeThrowActor;
import dev.flake.features.chronotrident.client.ChronosphereActor;
import dev.flake.features.bastion.client.BulwarkActor;
import dev.flake.features.bastion.client.ShieldRamActor;
import dev.flake.features.concussed.client.ChargeActor;
import dev.flake.features.concussed.client.QuakeActor;
import dev.flake.features.concussed.client.ShockwaveActor;
import dev.flake.features.controlhammer.client.HammerStunActor;
import dev.flake.features.chaosbroadsword.client.BroadswordSweepActor;
import dev.flake.features.crystalclaw.client.CrystalClawActor;
import dev.flake.features.darkcube.client.DarkBoltActor;
import dev.flake.features.demolitionist.client.DemolitionExplosionActor;
import dev.flake.features.demolitionist.client.ProximityMineActor;
import dev.flake.features.junglespear.client.SpearDashActor;
import dev.flake.features.matadora.client.MatadoraActor;
import dev.flake.features.vampire.client.GhoulRenderState;
import dev.flake.features.voidpaladin.client.CritActor;
import dev.flake.features.passives.client.HunterCompassActor;
import dev.flake.features.passives.client.StormArcActor;
import dev.flake.features.voidanchor.client.VoidAnchorActor;
import dev.flake.features.mirrortrap.client.MirrorTrapActor;
import dev.flake.features.sparksyringe.client.SparkSyringeActor;
import dev.flake.features.huntermark.client.HunterMarkActor;
import dev.flake.features.stormharpoon.client.StormHarpoonActor;
import dev.flake.features.phasecrystal.client.PhaseCrystalActor;
import dev.flake.features.chainblade.client.ChainBladeActor;
import dev.flake.features.stormstaff.client.StormStaffActor;
import dev.flake.features.shaman.client.TotemOfPainActor;
import dev.flake.features.shaman.client.SpiritWardActor;
import net.minecraft.client.multiplayer.ClientLevel;

/** Composition root only: no animation math, geometry, or feature state. */
public final class EffectActors {
	public static void install() { VfxRenderState.configure(EffectActors::admission, EffectActors::create); }

	private EffectActors() {}

	public static Admission admission(CombatVfxPayload.Phase phase) {
		return switch (phase) {
			case BLOOD_LUNGE, BLOODLUST_ON, BLOODLUST_OFF, BLOOD_SIPHON,
					VOID_ANCHOR_PULL, MIRROR_TRAP_REFLECT -> Admission.COALESCE;
			case MASK_OFF, CRYSTAL_OFF, BULWARK_OFF, MIRROR_TRAP_OFF, PHASE_CRYSTAL_OFF -> Admission.UPDATE_ALL;
			case BULWARK_HIT, MINE_DISARM, SHAMAN_TOTEM_OFF -> Admission.UPDATE_FIRST;
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
			case AXE_THROW -> new AxeThrowActor(payload);
			case SPEAR_DASH -> new SpearDashActor(payload);
			case BROADSWORD_SWEEP -> new BroadswordSweepActor(payload);
			case CHRONOSPHERE -> new ChronosphereActor(payload);
			case MINE_PLACE -> new ProximityMineActor(payload);
			case MINE_DETONATE, BLAST_JUMP -> new DemolitionExplosionActor(payload);
			case HAMMER_STUN -> new HammerStunActor(payload);
			case BLOOD_LUNGE, BLOODLUST_ON, BLOOD_SIPHON -> new GhoulRenderState.Actor(payload);
			case HUNTER_HIGHLIGHT -> new HunterCompassActor(payload);
			case STORM_ARC -> new StormArcActor(payload);
			case VOID_ANCHOR_CAST, VOID_ANCHOR_PULL -> new VoidAnchorActor(payload);
			case MIRROR_TRAP_ON, MIRROR_TRAP_REFLECT -> new MirrorTrapActor(payload);
			case SPARK_SYRINGE -> new SparkSyringeActor(payload);
			case HUNTER_MARK -> new HunterMarkActor(payload);
			case STORM_HARPOON -> new StormHarpoonActor(payload);
			case PHASE_CRYSTAL_ON -> new PhaseCrystalActor(payload);
			case CHAIN_BLADE_PULL -> new ChainBladeActor(payload);
			case STORM_STAFF_RAY -> new StormStaffActor(payload);
			case SHAMAN_TOTEM -> new TotemOfPainActor(payload);
			case SHAMAN_WARD -> new SpiritWardActor(payload);
			default -> new ShockwaveActor(payload);
		};
	}
}
