package dev.flake.features.matadora.client;

import dev.flake.render.vfx.FocusAuraActor;
import dev.flake.features.matadora.MaskAnimation;
import dev.flake.render.vfx.CombatVfxPayload;

public final class MatadoraActor extends FocusAuraActor {
	public MatadoraActor(CombatVfxPayload payload) {
		super(payload, CombatVfxPayload.Phase.MASK_ON, CombatVfxPayload.Phase.MASK_OFF, MaskAnimation.BUFF_SECONDS);
	}
	@Override protected boolean finished(float age, float off) { return MaskAnimation.finished(age, off); }
	@Override protected float intensity(float age, float off) { return MaskAnimation.intensity(age, off); }
	@Override protected float burst(float age) { return MaskAnimation.burst(age); }
	@Override protected int ownerStyle() { return 4; }
}
