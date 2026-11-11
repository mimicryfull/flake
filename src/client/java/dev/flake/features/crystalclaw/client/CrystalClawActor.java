package dev.flake.features.crystalclaw.client;

import dev.flake.render.vfx.FocusAuraActor;
import dev.flake.features.crystalclaw.CrystalClawAnimation;
import dev.flake.render.vfx.CombatVfxPayload;

public final class CrystalClawActor extends FocusAuraActor {
	public CrystalClawActor(CombatVfxPayload payload) {
		super(payload, CombatVfxPayload.Phase.CRYSTAL_ON, CombatVfxPayload.Phase.CRYSTAL_OFF, CrystalClawAnimation.BUFF_SECONDS);
	}
	@Override protected boolean finished(float age, float off) { return CrystalClawAnimation.finished(age, off); }
	@Override protected float intensity(float age, float off) { return CrystalClawAnimation.intensity(age, off); }
	@Override protected float burst(float age) { return CrystalClawAnimation.burst(age); }
	@Override protected int ownerStyle() { return 6; }
}
