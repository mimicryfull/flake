package dev.flake.features.voidpaladin.client;

import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedActor;
import dev.flake.features.voidpaladin.CritAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import net.minecraft.world.phys.Vec3;

/** World-space crimson rupture at the server-confirmed crit hit point. */
public final class CritActor extends TimedActor {
	private final Vec3 aim;
	private final int targetId;
	private final boolean orange;

	public CritActor(CombatVfxPayload payload) {
		this(payload, false);
	}

	public CritActor(CombatVfxPayload payload, boolean orange) {
		super(payload);
		aim = payload.aim(); targetId = payload.targetId(); this.orange = orange;
	}

	@Override public boolean write(RenderFrame frame, SlotWriter slot) {
		float age = age(frame);
		if (age >= CritAnimation.DURATION) return true;
		var cameraPos = frame.camera;
		float[] values = slot.values;
		float ox = (float) (origin.x - cameraPos.x);
		float oy = (float) (origin.y - cameraPos.y);
		float oz = (float) (origin.z - cameraPos.z);
		if (ox * ox + oy * oy + oz * oz > 96 * 96) return false;
		float scale = 1.15f;
		int position = slot.position;
		values[position] = ox; values[position + 1] = oy; values[position + 2] = oz; values[position + 3] = scale;
		int style = slot.style;
		values[style] = CritAnimation.alpha(age);
		values[style + 1] = age;
		values[style + 2] = 8;
		values[style + 3] = 0.37f + slot.index * 0.41f;
		int bounds = slot.bounds;
		values[bounds] = ox; values[bounds + 1] = oy; values[bounds + 2] = oz; values[bounds + 3] = scale * 2.4f;
		int material = slot.material;
		values[material] = (float) aim.x; values[material + 1] = (float) aim.y; values[material + 2] = (float) aim.z;
		values[material + 3] = values[style + 3];
		var victim = frame.client.level.getEntity(targetId);
		double groundY = victim != null ? victim.getY() : origin.y - 1.0;
		values[material + 4] = (float) (groundY - cameraPos.y);
		values[material + 5] = orange ? 1f : 0f;
		return false;
	}
}
