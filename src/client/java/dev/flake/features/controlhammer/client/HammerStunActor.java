package dev.flake.features.controlhammer.client;

import dev.flake.render.vfx.RadialActor;
import dev.flake.features.controlhammer.HammerAnimation;
import dev.flake.features.concussed.ShockwaveAnimation;
import dev.flake.render.vfx.CombatVfxPayload;

public final class HammerStunActor extends RadialActor {
	public HammerStunActor(CombatVfxPayload payload) { super(payload); }
	@Override protected float duration() { return HammerAnimation.DURATION; }
	@Override protected float radius(float age) { return HammerAnimation.radius(age); }
	@Override protected float opacity(float age) { return HammerAnimation.opacity(age); }
	@Override protected int style() { return 0; }
	@Override protected float styleExtra(float age) { return ShockwaveAnimation.chargeProgress(age); }
}
