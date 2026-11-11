package dev.flake.render.vfx;

import net.minecraft.world.phys.Vec3;

/** Common camera-relative radial placement and conservative distance culling. */
public abstract class RadialActor extends TimedActor {
	protected RadialActor(CombatVfxPayload payload) { super(payload); }
	protected abstract float duration();
	protected abstract float radius(float age);
	protected abstract float opacity(float age);
	protected abstract int style();
	protected float styleExtra(float age) { return 0; }
	protected Vec3 position(RenderFrame frame) { return origin; }
	protected void details(RenderFrame frame, SlotWriter slot, float age, float x, float y, float z) {}

	@Override public final boolean write(RenderFrame frame, SlotWriter slot) {
		float age = age(frame);
		if (age >= duration()) return true;
		var origin = position(frame);
		float ox = (float) (origin.x - frame.camera.x), oy = (float) (origin.y - frame.camera.y), oz = (float) (origin.z - frame.camera.z);
		if (ox * ox + oy * oy + oz * oz > 64 * 64) return false;
		float[] values = slot.values;
		values[slot.position] = ox; values[slot.position + 1] = oy; values[slot.position + 2] = oz;
		values[slot.position + 3] = radius(age);
		values[slot.style] = opacity(age);
		values[slot.style + 1] = age;
		values[slot.style + 2] = style();
		values[slot.style + 3] = styleExtra(age);
		details(frame, slot, age, ox, oy, oz);
		return false;
	}
}
