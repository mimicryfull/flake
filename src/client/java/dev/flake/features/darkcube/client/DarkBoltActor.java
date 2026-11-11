package dev.flake.features.darkcube.client;

import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedActor;
import dev.flake.features.darkcube.DarkBoltAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** One homing bolt; the server-chosen target id is never re-evaluated. */
public final class DarkBoltActor extends TimedActor {
	private final int targetId;
	private final Vec3 aim;
	private Vec3 lastStart;
	private final float[] dx = new float[DarkBoltAnimation.FRAGMENTS], dy = new float[DarkBoltAnimation.FRAGMENTS],
			dz = new float[DarkBoltAnimation.FRAGMENTS], speed = new float[DarkBoltAnimation.FRAGMENTS];

	public DarkBoltActor(CombatVfxPayload payload, ClientLevel level) {
		super(payload);
		targetId = payload.targetId(); aim = payload.aim();
		var random = new java.util.Random(((long) sourceId << 20) ^ targetId ^ (level == null ? 0 : level.getGameTime()));
		for (int i = 0; i < DarkBoltAnimation.FRAGMENTS; i++) {
			double y = random.nextDouble() * 2 - 1, r = Math.sqrt(1 - y * y), a = random.nextDouble() * Math.PI * 2;
			dx[i] = (float) (Math.cos(a) * r); dy[i] = (float) y; dz[i] = (float) (Math.sin(a) * r);
			speed[i] = 0.7f + random.nextFloat() * 0.9f;
		}
	}

	@Override public boolean write(RenderFrame frame, SlotWriter slot) {
		float age = age(frame);
		if (age >= DarkBoltAnimation.DURATION) return true;
		var client = frame.client;
		var cameraPos = frame.camera;
		float partial = frame.partial;
		float[] values = slot.values;
		Vec3 start = origin, aim = this.aim;
		var source = client.level.getEntity(sourceId);
		if (source != null && age < DarkBoltAnimation.CHARGE + DarkBoltAnimation.SHOT) {
			var look = source.getViewVector(partial);
			var right = look.cross(new Vec3(0, 1, 0)).normalize();
			start = source.getEyePosition(partial).add(look.scale(0.55)).add(right.scale(0.28)).add(0, -0.28, 0);
			lastStart = start;
		} else if (lastStart != null) start = lastStart;
		var target = targetId >= 0 ? client.level.getEntity(targetId) : null;
		if (target != null) aim = target.getPosition(partial).add(0, target.getBbHeight() * 0.6, 0);
		float sx = (float) (start.x - cameraPos.x), sy = (float) (start.y - cameraPos.y), sz = (float) (start.z - cameraPos.z);
		float tx = (float) (aim.x - cameraPos.x), ty = (float) (aim.y - cameraPos.y), tz = (float) (aim.z - cameraPos.z);
		float length = (float) Math.sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy) + (tz - sz) * (tz - sz));
		if (sx * sx + sy * sy + sz * sz > 64 * 64 && tx * tx + ty * ty + tz * tz > 64 * 64) return false;
		int position = slot.position;
		values[position] = sx; values[position + 1] = sy; values[position + 2] = sz; values[position + 3] = length;
		int style = slot.style;
		values[style] = Math.min(1, (DarkBoltAnimation.DURATION - age) / 0.12f);
		values[style + 1] = age;
		values[style + 2] = 3;
		int bounds = slot.bounds;
		values[bounds] = tx; values[bounds + 1] = ty; values[bounds + 2] = tz; values[bounds + 3] = length * 0.5f + 2;
		int material = slot.material;
		float hit = DarkBoltAnimation.hit(age);
		values[material] = DarkBoltAnimation.orbRadius(age);
		values[material + 1] = DarkBoltAnimation.head(age);
		values[material + 2] = DarkBoltAnimation.tail(age);
		values[material + 3] = hit;
		if (hit <= 0) return false;
		int count = frame.quality == 0 ? 3 : frame.quality == 1 ? 5 : DarkBoltAnimation.FRAGMENTS;
		float travel = DarkBoltAnimation.easeOutCubic(hit);
		float fade = (1 - hit) * (1 - hit);
		for (int i = 0; i < count; i++) {
			int base = slot.particle + i * 4;
			float speed = this.speed[i];
			values[base] = tx + dx[i] * speed * travel;
			values[base + 1] = ty + dy[i] * speed * travel;
			values[base + 2] = tz + dz[i] * speed * travel;
			values[base + 3] = 0.022f + 0.012f * (i % 3);
			base = slot.tail + i * 4;
			float tail = speed * 0.38f * (1 - hit);
			values[base] = -dx[i] * tail; values[base + 1] = -dy[i] * tail; values[base + 2] = -dz[i] * tail;
			values[base + 3] = fade;
		}
		return false;
	}
}
