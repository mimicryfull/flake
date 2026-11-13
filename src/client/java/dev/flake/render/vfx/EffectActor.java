package dev.flake.render.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

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
	/** True when this actor can be removed even if post-effect uniforms() isn't ticking. */
	default boolean expired() { return false; }
	/** Optional world-space mesh submission during level rendering. */
	default void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {}
}
