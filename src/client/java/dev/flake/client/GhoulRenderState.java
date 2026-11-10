package dev.flake.client;

import dev.flake.duel.GhoulAnimation;
import dev.flake.duel.ShockwaveAnimation;
import dev.flake.duel.ShockwavePayload;
import dev.flake.duel.VfxTimeline;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Bounded procedural blood rig in the existing depth-aware post pass. No new render state or GPU buffers. */
final class GhoulRenderState {
	private GhoulRenderState() {}
	static boolean handles(ShockwavePayload.Phase phase) {
		return switch (phase) {
			case BLOOD_LUNGE, BLOODLUST_ON, BLOODLUST_OFF, BLOOD_SIPHON -> true;
			default -> false;
		};
	}

	static final class Actor {
		private final int sourceId;
		private final VfxTimeline clock = new VfxTimeline();
		private final GhoulAnimation.Trail trail = new GhoulAnimation.Trail();
		private final double[] sampled = new double[4];
		private final Siphon[] siphons = {new Siphon(), new Siphon()};
		private final float[] minimum = new float[24], maximum = new float[24], widths = new float[8];
		private Vec3 body, previous, velocity = Vec3.ZERO, lag;
		private Vec3 contact = Vec3.ZERO;
		private float auraAt = -100, offAt = -100, lungeAt = -100, previousAge, frozenTrailAt;
		private double yaw, lungeYaw;
		private boolean confirmed;
		private float[] u;
		private int wave, material, quality;
		private float age;
		private Vec3 camera, right, forward;
		private float height = 1.8f;

		Actor(ShockwavePayload payload) {
			sourceId = payload.sourceId();
			body = payload.phase() == ShockwavePayload.Phase.BLOOD_SIPHON ? payload.aim().add(0, -1, 0) : payload.origin();
			previous = lag = body;
			receive(payload);
		}

		void receive(ShockwavePayload payload) {
			float now = clock.sample(System.nanoTime(), Minecraft.getInstance().isPaused());
			switch (payload.phase()) {
				case BLOODLUST_ON -> { auraAt = now; offAt = now + GhoulAnimation.BUFF_SECONDS; }
				case BLOODLUST_OFF -> offAt = Math.min(offAt, now);
				case BLOOD_LUNGE -> {
					lungeAt = frozenTrailAt = now; lungeYaw = payload.yaw();
					contact = payload.aim(); confirmed = payload.targetId() >= 0;
					trail.reset(now, payload.origin().x, payload.origin().y, payload.origin().z);
				}
				case BLOOD_SIPHON -> {
					Siphon slot = siphons[0].at <= siphons[1].at ? siphons[0] : siphons[1];
					slot.at = now; slot.origin = payload.origin();
					slot.duration = GhoulAnimation.siphonDuration(payload.origin().distanceTo(payload.aim()));
					slot.strength = (float) Math.clamp(Math.sqrt(Math.max(0, payload.yaw())) * 0.65, 0.35, 1.15);
				}
				default -> {}
			}
		}

		/** Returns true only after every animation tail has expired. All coordinates stay double-precision until upload. */
		boolean write(float[] values, int index, long nanos, Minecraft client, Vec3 cameraPos, float partial, int detail) {
			age = clock.sample(nanos, client.isPaused());
			var entity = client.level.getEntity(sourceId);
			if (entity == null || !entity.isAlive()) offAt = Math.min(offAt, age);
			if (age >= offAt + GhoulAnimation.EXIT && age >= lungeAt + GhoulAnimation.LUNGE_SECONDS
					&& age >= siphons[0].at + siphons[0].duration + 0.28f && age >= siphons[1].at + siphons[1].duration + 0.28f) return true;
			u = values; wave = index; camera = cameraPos; quality = detail;
			material = ShockwaveRenderState.MATERIAL_OFFSET + index * 128;
			float dt = age - previousAge;
			if (entity != null) {
				body = entity.getPosition(partial); height = entity.getBbHeight();
				if (dt > 0 && dt < 0.3 && body.distanceToSqr(previous) < 36) {
					velocity = velocity.lerp(body.subtract(previous).scale(1 / dt), -Math.expm1(-dt / 0.07));
					lag = lag.lerp(body, -Math.expm1(-dt / 0.055));
				} else if (dt >= 0.3 || body.distanceToSqr(previous) >= 36) { velocity = Vec3.ZERO; lag = body; }
				double targetYaw = Math.toRadians(entity instanceof LivingEntity living ? living.getPreciseBodyRotation(partial) : entity.getYRot());
				yaw += Math.atan2(Math.sin(targetYaw - yaw), Math.cos(targetYaw - yaw)) * -Math.expm1(-Math.max(0, dt) / 0.065);
			}
			previous = body; previousAge = age;
			if (age - lungeAt < 0.36f && entity != null) { trail.update(age, body.x, body.y, body.z); frozenTrailAt = age; }
			boolean near = body.distanceToSqr(camera) < 56 * 56 || contact.distanceToSqr(camera) < 56 * 56;
			for (var siphon : siphons) if (age < siphon.at + siphon.duration + 0.28f && siphon.origin.distanceToSqr(camera) < 56 * 56) near = true;
			if (!near) return false;
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
			forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
			Arrays.fill(minimum, Float.POSITIVE_INFINITY); Arrays.fill(maximum, Float.NEGATIVE_INFINITY); Arrays.fill(widths, 0);
			int style = 36 + ShockwaveAnimation.MAX_WAVES * 4 + index * 4;
			u[style] = 1; u[style + 1] = age; u[style + 2] = 12;
			point(material, body, height);
			u[material + 4] = (float) velocity.x; u[material + 5] = (float) velocity.y; u[material + 6] = (float) velocity.z;
			u[material + 7] = (float) Math.min(1, velocity.length() / 8);
			float auraAge = age - auraAt, lungeAge = age - lungeAt;
			float aura = GhoulAnimation.aura(auraAge, offAt - auraAt);
			float release = GhoulAnimation.smooth(offAt, offAt + GhoulAnimation.EXIT, age);
			float swing = entity instanceof LivingEntity living ? living.getSwingAnimation(partial) : 0;
			float absorbed = 0;
			for (var siphon : siphons) absorbed += GhoulAnimation.absorption(age - siphon.at, siphon.duration) * siphon.strength;
			u[material + 8] = auraAge; u[material + 9] = release; u[material + 10] = aura; u[material + 11] = swing;
			u[material + 12] = lungeAge; u[material + 13] = confirmed ? 1 : 0;
			u[material + 14] = confirmed ? GhoulAnimation.flash(lungeAge) : 0;
			u[material + 15] = GhoulAnimation.compression(lungeAge);
			point(material + 16, contact, confirmed ? GhoulAnimation.flash(lungeAge) : 0);
			boolean inside = client.getCameraEntity() == client.player && client.player != null && client.player.getId() == sourceId && client.options.getCameraType().isFirstPerson();
			u[material + 36] = detail; u[material + 37] = sourceId * 0.73f; u[material + 38] = inside ? 1 : 0;
			u[material + 39] = client.options.screenEffectScale().get().floatValue();
			float compression = GhoulAnimation.compression(lungeAge);
			if (aura > 0.001 || compression > 0.001) drawAura(auraAge, aura, release, swing, absorbed, compression);
			if (lungeAge >= 0 && lungeAge < GhoulAnimation.LUNGE_SECONDS) {
				drawTrail(lungeAge);
				if (confirmed) drawImpact(lungeAge);
			}
			for (int i = 0; i < siphons.length; i++) drawSiphon(siphons[i], i);
			for (int group = 0; group < 8; group++) {
				if (widths[group] == 0) continue;
				int entry = ShockwaveRenderState.BOUNDS_OFFSET + index * 32 + group * 4, b = group * 3;
				float x = (minimum[b] + maximum[b]) * 0.5f, y = (minimum[b + 1] + maximum[b + 1]) * 0.5f, z = (minimum[b + 2] + maximum[b + 2]) * 0.5f;
				u[entry] = x; u[entry + 1] = y; u[entry + 2] = z;
				float dx = maximum[b] - x, dy = maximum[b + 1] - y, dz = maximum[b + 2] - z;
				u[entry + 3] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + widths[group] * 3.5f;
			}
			return false;
		}

		private void drawAura(float localAge, float aura, float release, float swing, float absorbed, float compression) {
			Vec3 inertia = lag.subtract(body);
			if (inertia.lengthSqr() > 0.16) inertia = inertia.normalize().scale(0.4);
			float attack = (float) Math.sin(Math.PI * Math.clamp(swing, 0, 1));
			float motionAge = age - Math.max(0, age - offAt) * release * 0.85f;
			for (int tendon = 0; tendon < 2; tendon++) {
				double seed = tendon * 2.17 + sourceId * 0.27, wing = tendon % 2 == 0 ? -1 : 1;
				float rise = GhoulAnimation.smooth(tendon * 0.026f, 0.28f + tendon * 0.026f, localAge);
				double breathe = Math.sin(motionAge * 3.1 + seed) * 0.04 + Math.sin(motionAge * 6.3 - seed) * 0.014;
				double reach = (0.48 + attack * 0.20 + breathe) * (1 - release * 0.65);
				Vec3 root = local(wing * 0.24, height * 0.32, tendon < 2 ? -0.12 : 0.18);
				Vec3 controlA = local(wing * (reach + 0.12), height * 0.60, -0.22 + Math.sin(seed + age * 1.8) * 0.13).add(inertia.scale(0.6));
				Vec3 controlB = local(wing * (reach + 0.04), height * (0.95 + breathe), 0.13 + attack * 0.18).add(inertia);
				Vec3 tip = local(wing * (0.31 + attack * 0.22), height * (0.70 + breathe), 0.32 + attack * 0.35).add(inertia.scale(1.4));
				float intensity = Math.max(aura * rise, compression * 0.8f) * (1 + absorbed * 0.65f);
				if (localAge >= 0 && localAge < 0.40f) {
					Vec3 low = local(wing * 0.25, 0.08, 0.05);
					root = low.lerp(root, rise); controlA = low.lerp(controlA, rise); controlB = low.lerp(controlB, rise); tip = low.lerp(tip, rise);
				}
				if (compression > 0 && aura < 0.01) {
					double collapse = 1 - GhoulAnimation.smooth(0, 0.085f, age - lungeAt) * 0.52;
					Vec3 centre = body.add(0, height * 0.55, 0);
					root = centre.lerp(root, collapse); controlA = centre.lerp(controlA, collapse);
					controlB = centre.lerp(controlB, collapse); tip = centre.lerp(tip, collapse).add(new Vec3(Math.cos(lungeYaw), 0, Math.sin(lungeYaw)).scale(0.18));
				}
				Vec3 last = root;
				for (int j = 1; j <= 8; j++) {
					double t = j / 8.0;
					Vec3 p = bezier(root, controlA, controlB, tip, t)
							.add(right.scale(Math.sin(t * 8 + seed - motionAge * 2) * Math.sin(t * Math.PI) * 0.035));
					float width = (float) ((0.12 + 0.075 * Math.sin(t * Math.PI)) * (1 - t * 0.67) * (1 - release * 0.6));
					segment(tendon * 8 + j - 1, last, p, width * (1 + attack * 0.25f), intensity); last = p;
				}
				// Smaller, sharper secondary curls follow the broad shoulder tendons, with their own phase/lag.
				Vec3 clawRoot = local(wing * 0.38, height * 0.73, -0.06).add(inertia.scale(0.45));
				Vec3 clawA = local(wing * (0.72 + breathe), height * 0.91, 0.08).add(inertia.scale(0.8));
				Vec3 clawB = local(wing * (0.78 + attack * 0.12), height * (0.74 + breathe), 0.46).add(inertia);
				Vec3 clawTip = local(wing * 0.45, height * 0.56, 0.30 + attack * 0.28).add(inertia.scale(1.3));
				last = clawRoot;
				for (int j = 1; j <= 4; j++) {
					Vec3 p = bezier(clawRoot, clawA, clawB, clawTip, j / 4.0);
					segment(56 + tendon * 4 + j - 1, last, p, (0.07f - j * 0.011f) * (1 - release * 0.7f), intensity * 0.55f);
					last = p;
				}
			}
		}

		private void drawTrail(float localAge) {
			double headTime = Math.min(age, frozenTrailAt), phase = sourceId * 0.37;
			Vec3 dir = new Vec3(Math.cos(lungeYaw), 0, Math.sin(lungeYaw));
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			for (int ribbon = 0; ribbon < (quality == 0 ? 2 : 3); ribbon++) {
				Vec3 last = null;
				Vec3 lastRaw = null;
				for (int j = 0; j <= 8; j++) {
					double at = headTime - j * 0.035;
					trail.sample(at, sampled);
					double angle = ribbon * 2.0944 + j * 0.27 + phase + Math.sin(sampled[3] * 14 + ribbon) * 0.16;
					double radius = (0.21 + ribbon * 0.06) * (1 - j / 12.0) * (1 - GhoulAnimation.smooth(0.38f, 0.82f, localAge) * 0.7);
					Vec3 raw = new Vec3(sampled[0], sampled[1], sampled[2]);
					Vec3 p = raw.add(0, height * (0.46 + ribbon * 0.07), 0)
							.add(side.scale(Math.cos(angle) * radius)).add(0, Math.sin(angle) * radius * 0.8, 0);
					if (last != null) {
						float movement = (float) Math.clamp(lastRaw.distanceTo(raw) * 3.5, 0, 1);
						// A stationary cast cannot manufacture a long trail. Stored segment positions survive the dash.
						float alpha = (1 - GhoulAnimation.smooth(0.08f, 0.50f, (float) (age - sampled[3])))
								* (1 - GhoulAnimation.smooth(0.40f, 0.82f, localAge)) * movement;
						float width = (float) ((0.13 + ribbon * 0.012) * Math.pow(1 - (j - 0.5) / 9.0, 0.65));
						segment(16 + ribbon * 8 + j - 1, last, p, width, alpha);
					}
					last = p;
					lastRaw = raw;
				}
			}
		}

		private void drawImpact(float localAge) {
			float t = localAge - GhoulAnimation.CONTACT_DELAY;
			if (t < 0 || t > 0.35f) return;
			Vec3 dir = new Vec3(Math.cos(lungeYaw), 0, Math.sin(lungeYaw));
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			double open = 1 - Math.exp(-t * 24), r = 0.18 + open * 0.52;
			for (int cut = 0; cut < 2; cut++) {
				Vec3 last = null;
				for (int j = 0; j <= 4; j++) {
					double arc = -1.2 + j * 0.68 * Math.min(1, t / 0.045) + cut * 2.6 + t * (cut == 0 ? 4 : -3);
					Vec3 p = contact.add(side.scale(Math.cos(arc) * r)).add(0, Math.sin(arc) * r * (cut == 0 ? 0.78 : 1.15), 0)
							.add(dir.scale(Math.sin(arc * 1.5 + cut) * 0.15 + t * 0.7));
					if (last != null) segment(56 + cut * 4 + j - 1, last, p, 0.075f * (1 - j / 6f),
							(1 - GhoulAnimation.smooth(0.09f, 0.35f, t)) * (cut == 0 ? 1 : 0.72f));
					last = p;
				}
			}
		}

		private void drawSiphon(Siphon siphon, int slot) {
			float elapsed = age - siphon.at, duration = siphon.duration;
			if (elapsed < 0 || elapsed > duration + 0.28f) return;
			Vec3 receiver = body.add(0, height * 0.58, 0);
			Vec3 incoming = siphon.origin.subtract(receiver);
			if (incoming.lengthSqr() > 0.0001) receiver = receiver.add(incoming.normalize().scale(0.33));
			float extraction = (1 - GhoulAnimation.smooth(0.035f, 0.15f, elapsed)) * siphon.strength;
			point(material + 20 + slot * 4, siphon.origin, extraction);
			point(material + 28 + slot * 4, receiver, GhoulAnimation.absorption(elapsed, duration) * siphon.strength);
			Vec3 delta = receiver.subtract(siphon.origin);
			Vec3 bend = delta.cross(new Vec3(0, 1, 0));
			bend = bend.lengthSqr() > 0.0001 ? bend.normalize() : right;
			double span = delta.length();
			for (int strand = 0; strand < (quality == 0 ? 1 : 2); strand++) {
				float strandAge = elapsed - strand * 0.022f;
				if (strandAge <= 0) continue;
				double head = Math.pow(Math.clamp(strandAge / duration, 0, 1), 1.65);
				double tail = Math.pow(Math.clamp((strandAge - 0.085) / duration, 0, 1), 1.65);
				if (head - tail < 0.001) continue;
				Vec3 c1 = siphon.origin.add(delta.scale(0.27)).add(bend.scale((strand == 0 ? 1 : -1) * (0.25 + span * 0.09))).add(0, 0.20 + span * 0.06, 0);
				Vec3 c2 = receiver.subtract(delta.scale(0.27)).add(bend.scale((strand == 0 ? -1 : 1) * (0.18 + span * 0.05))).add(0, 0.12, 0);
				Vec3 last = null;
				for (int j = 0; j <= 4; j++) {
					double t = tail + (head - tail) * j / 4.0;
					Vec3 p = bezier(siphon.origin, c1, c2, receiver, t)
							.add(bend.scale(Math.sin(t * 10 + strand * 2 + age * 9) * Math.sin(t * Math.PI) * 0.045));
					if (last != null) segment(40 + slot * 8 + strand * 4 + j - 1, last, p,
							(0.065f + strand * 0.023f) * (0.6f + j / 8f), siphon.strength * (1 - GhoulAnimation.smooth(duration, duration + 0.11f, strandAge)));
					last = p;
				}
			}
		}

		private Vec3 local(double x, double y, double z) { return body.add(right.scale(x)).add(0, y, 0).add(forward.scale(z)); }
		private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
			double v = 1 - t;
			return a.scale(v * v * v).add(b.scale(3 * v * v * t)).add(c.scale(3 * v * t * t)).add(d.scale(t * t * t));
		}
		private void point(int offset, Vec3 p, float w) {
			u[offset] = (float) (p.x - camera.x); u[offset + 1] = (float) (p.y - camera.y); u[offset + 2] = (float) (p.z - camera.z); u[offset + 3] = w;
		}
		private void segment(int local, Vec3 a, Vec3 b, float width, float alpha) {
			if (alpha < 0.001 || width < 0.001 || a.distanceToSqr(b) < 0.00002) return;
			int offset = ShockwaveRenderState.SEGMENT_OFFSET + (wave * 64 + local) * 4;
			point(offset, a, width); point(offset + ShockwaveRenderState.SEGMENTS * 4, b, alpha);
			int group = local / 8, g = group * 3;
			for (int axis = 0; axis < 3; axis++) {
				float x = u[offset + axis], y = u[offset + ShockwaveRenderState.SEGMENTS * 4 + axis];
				minimum[g + axis] = Math.min(minimum[g + axis], Math.min(x, y)); maximum[g + axis] = Math.max(maximum[g + axis], Math.max(x, y));
			}
			widths[group] = Math.max(widths[group], width);
		}
	}

	private static final class Siphon {
		float at = -100, duration = 0.2f, strength;
		Vec3 origin = Vec3.ZERO;
	}
}
