package dev.flake.features.concussed.client;

import dev.flake.render.vfx.RadialActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.features.concussed.Shockwave;
import dev.flake.features.concussed.ShockwaveAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import net.minecraft.world.phys.Vec3;

public final class ChargeActor extends RadialActor {
	public ChargeActor(CombatVfxPayload payload) { super(payload); }
	@Override public boolean charging() { return true; }
	@Override protected float duration() { return Shockwave.CHARGE_TICKS / 20f + 1; }
	@Override protected float radius(float age) { return ShockwaveAnimation.chargeRadius(age); }
	@Override protected float opacity(float age) { return 0.35f + ShockwaveAnimation.chargeProgress(age) * 0.65f; }
	@Override protected int style() { return 1; }
	@Override protected float styleExtra(float age) { return ShockwaveAnimation.chargeProgress(age); }
	@Override protected Vec3 position(RenderFrame frame) {
		var source = frame.client.level.getEntity(sourceId);
		return source != null ? source.getPosition(frame.partial).add(0, 1, 0) : origin;
	}
}
