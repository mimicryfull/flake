package dev.flake.render.vfx;

import net.minecraft.world.phys.Vec3;

/** Camera-relative glowing streak between two world points; used by thrown artifacts and dashes. */
public final class WeaponStreakActor extends TimedActor {
	private final Vec3 end;
	private final Vec3 color;
	private final float duration;
	private final float width;

	public WeaponStreakActor(CombatVfxPayload payload, Vec3 color, float duration, float width) {
		super(payload);
		this.end = payload.aim();
		this.color = color;
		this.duration = duration;
		this.width = width;
	}

	@Override public boolean write(RenderFrame frame, SlotWriter slot) {
		float age = age(frame);
		if (age >= duration) return true;
		var camera = frame.camera;
		float sx = (float) (origin.x - camera.x), sy = (float) (origin.y - camera.y), sz = (float) (origin.z - camera.z);
		float ex = (float) (end.x - camera.x), ey = (float) (end.y - camera.y), ez = (float) (end.z - camera.z);
		if (sx * sx + sy * sy + sz * sz > 96 * 96 && ex * ex + ey * ey + ez * ez > 96 * 96) return false;

		float progress = Math.clamp(age / duration, 0f, 1f);
		float alpha = Math.clamp(age / 0.02f, 0f, 1f) * (1f - smoothstep(0.78f * duration, duration, age));
		float[] values = slot.values;
		values[slot.position] = sx; values[slot.position + 1] = sy; values[slot.position + 2] = sz; values[slot.position + 3] = 0f;
		values[slot.style] = alpha;
		values[slot.style + 1] = age;
		values[slot.style + 2] = 13f;
		values[slot.style + 3] = progress;
		values[slot.material] = ex; values[slot.material + 1] = ey; values[slot.material + 2] = ez; values[slot.material + 3] = width;
		values[slot.material + 4] = (float) color.x;
		values[slot.material + 5] = (float) color.y;
		values[slot.material + 6] = (float) color.z;
		values[slot.material + 7] = 0f;
		float mx = (sx + ex) * 0.5f, my = (sy + ey) * 0.5f, mz = (sz + ez) * 0.5f;
		float half = (float) Math.sqrt((ex - sx) * (ex - sx) + (ey - sy) * (ey - sy) + (ez - sz) * (ez - sz)) * 0.5f;
		values[slot.bounds] = mx; values[slot.bounds + 1] = my; values[slot.bounds + 2] = mz;
		values[slot.bounds + 3] = half + width * 5f + 0.5f;
		return false;
	}

	private static float smoothstep(float low, float high, float value) {
		float t = Math.clamp((value - low) / (high - low), 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
