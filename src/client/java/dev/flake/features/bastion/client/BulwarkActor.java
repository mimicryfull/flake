package dev.flake.features.bastion.client;

import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedBuffActor;
import dev.flake.features.bastion.BastionAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The shield keeps its last body pose while dissolving, including after entity removal. */
public final class BulwarkActor extends TimedBuffActor {
	private final VfxTimeline hit = new VfxTimeline();
	private boolean hitFired;
	private Vec3 hitDirection = Vec3.ZERO, lastBody;

	public BulwarkActor(CombatVfxPayload payload) {
		super(payload, CombatVfxPayload.Phase.BULWARK_ON, CombatVfxPayload.Phase.BULWARK_OFF, BastionAnimation.Bulwark.BUFF_SECONDS);
		lastBody = origin;
	}

	@Override public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.BULWARK_HIT) {
			hit.reset(); hitFired = true; hitDirection = payload.aim();
			return true;
		}
		return super.receive(payload);
	}

	@Override public boolean write(RenderFrame frame, SlotWriter slot) {
		float age = age(frame), off = off();
		if (BastionAnimation.Bulwark.finished(age, off)) return true;
		var client = frame.client;
		float hitAge = hitFired ? hit.sample(frame.nanos, client.isPaused()) : -1;
		boolean owner = client.player != null && client.player.getId() == sourceId;
		float intensity = BastionAnimation.Bulwark.intensity(age, off);
		var entity = client.level.getEntity(sourceId);
		if ((entity == null || !entity.isAlive()) && Float.isNaN(offAt)) offAt = Math.min(age, off);
		var feet = entity != null ? entity.getPosition(frame.partial) : lastBody;
		lastBody = feet;
		float fx = (float) (feet.x - frame.camera.x), fy = (float) (feet.y - frame.camera.y), fz = (float) (feet.z - frame.camera.z);
		float[] values = slot.values;
		if (fx * fx + fy * fy + fz * fz <= 48 * 48) {
			values[slot.bounds] = fx; values[slot.bounds + 1] = fy; values[slot.bounds + 2] = fz;
			values[slot.bounds + 3] = entity != null ? entity.getBbHeight() : 1.8f;
		} else if (!owner) return false;
		values[slot.style] = intensity;
		values[slot.style + 1] = age;
		values[slot.style + 2] = owner && client.options.getCameraType().isFirstPerson() ? 11 : 10;
		values[slot.style + 3] = client.options.screenEffectScale().get().floatValue();
		int material = slot.material;
		values[material] = entity instanceof LivingEntity living ? Math.clamp(living.getAbsorptionAmount() / 4, 0, 1) : 0;
		values[material + 1] = BastionAnimation.Bulwark.hit(hitAge);
		values[material + 2] = intensity;
		values[material + 3] = BastionAnimation.Bulwark.burst(age);
		values[material + 4] = (float) hitDirection.x;
		values[material + 5] = (float) hitDirection.y;
		values[material + 6] = (float) hitDirection.z;
		values[material + 7] = hitAge;
		values[material + 8] = Float.isNaN(offAt) ? off : offAt;
		return false;
	}
}
