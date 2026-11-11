package dev.flake.render.vfx;


/** Common OFF/recast semantics, independent of a buff's animation or geometry. */
public abstract class TimedBuffActor extends TimedActor {
	protected float offAt = Float.NaN;
	private final CombatVfxPayload.Phase on, off;
	protected final float duration;

	protected TimedBuffActor(CombatVfxPayload payload, CombatVfxPayload.Phase on, CombatVfxPayload.Phase off, float duration) {
		super(payload);
		this.on = on; this.off = off; this.duration = duration;
	}

	@Override public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId || payload.phase() != off) return false;
		if (Float.isNaN(offAt)) offAt = Math.min(duration, time.sample(System.nanoTime(), false));
		return true;
	}

	@Override public final boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == on;
	}

	protected final float off() { return Float.isNaN(offAt) ? duration : offAt; }
}
