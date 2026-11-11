package dev.flake.render.vfx;

import net.minecraft.world.phys.Vec3;

public abstract class TimedActor implements EffectActor {
	protected final int sourceId;
	protected final Vec3 origin;
	protected final VfxTimeline time = new VfxTimeline();

	protected TimedActor(CombatVfxPayload payload) {
		sourceId = payload.sourceId();
		origin = payload.origin();
	}

	@Override public final int sourceId() { return sourceId; }
	protected final float age(RenderFrame frame) { return time.sample(frame.nanos, frame.client.isPaused()); }
}
