package dev.flake.render.vfx;


/** A bounded actor owns only its feature's state, while the renderer owns slot admission. */
public interface EffectActor {
	int sourceId();
	/** True when the actor consumed an update without allocating another slot. */
	default boolean receive(CombatVfxPayload payload) { return false; }
	/** Recasting an aura replaces its old actor rather than stacking it. */
	default boolean replaces(CombatVfxPayload payload) { return false; }
	default boolean charging() { return false; }
	/** True after all animation tails have expired. */
	boolean write(RenderFrame frame, SlotWriter slot);
}
