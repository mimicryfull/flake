package dev.flake.render.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Shared frame inputs, sampled once and reused by every effect actor. */
public final class RenderFrame {
	public Minecraft client;
	public Vec3 camera;
	public long nanos;
	public float partial;
	public int quality;

	public void begin(Minecraft client, Vec3 camera, long nanos, int quality) {
		this.client = client;
		this.camera = camera;
		this.nanos = nanos;
		this.quality = quality;
		partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}
}
