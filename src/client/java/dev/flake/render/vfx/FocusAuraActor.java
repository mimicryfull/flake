package dev.flake.render.vfx;

import dev.flake.client.settings.FocusOverlaySettings;

/** Shared owner-overlay/body-aura placement. Each item owns its envelope and shader style. */
public abstract class FocusAuraActor extends TimedBuffActor {
	protected FocusAuraActor(CombatVfxPayload payload, CombatVfxPayload.Phase on, CombatVfxPayload.Phase off, float duration) {
		super(payload, on, off, duration);
	}

	protected abstract boolean finished(float age, float off);
	protected abstract float intensity(float age, float off);
	protected abstract float burst(float age);
	protected abstract int ownerStyle();

	@Override public final boolean write(RenderFrame frame, SlotWriter slot) {
		float age = age(frame), off = off();
		if (finished(age, off)) return true;
		var client = frame.client;
		boolean owner = client.player != null && client.player.getId() == sourceId;
		float[] values = slot.values;
		if (!owner || !client.options.getCameraType().isFirstPerson()) {
			var entity = client.level.getEntity(sourceId);
			if (entity == null && !owner) return false;
			if (entity != null) {
				var feet = entity.getPosition(frame.partial);
				float fx = (float) (feet.x - frame.camera.x), fy = (float) (feet.y - frame.camera.y), fz = (float) (feet.z - frame.camera.z);
				if (fx * fx + fy * fy + fz * fz <= 48 * 48) {
					values[slot.bounds] = fx; values[slot.bounds + 1] = fy; values[slot.bounds + 2] = fz;
					values[slot.bounds + 3] = entity.getBbHeight();
				} else if (!owner) return false;
			}
		}
		values[slot.style] = intensity(age, off);
		values[slot.style + 1] = age;
		values[slot.style + 2] = ownerStyle() + (owner ? 0 : 1);
		values[slot.style + 3] = client.options.screenEffectScale().get().floatValue();
		if (owner) {
			values[slot.material] = burst(age);
			values[slot.position] = FocusOverlaySettings.density / 100f;
			values[slot.position + 1] = FocusOverlaySettings.noise / 100f;
			values[slot.position + 2] = FocusOverlaySettings.distortion / 100f;
		}
		return false;
	}
}
