package dev.flake.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.flake.duel.BastionAnimation;
import dev.flake.duel.CritAnimation;
import dev.flake.duel.CrystalClawAnimation;
import dev.flake.duel.DarkBoltAnimation;
import dev.flake.duel.ElectricQuake;
import dev.flake.duel.EnergyMotes;
import dev.flake.duel.ImpactAnimation;
import dev.flake.duel.MaskAnimation;
import dev.flake.duel.Shockwave;
import dev.flake.duel.ShockwaveAnimation;
import dev.flake.duel.ShockwavePayload;
import dev.flake.duel.VfxTimeline;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class ShockwaveRenderState {
	// One shared std140 UBO, below the OpenGL minimum 16 KiB block limit (15,136 bytes).
	private static final int PARTICLES = ShockwaveAnimation.MAX_WAVES * GroundLightning.MOTES;
	private static final int IMPACT_OFFSET = 36 + ShockwaveAnimation.MAX_WAVES * 8;
	private static final int PARTICLE_OFFSET = IMPACT_OFFSET + 4;
	static final int MATERIAL_OFFSET = PARTICLE_OFFSET + PARTICLES * 8;
	static final int SEGMENT_OFFSET = MATERIAL_OFFSET + PARTICLES * 4;
	static final int SEGMENTS = ShockwaveAnimation.MAX_WAVES * GroundLightning.SEGMENTS;
	static final int BOUNDS_OFFSET = SEGMENT_OFFSET + SEGMENTS * 8;
	public static final int UNIFORM_BYTES = (BOUNDS_OFFSET + ShockwaveAnimation.MAX_WAVES * GroundLightning.GROUPS * 4) * Float.BYTES;
	private static final float[] values = new float[UNIFORM_BYTES / Float.BYTES];
	private static final ArrayList<Wave> waves = new ArrayList<>(ShockwaveAnimation.MAX_WAVES);
	private static final Matrix4f viewProjection = new Matrix4f();
	private static final Matrix4f inverseViewProjection = new Matrix4f();
	private static final VfxTimeline impactTime = new VfxTimeline();
	private static boolean impacting;
	private static ClientLevel level;

	private ShockwaveRenderState() {}

	public static void checkWorld(ClientLevel current) {
		if (level != current) clear();
	}

	public static void receive(ShockwavePayload payload) {
		if (payload.phase() == ShockwavePayload.Phase.CLEAR) { clear(); return; }
		var current = Minecraft.getInstance().level;
		if (current == null) return;
		checkWorld(current);
		level = current;
		if (payload.phase() == ShockwavePayload.Phase.IMPACT) {
			impactTime.reset();
			impacting = true;
			return;
		}
		if (GhoulRenderState.handles(payload.phase())) {
			// One slot per ghoul, not one slot per strike: aura/lunge/two siphons coexist without evicting each other.
			for (var wave : waves) if (wave.ghoul != null && wave.sourceId == payload.sourceId()) {
				wave.ghoul.receive(payload);
				return;
			}
			if (payload.phase() == ShockwavePayload.Phase.BLOODLUST_OFF) return;
			if (waves.size() == ShockwaveAnimation.MAX_WAVES) waves.removeFirst();
			waves.add(new Wave(payload));
			return;
		}
		if (payload.phase() == ShockwavePayload.Phase.MASK_OFF || payload.phase() == ShockwavePayload.Phase.CRYSTAL_OFF
				|| payload.phase() == ShockwavePayload.Phase.BULWARK_OFF) {
			int kind = payload.phase() == ShockwavePayload.Phase.BULWARK_OFF ? 3
					: payload.phase() == ShockwavePayload.Phase.CRYSTAL_OFF ? 2 : 1;
			for (var wave : waves) if (wave.buff() == kind && wave.sourceId == payload.sourceId() && Float.isNaN(wave.offAt))
				wave.offAt = Math.min(buffSeconds(kind), wave.time.sample(System.nanoTime(), false));
			return;
		}
		if (payload.phase() == ShockwavePayload.Phase.BULWARK_HIT) {
			for (var wave : waves) if (wave.buff() == 3 && wave.sourceId == payload.sourceId()) {
				wave.hit.reset();
				wave.hitFired = true;
				wave.hitDirection = payload.aim();
				return;
			}
			return;
		}
		if (payload.phase() == ShockwavePayload.Phase.MASK_ON || payload.phase() == ShockwavePayload.Phase.CRYSTAL_ON
				|| payload.phase() == ShockwavePayload.Phase.BULWARK_ON) {
			int kind = payload.phase() == ShockwavePayload.Phase.BULWARK_ON ? 3
					: payload.phase() == ShockwavePayload.Phase.CRYSTAL_ON ? 2 : 1;
			waves.removeIf(wave -> wave.buff() == kind && wave.sourceId == payload.sourceId());
		}
		waves.removeIf(wave -> wave.charging && wave.sourceId == payload.sourceId());
		if (payload.phase() == ShockwavePayload.Phase.CANCEL) return;
		if (waves.size() == ShockwaveAnimation.MAX_WAVES) waves.removeFirst();
		waves.add(new Wave(payload));
	}

	public static void clear() {
		waves.clear();
		level = null;
		impacting = false;
		impactTime.reset();
		Arrays.fill(values, 0);
	}

	/** Reuses one CPU array and the PostPass-owned UBO. No per-frame particle objects or meshes. */
	public static float[] uniforms() {
		Arrays.fill(values, 0);
		var client = Minecraft.getInstance();
		if (client.level == null || client.level != level) { clear(); return values; }
		if (waves.isEmpty() && !impacting) return values;
		long now = System.nanoTime();
		var camera = client.gameRenderer.mainCamera();
		var cameraPos = camera.position();
		camera.getViewRotationProjectionMatrix(viewProjection).get(values, 0);
		inverseViewProjection.set(viewProjection).invert().get(values, 16);
		boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		values[32] = zeroToOne ? 1 : 2;
		values[33] = zeroToOne ? 0 : -1;
		int quality = switch (client.options.particles().get()) {
			case MINIMAL -> 0;
			case DECREASED -> 1;
			default -> 2;
		};
		values[34] = quality;
		values[35] = 4 + quality * 4;
		if (impacting) {
			float age = impactTime.sample(now, client.isPaused());
			float strength = client.options.screenEffectScale().get().floatValue();
			values[IMPACT_OFFSET] = ImpactAnimation.displacement(age) * strength;
			values[IMPACT_OFFSET + 1] = ImpactAnimation.envelope(age) * strength;
			values[IMPACT_OFFSET + 2] = 0.5f;
			values[IMPACT_OFFSET + 3] = 0.5f;
			if (age >= ImpactAnimation.DURATION) impacting = false;
		}
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		for (int index = 0; index < waves.size();) {
			var wave = waves.get(index);
			if (wave.ghoul != null) {
				if (wave.ghoul.write(values, index, now, client, cameraPos, partial, quality)) { waves.remove(index); continue; }
				index++;
				continue;
			}
			float age = wave.time.sample(now, client.isPaused());
			int buffKind = wave.buff();
			if (buffKind != 0) {
				float off = Float.isNaN(wave.offAt) ? buffSeconds(buffKind) : wave.offAt;
				boolean finished = buffKind == 3 ? BastionAnimation.Bulwark.finished(age, off)
						: buffKind == 2 ? CrystalClawAnimation.finished(age, off) : MaskAnimation.finished(age, off);
				if (finished) { waves.remove(index); continue; }
				float hitAge = buffKind == 3 && wave.hitFired ? wave.hit.sample(now, client.isPaused()) : -1;
				writeBuff(wave, buffKind, index, age, off, client, cameraPos, partial, hitAge);
				index++;
				continue;
			}
			if (wave.ram) {
				if (BastionAnimation.Ram.finished(age)) { waves.remove(index); continue; }
				writeRam(wave, index, age, cameraPos, client, partial);
				index++;
				continue;
			}
			if (wave.crit) {
				if (age >= CritAnimation.DURATION) { waves.remove(index); continue; }
				writeCrit(wave, index, age, cameraPos, client.level);
				index++;
				continue;
			}
			if (wave.bolt) {
				if (age >= DarkBoltAnimation.DURATION) { waves.remove(index); continue; }
				writeBolt(wave, index, age, quality, cameraPos, partial);
				index++;
				continue;
			}
			float duration = wave.quake ? GroundLightning.DURATION
					: wave.charging ? Shockwave.CHARGE_TICKS / 20f + 1 : ShockwaveAnimation.VFX_DURATION_SECONDS;
			if (age >= duration) { waves.remove(index); continue; }
			double x = wave.origin.x, y = wave.origin.y, z = wave.origin.z;
			var source = wave.charging ? client.level.getEntity(wave.sourceId) : null;
			if (source != null) {
				var pos = source.getPosition(partial);
				x = pos.x; y = pos.y + 1; z = pos.z;
			}
			float ox = (float) (x - cameraPos.x), oy = (float) (y - cameraPos.y), oz = (float) (z - cameraPos.z);
			// Conservative distance cull. Shader additionally rejects rays outside each 3D bound.
			if (ox * ox + oy * oy + oz * oz > 64 * 64) { index++; continue; }
			int position = 36 + index * 4;
			values[position] = ox; values[position + 1] = oy; values[position + 2] = oz;
			values[position + 3] = wave.quake ? ElectricQuake.wavefrontDistance(age)
					: wave.charging ? ShockwaveAnimation.chargeRadius(age) : ShockwaveAnimation.radius(age, (float) Shockwave.RADIUS);
			int style = 36 + ShockwaveAnimation.MAX_WAVES * 4 + index * 4;
			values[style] = wave.quake ? 1
					: wave.charging ? 0.35f + ShockwaveAnimation.chargeProgress(age) * 0.65f
					: Math.max(ShockwaveAnimation.opacity(age), age < EnergyMotes.DURATION ? 0.001f : 0f);
			values[style + 1] = age;
			values[style + 2] = wave.quake ? 2 : wave.charging ? 1 : 0;
			values[style + 3] = wave.quake ? wave.yaw : ShockwaveAnimation.chargeProgress(age);
			if (wave.quake) writeLightning(wave.lightning, index, age, quality, ox, oy, oz);
			if (!wave.charging && !wave.quake) {
				double travel = EnergyMotes.travel(age);
				double tailTravel = travel - EnergyMotes.travel(Math.max(0, age - 0.035f));
				for (int p = 0; p < (int) values[35]; p++) {
					// Interleaved sampling preserves a full spherical spread at Low quality.
					int seed = quality == 0 ? p * 3 : quality == 1 ? (p * 5) % EnergyMotes.COUNT : p;
					float opacity = EnergyMotes.opacity(seed, age);
					if (opacity <= 0) continue;
					double reach = EnergyMotes.speed(seed) * travel;
					double tail = EnergyMotes.speed(seed) * tailTravel;
					int entry = index * GroundLightning.MOTES + p;
					int base = PARTICLE_OFFSET + entry * 4;
					values[base] = ox + (float) (wave.ex[seed] * reach);
					values[base + 1] = oy + (float) (wave.ey[seed] * reach);
					values[base + 2] = oz + (float) (wave.ez[seed] * reach);
					values[base + 3] = EnergyMotes.size(seed) * (0.7f + opacity * 0.3f);
					base += PARTICLES * 4;
					values[base] = (float) (-wave.ex[seed] * tail);
					values[base + 1] = (float) (-wave.ey[seed] * tail);
					values[base + 2] = (float) (-wave.ez[seed] * tail);
					values[base + 3] = opacity;
				}
			}
			index++;
		}
		return values;
	}

	private static float buffSeconds(int kind) {
		return kind == 3 ? BastionAnimation.Bulwark.BUFF_SECONDS : kind == 2 ? CrystalClawAnimation.BUFF_SECONDS : MaskAnimation.BUFF_SECONDS;
	}

	/** Bulwark geometry is also visible from inside in first person; 11 only adds a restrained peripheral cue. */
	private static void writeBuff(Wave wave, int kind, int index, float age, float off, Minecraft client, Vec3 cameraPos, float partial, float hitAge) {
		boolean owner = client.player != null && client.player.getId() == wave.sourceId;
		boolean crystal = kind == 2;
		boolean bulwark = kind == 3;
		float intensity = bulwark ? BastionAnimation.Bulwark.intensity(age, off)
				: crystal ? CrystalClawAnimation.intensity(age, off) : MaskAnimation.intensity(age, off);
		int style = 36 + ShockwaveAnimation.MAX_WAVES * 4 + index * 4;
		if (bulwark || !owner || !client.options.getCameraType().isFirstPerson()) {
			var entity = client.level.getEntity(wave.sourceId);
			if (entity == null && !bulwark && !owner) return;
			if (bulwark && (entity == null || !entity.isAlive()) && Float.isNaN(wave.offAt)) wave.offAt = Math.min(age, off);
			if (entity != null || bulwark) {
				var feet = entity != null ? entity.getPosition(partial) : wave.lastBody;
				wave.lastBody = feet;
				float fx = (float) (feet.x - cameraPos.x), fy = (float) (feet.y - cameraPos.y), fz = (float) (feet.z - cameraPos.z);
				if (fx * fx + fy * fy + fz * fz <= 48 * 48) {
					int bounds = BOUNDS_OFFSET + index * GroundLightning.GROUPS * 4;
					values[bounds] = fx;
					values[bounds + 1] = fy;
					values[bounds + 2] = fz;
					values[bounds + 3] = entity != null ? entity.getBbHeight() : 1.8f;
				} else if (!owner) return;
			}
		}
		values[style] = intensity;
		values[style + 1] = age;
		values[style + 2] = bulwark ? (owner && client.options.getCameraType().isFirstPerson() ? 11 : 10) : (owner ? 4 : 5) + (crystal ? 2 : 0);
		values[style + 3] = client.options.screenEffectScale().get().floatValue();
		if (bulwark) {
			int material = MATERIAL_OFFSET + index * GroundLightning.MOTES * 4;
			var entity = client.level.getEntity(wave.sourceId);
			values[material] = entity instanceof net.minecraft.world.entity.LivingEntity living
					? Math.clamp(living.getAbsorptionAmount() / 4, 0, 1) : 0;
			values[material + 1] = BastionAnimation.Bulwark.hit(hitAge);
			values[material + 2] = intensity;
			values[material + 3] = BastionAnimation.Bulwark.burst(age);
			values[material + 4] = (float) wave.hitDirection.x;
			values[material + 5] = (float) wave.hitDirection.y;
			values[material + 6] = (float) wave.hitDirection.z;
			values[material + 7] = hitAge;
			values[material + 8] = Float.isNaN(wave.offAt) ? off : wave.offAt;
		} else if (owner) {
			values[MATERIAL_OFFSET + index * GroundLightning.MOTES * 4] = crystal ? CrystalClawAnimation.burst(age) : MaskAnimation.burst(age);
			int position = 36 + index * 4;
			values[position] = MaskSettings.density / 100f; values[position + 1] = MaskSettings.noise / 100f;
			values[position + 2] = MaskSettings.distortion / 100f;
		}
	}

	/** Shield Ram: caster body, start point and impact point in one wave; the shader times the phases from age. */
	private static void writeRam(Wave wave, int index, float age, Vec3 cameraPos, Minecraft client, float partial) {
		var source = client.level.getEntity(wave.sourceId);
		var target = wave.targetId >= 0 ? client.level.getEntity(wave.targetId) : null;
		Vec3 body = source != null ? source.getPosition(partial) : wave.lastBody;
		Vec3 dir = new Vec3(Math.cos(wave.yaw), 0, Math.sin(wave.yaw));
		wave.sampleMotion(body, age);
		float bodyHeight = source != null ? source.getBbHeight() : 1.8f;
		float targetHeight = target != null ? target.getBbHeight() : 1.8f;
		Vec3 impact = wave.aim;
		Vec3 mid = body.add(0, bodyHeight * 0.5, 0).lerp(impact, 0.55);
		float span = (float) body.distanceTo(impact);
		if (mid.distanceToSqr(cameraPos) > 64 * 64) return;
		float ox = (float) (mid.x - cameraPos.x), oy = (float) (mid.y - cameraPos.y), oz = (float) (mid.z - cameraPos.z);
		int position = 36 + index * 4;
		values[position] = ox; values[position + 1] = oy; values[position + 2] = oz; values[position + 3] = span * 0.5f + 1.8f;
		int style = 36 + ShockwaveAnimation.MAX_WAVES * 4 + index * 4;
		values[style] = 1.0f;
		values[style + 1] = age;
		values[style + 2] = 9.0f;
		values[style + 3] = wave.shieldRam ? 1.0f : 0.0f;
		int bounds = BOUNDS_OFFSET + index * GroundLightning.GROUPS * 4;
		values[bounds] = (float) (body.x - cameraPos.x);
		values[bounds + 1] = (float) (body.y - cameraPos.y);
		values[bounds + 2] = (float) (body.z - cameraPos.z);
		values[bounds + 3] = bodyHeight;
		values[bounds + 4] = (float) (impact.x - cameraPos.x);
		values[bounds + 5] = (float) (impact.y - cameraPos.y);
		values[bounds + 6] = (float) (impact.z - cameraPos.z);
		values[bounds + 7] = 1.0f + (wave.shieldRam ? 0.42f : 0.0f);
		Vec3 victim = target != null ? target.getPosition(partial) : impact.add(0, -targetHeight * 0.52, 0);
		values[bounds + 8] = (float) (victim.x - cameraPos.x);
		values[bounds + 9] = (float) (victim.y - cameraPos.y);
		values[bounds + 10] = (float) (victim.z - cameraPos.z);
		values[bounds + 11] = targetHeight;
		for (int i = 0; i < wave.history.length; i++) {
			int entry = bounds + 12 + i * 4;
			values[entry] = (float) (wave.history[i].x - cameraPos.x);
			values[entry + 1] = (float) (wave.history[i].y - cameraPos.y);
			values[entry + 2] = (float) (wave.history[i].z - cameraPos.z);
			values[entry + 3] = bodyHeight;
		}
		int material = MATERIAL_OFFSET + index * GroundLightning.MOTES * 4;
		values[material] = (float) dir.x; values[material + 1] = (float) dir.y; values[material + 2] = (float) dir.z;
		values[material + 3] = wave.shieldRam ? 1.0f : 0.0f;
		values[material + 4] = targetHeight;
		values[material + 5] = wave.targetId >= 0 ? 1 : 0;
		values[material + 6] = wave.targetId >= 0 ? BastionAnimation.Ram.impact(age) : 0;
		values[material + 7] = wave.targetId >= 0 ? BastionAnimation.Ram.suppression(age) : 0;
		Vec3 lag = wave.lagBody.subtract(body);
		if (lag.lengthSqr() > 0.16) lag = lag.normalize().scale(0.4);
		values[material + 8] = (float) lag.x;
		values[material + 9] = (float) lag.y;
		values[material + 10] = (float) lag.z;
		values[material + 11] = (float) Math.min(1, lag.length() * 3);
	}

	/** One homing bolt: start/target are tracked live; the server-chosen target id is never re-evaluated. */
	private static void writeBolt(Wave wave, int index, float age, int quality, Vec3 cameraPos, float partial) {
		var client = Minecraft.getInstance();
		Vec3 start = wave.origin, aim = wave.aim;
		var source = client.level.getEntity(wave.sourceId);
		if (source != null && age < DarkBoltAnimation.CHARGE + DarkBoltAnimation.SHOT) {
			var look = source.getViewVector(partial);
			var right = look.cross(new Vec3(0, 1, 0)).normalize();
			start = source.getEyePosition(partial).add(look.scale(0.55)).add(right.scale(0.28)).add(0, -0.28, 0);
			wave.lastStart = start;
		} else if (wave.lastStart != null) start = wave.lastStart;
		var target = wave.targetId >= 0 ? client.level.getEntity(wave.targetId) : null;
		if (target != null) aim = target.getPosition(partial).add(0, target.getBbHeight() * 0.6, 0);
		float sx = (float) (start.x - cameraPos.x), sy = (float) (start.y - cameraPos.y), sz = (float) (start.z - cameraPos.z);
		float tx = (float) (aim.x - cameraPos.x), ty = (float) (aim.y - cameraPos.y), tz = (float) (aim.z - cameraPos.z);
		float length = (float) Math.sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy) + (tz - sz) * (tz - sz));
		if (sx * sx + sy * sy + sz * sz > 64 * 64 && tx * tx + ty * ty + tz * tz > 64 * 64) return;
		int position = 36 + index * 4;
		values[position] = sx; values[position + 1] = sy; values[position + 2] = sz; values[position + 3] = length;
		int style = 36 + ShockwaveAnimation.MAX_WAVES * 4 + index * 4;
		values[style] = Math.min(1, (DarkBoltAnimation.DURATION - age) / 0.12f);
		values[style + 1] = age;
		values[style + 2] = 3;
		int bounds = BOUNDS_OFFSET + index * GroundLightning.GROUPS * 4;
		values[bounds] = tx; values[bounds + 1] = ty; values[bounds + 2] = tz; values[bounds + 3] = length * 0.5f + 2;
		int material = MATERIAL_OFFSET + index * GroundLightning.MOTES * 4;
		float hit = DarkBoltAnimation.hit(age);
		values[material] = DarkBoltAnimation.orbRadius(age);
		values[material + 1] = DarkBoltAnimation.head(age);
		values[material + 2] = DarkBoltAnimation.tail(age);
		values[material + 3] = hit;
		if (hit <= 0) return;
		int count = quality == 0 ? 3 : quality == 1 ? 5 : DarkBoltAnimation.FRAGMENTS;
		float travel = DarkBoltAnimation.easeOutCubic(hit);
		float fade = (1 - hit) * (1 - hit);
		for (int i = 0; i < count; i++) {
			int base = PARTICLE_OFFSET + (index * GroundLightning.MOTES + i) * 4;
			float speed = wave.speed[i];
			values[base] = tx + wave.dx[i] * speed * travel;
			values[base + 1] = ty + wave.dy[i] * speed * travel;
			values[base + 2] = tz + wave.dz[i] * speed * travel;
			values[base + 3] = 0.022f + 0.012f * (i % 3);
			base += PARTICLES * 4;
			float tail = speed * 0.38f * (1 - hit);
			values[base] = -wave.dx[i] * tail; values[base + 1] = -wave.dy[i] * tail; values[base + 2] = -wave.dz[i] * tail;
			values[base + 3] = fade;
		}
	}

	/** World-space crimson rupture at the confirmed crit hit point, oriented along the blade direction. */
	private static void writeCrit(Wave wave, int index, float age, Vec3 cameraPos, ClientLevel level) {
		float ox = (float) (wave.origin.x - cameraPos.x);
		float oy = (float) (wave.origin.y - cameraPos.y);
		float oz = (float) (wave.origin.z - cameraPos.z);
		if (ox * ox + oy * oy + oz * oz > 96 * 96) return;
		float scale = 1.15f;
		int position = 36 + index * 4;
		values[position] = ox; values[position + 1] = oy; values[position + 2] = oz; values[position + 3] = scale;
		int style = 36 + ShockwaveAnimation.MAX_WAVES * 4 + index * 4;
		values[style] = CritAnimation.alpha(age);
		values[style + 1] = age;
		values[style + 2] = 8;
		values[style + 3] = 0.37f + index * 0.41f;
		int bounds = BOUNDS_OFFSET + index * GroundLightning.GROUPS * 4;
		values[bounds] = ox; values[bounds + 1] = oy; values[bounds + 2] = oz; values[bounds + 3] = scale * 2.4f;
		int material = MATERIAL_OFFSET + index * GroundLightning.MOTES * 4;
		var aim = wave.aim;
		values[material] = (float) aim.x; values[material + 1] = (float) aim.y; values[material + 2] = (float) aim.z;
		values[material + 3] = values[style + 3];
		// Ground height under the victim (camera-relative) so blood can fall and settle on the floor.
		var victim = level == null ? null : level.getEntity(wave.targetId);
		double groundY = victim != null ? victim.getY() : wave.origin.y - 1.0;
		values[material + 4] = (float) (groundY - cameraPos.y);
	}

	private static void writeLightning(GroundLightning quake, int wave, float age, int quality, float ox, float oy, float oz) {
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
				// A few branches briefly restrike during the afterglow; no new randomness per frame.
				if (s.phase() > 3.6f && elapsed > 0.055f && elapsed < 0.088f) flicker += 0.62f;
				int entry = wave * GroundLightning.SEGMENTS + i;
				int start = SEGMENT_OFFSET + entry * 4, end = start + SEGMENTS * 4;
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
				int entry = wave * GroundLightning.MOTES + i;
				int start = PARTICLE_OFFSET + entry * 4, tail = start + PARTICLES * 4, material = MATERIAL_OFFSET + entry * 4;
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
					int base = BOUNDS_OFFSET + (wave * GroundLightning.GROUPS + group) * 4;
					values[base] = ox + (float) p.x; values[base + 1] = oy + (float) p.y; values[base + 2] = oz + (float) p.z;
					values[base + 3] = quake.radii[group];
				}
			}
		}
	}

	private static final class Wave {
		private final Vec3 origin;
		private final int sourceId;
		private final GhoulRenderState.Actor ghoul;
		private final boolean charging, quake, bolt, crit, ram, shieldRam;
		/** 0 = not a buff wave, 1 = Matadora dark focus, 2 = Crystal Claw frost, 3 = Bastion Bulwark. */
		private final int buffKind;
		private float offAt = Float.NaN;
		private boolean hitFired;
		private Vec3 hitDirection = Vec3.ZERO;
		private final VfxTimeline hit = new VfxTimeline();
		private final int targetId;
		private final Vec3 aim;
		private Vec3 lastStart;
		private Vec3 lastBody, lagBody;
		private final Vec3[] history = new Vec3[5];
		private float poseAge, historyAge;

		/** Fixed-time world-space breadcrumbs, interpolated across frames, not a straight line to the cast origin. */
		private void sampleMotion(Vec3 body, float age) {
			float dt = age - poseAge;
			if (dt > 0.25f || lastBody.distanceToSqr(body) > 36) {
				Arrays.fill(history, body);
				lagBody = body;
				historyAge = age;
			} else if (dt > 0) {
				lagBody = lagBody.lerp(body, -Math.expm1(-dt / 0.035));
				while (historyAge + 0.035f <= age) {
					historyAge += 0.035f;
					System.arraycopy(history, 0, history, 1, history.length - 1);
					history[0] = lastBody.lerp(body, Math.clamp((historyAge - poseAge) / dt, 0, 1));
				}
			}
			lastBody = body;
			poseAge = age;
		}
		private final float[] dx = new float[DarkBoltAnimation.FRAGMENTS], dy = new float[DarkBoltAnimation.FRAGMENTS],
				dz = new float[DarkBoltAnimation.FRAGMENTS], speed = new float[DarkBoltAnimation.FRAGMENTS];
		private final float yaw;
		private final GroundLightning lightning;
		private final VfxTimeline time = new VfxTimeline();
		private final double[] ex = new double[EnergyMotes.COUNT], ey = new double[EnergyMotes.COUNT], ez = new double[EnergyMotes.COUNT];
		private int buff() { return buffKind; }
		private Wave(ShockwavePayload payload) {
			origin = payload.origin(); sourceId = payload.sourceId(); yaw = payload.yaw();
			ghoul = GhoulRenderState.handles(payload.phase()) ? new GhoulRenderState.Actor(payload) : null;
			lastBody = lagBody = origin;
			Arrays.fill(history, origin);
			charging = payload.phase() == ShockwavePayload.Phase.CHARGE;
			quake = payload.phase() == ShockwavePayload.Phase.QUAKE;
			bolt = payload.phase() == ShockwavePayload.Phase.BOLT;
			crit = payload.phase() == ShockwavePayload.Phase.CRIT;
			ram = payload.phase() == ShockwavePayload.Phase.SHIELD_RAM || payload.phase() == ShockwavePayload.Phase.SHIELD_RAM_SHIELD;
			shieldRam = payload.phase() == ShockwavePayload.Phase.SHIELD_RAM_SHIELD;
			buffKind = payload.phase() == ShockwavePayload.Phase.MASK_ON ? 1
					: payload.phase() == ShockwavePayload.Phase.CRYSTAL_ON ? 2
					: payload.phase() == ShockwavePayload.Phase.BULWARK_ON ? 3 : 0;
			targetId = payload.targetId(); aim = payload.aim();
			var random = new java.util.Random(((long) sourceId << 20) ^ targetId ^ (level == null ? 0 : level.getGameTime()));
			for (int i = 0; i < DarkBoltAnimation.FRAGMENTS; i++) {
				double y = random.nextDouble() * 2 - 1, r = Math.sqrt(1 - y * y), a = random.nextDouble() * Math.PI * 2;
				dx[i] = (float) (Math.cos(a) * r); dy[i] = (float) y; dz[i] = (float) (Math.sin(a) * r);
				speed[i] = 0.7f + random.nextFloat() * 0.9f;
			}
			lightning = quake ? new GroundLightning(level, origin, yaw,
					((long) sourceId << 32) ^ Double.doubleToLongBits(origin.x + origin.z) ^ level.getGameTime()) : null;
			for (int i = 0; i < EnergyMotes.COUNT; i++) {
				ey[i] = EnergyMotes.directionY(i);
				double r = Math.sqrt(1 - ey[i] * ey[i]);
				double angle = EnergyMotes.angle(i, sourceId);
				ex[i] = Math.cos(angle) * r; ez[i] = Math.sin(angle) * r;
			}
		}
	}
}
