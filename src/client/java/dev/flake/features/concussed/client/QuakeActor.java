package dev.flake.features.concussed.client;

import dev.flake.render.vfx.RadialActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.features.concussed.ElectricQuake;
import dev.flake.render.vfx.CombatVfxPayload;
import net.minecraft.client.multiplayer.ClientLevel;

public final class QuakeActor extends RadialActor {
	private final GroundLightning quake;
	private final float yaw;

	public QuakeActor(CombatVfxPayload payload, ClientLevel level) {
		super(payload);
		yaw = payload.yaw();
		quake = new GroundLightning(level, origin, yaw,
				((long) sourceId << 32) ^ Double.doubleToLongBits(origin.x + origin.z) ^ level.getGameTime());
	}
	@Override protected float duration() { return GroundLightning.DURATION; }
	@Override protected float radius(float age) { return ElectricQuake.wavefrontDistance(age); }
	@Override protected float opacity(float age) { return 1; }
	@Override protected int style() { return 2; }
	@Override protected float styleExtra(float age) { return yaw; }

	@Override protected void details(RenderFrame frame, SlotWriter slot, float age, float ox, float oy, float oz) {
		float[] values = slot.values;
		int quality = frame.quality;
		float front = ElectricQuake.wavefrontDistance(age);
		double dx = Math.cos(quake.yaw()), dz = Math.sin(quake.yaw());
		for (int group = 0; group < GroundLightning.GROUPS; group++) {
			boolean visible = false;
			for (int j = 0; j < GroundLightning.PER_GROUP; j++) {
				int i = group * GroundLightning.PER_GROUP + j;
				var s = quake.segments[i];
				if (s == null || s.detail() > quality || age < s.birth()) continue;
				float elapsed = Math.max(0, age - s.end());
				if (elapsed >= s.life()) continue;
				float reveal = Math.clamp((age - s.birth()) / (s.end() - s.birth()), 0, 1);
				float fade = 1 - elapsed / s.life();
				float freq = s.detail() > 0 ? 155 : 115;
				float flicker = 0.76f + 0.24f * (float) Math.sin(age * freq + s.phase());
				if (s.phase() > 3.6f && elapsed > 0.055f && elapsed < 0.088f) flicker += 0.62f;
				int start = slot.segment + i * 4, end = slot.segmentEnd + i * 4;
				values[start] = ox + (float) s.a().x; values[start + 1] = oy + (float) s.a().y;
				values[start + 2] = oz + (float) s.a().z;
				values[start + 3] = s.width() * (0.93f + flicker * 0.07f);
				values[end] = ox + (float) (s.a().x + (s.b().x - s.a().x) * reveal);
				values[end + 1] = oy + (float) (s.a().y + (s.b().y - s.a().y) * reveal);
				values[end + 2] = oz + (float) (s.a().z + (s.b().z - s.a().z) * reveal);
				values[end + 3] = fade * fade * flicker * (0.62f + s.power() * 1.15f);
				visible = true;
			}
			for (int j = 0; j < 4; j++) {
				if (j == 1 && quality < 1 || j == 2 && quality < 2
						|| j == 3 && (quality == 0 && group != 3 && group != 7 || quality == 1 && group % 2 == 0)) continue;
				int i = group * 4 + j;
				var m = quake.motes[i];
				if (m == null) continue;
				float t = age - m.birth();
				if (t < 0 || t >= m.life()) continue;
				float fade = 1 - t / m.life();
				float travel = (float) (-Math.expm1(-4 * t) / 4), speed = (float) Math.exp(-4 * t);
				float gravity = m.debris() ? 24 : 14;
				int start = slot.particle + i * 4, tail = slot.tail + i * 4, material = slot.material + i * 4;
				values[start] = ox + (float) m.origin().x + m.vx() * travel;
				values[start + 1] = oy + (float) m.origin().y + m.vy() * t - gravity * t * t * 0.5f;
				values[start + 2] = oz + (float) m.origin().z + m.vz() * travel;
				values[start + 3] = m.size() * (m.debris() ? -1 : 1);
				values[tail] = -m.vx() * speed * 0.032f;
				values[tail + 1] = -(m.vy() - gravity * t) * 0.032f;
				values[tail + 2] = -m.vz() * speed * 0.032f;
				values[tail + 3] = fade * Math.min(1, t * 180);
				values[material] = ((m.color() >> 16) & 255) / 255f;
				values[material + 1] = ((m.color() >> 8) & 255) / 255f;
				values[material + 2] = (m.color() & 255) / 255f;
				values[material + 3] = m.spin() + t * 13;
				visible = true;
			}
			var p = quake.centres[group];
			if (p != null) {
				double groupForward = p.x * dx + p.z * dz;
				float groupArrival = GroundLightning.arrival(Math.max(ElectricQuake.MIN_FORWARD, groupForward - 0.55));
				boolean fogActive = front >= groupForward - 0.85 && age >= groupArrival && age < groupArrival + 0.50f;
				if (visible || fogActive) {
					int base = slot.bounds + group * 4;
					values[base] = ox + (float) p.x; values[base + 1] = oy + (float) p.y; values[base + 2] = oz + (float) p.z;
					values[base + 3] = quake.radii[group];
				}
			}
		}
	}
}
