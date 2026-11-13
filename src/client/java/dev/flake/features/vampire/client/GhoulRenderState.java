package dev.flake.features.vampire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.features.vampire.GhoulAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.VfxTimeline;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Hybrid mesh-based blood rig rendered in world space, with only a lightweight 2D edge pulse in post. */
public final class GhoulRenderState {
	private GhoulRenderState() {}
	public static boolean handles(CombatVfxPayload.Phase phase) {
		return switch (phase) {
			case BLOOD_LUNGE, BLOODLUST_ON, BLOODLUST_OFF, BLOOD_SIPHON -> true;
			default -> false;
		};
	}

	public static final class Actor implements EffectActor {
		private final int sourceId;
		private final VfxTimeline clock = new VfxTimeline();
		private final GhoulAnimation.Trail trail = new GhoulAnimation.Trail();
		private final double[] sampled = new double[4];
		private final Siphon[] siphons = {new Siphon(), new Siphon()};
		private final float[] segA = new float[64 * 4], segB = new float[64 * 4];
		private final float[] knotExt = new float[2 * 4], knotIng = new float[2 * 4];
		private double bodyX, bodyY, bodyZ;
		private double prevX, prevY, prevZ;
		private double velX, velY, velZ;
		private double lagX, lagY, lagZ;
		private double contactX, contactY, contactZ;
		private double launchX, launchY, launchZ;
		private float auraAt = -100, offAt = -100, lungeAt = -100, previousAge, frozenTrailAt;
		private double yaw, lungeYaw;
		private boolean confirmed;
		private float[] u;
		private int material, quality;
		private SlotWriter slot;
		private float age;
		private double camX, camY, camZ;
		private double rightX, rightZ, forwardX, forwardZ;
		private float height = 1.8f;
		private boolean meshVisible, meshInside;
		private float meshAuraAge, meshAura, meshSwing, meshLungeAge, meshLungeFlash, meshLungeCompression, meshFeeding, meshHeartbeat;

		public Actor(CombatVfxPayload payload) {
			sourceId = payload.sourceId();
			Vec3 init = payload.phase() == CombatVfxPayload.Phase.BLOOD_SIPHON ? payload.aim().add(0, -1, 0) : payload.origin();
			bodyX = prevX = lagX = launchX = init.x;
			bodyY = prevY = lagY = launchY = init.y;
			bodyZ = prevZ = lagZ = launchZ = init.z;
			receive(payload);
		}

		@Override public int sourceId() { return sourceId; }

		@Override public boolean receive(CombatVfxPayload payload) {
			if (payload.sourceId() != sourceId || !handles(payload.phase())) return false;
			float now = clock.sample(System.nanoTime(), Minecraft.getInstance().isPaused());
			switch (payload.phase()) {
				case BLOODLUST_ON -> { auraAt = now; offAt = now + GhoulAnimation.BUFF_SECONDS; }
				case BLOODLUST_OFF -> offAt = Math.min(offAt, now);
				case BLOOD_LUNGE -> {
					lungeAt = frozenTrailAt = now; lungeYaw = payload.yaw();
					Vec3 orig = payload.origin();
					launchX = orig.x; launchY = orig.y; launchZ = orig.z;
					Vec3 aim = payload.aim();
					contactX = aim.x; contactY = aim.y; contactZ = aim.z;
					confirmed = payload.targetId() >= 0;
					trail.reset(now, orig.x, orig.y, orig.z);
				}
				case BLOOD_SIPHON -> {
					Siphon slot = siphons[0].at <= siphons[1].at ? siphons[0] : siphons[1];
					slot.at = now;
					Vec3 orig = payload.origin();
					slot.ox = orig.x; slot.oy = orig.y; slot.oz = orig.z;
					slot.duration = GhoulAnimation.siphonDuration(orig.distanceTo(payload.aim()));
					slot.strength = (float) Math.clamp(Math.sqrt(Math.max(0, payload.yaw())) * 0.65, 0.35, 1.15);
				}
				default -> {}
			}
			return true;
		}

		@Override public boolean write(RenderFrame frame, SlotWriter slot) {
			this.slot = slot;
			return write(slot.values, frame.nanos, frame.client, frame.camera, frame.partial, frame.quality);
		}

		/** Returns true only after every animation tail has expired. All coordinates stay double-precision until upload. */
		private boolean write(float[] values, long nanos, Minecraft client, Vec3 cameraPos, float partial, int detail) {
			age = clock.sample(nanos, client.isPaused());
			var entity = client.level.getEntity(sourceId);
			if (entity == null || !entity.isAlive()) offAt = Math.min(offAt, age);
			if (age >= offAt + GhoulAnimation.EXIT && age >= lungeAt + GhoulAnimation.LUNGE_SECONDS
					&& age >= siphons[0].at + siphons[0].duration + 0.28f && age >= siphons[1].at + siphons[1].duration + 0.28f) return true;
			u = values;
			camX = cameraPos.x; camY = cameraPos.y; camZ = cameraPos.z;
			quality = detail;
			material = slot.material;
			float dt = age - previousAge;
			if (entity != null) {
				Vec3 pos = entity.getPosition(partial);
				bodyX = pos.x; bodyY = pos.y; bodyZ = pos.z;
				height = entity.getBbHeight();
				double dx = bodyX - prevX, dy = bodyY - prevY, dz = bodyZ - prevZ;
				double distSq = dx * dx + dy * dy + dz * dz;
				if (dt > 0 && dt < 0.3 && distSq < 36) {
					double kVel = -Math.expm1(-dt / 0.07);
					velX += (dx / dt - velX) * kVel;
					velY += (dy / dt - velY) * kVel;
					velZ += (dz / dt - velZ) * kVel;
					double kLag = -Math.expm1(-dt / 0.055);
					lagX += (bodyX - lagX) * kLag;
					lagY += (bodyY - lagY) * kLag;
					lagZ += (bodyZ - lagZ) * kLag;
				} else if (dt >= 0.3 || distSq >= 36) {
					velX = velY = velZ = 0;
					lagX = bodyX; lagY = bodyY; lagZ = bodyZ;
				}
				double targetYaw = Math.toRadians(entity instanceof LivingEntity living ? living.getPreciseBodyRotation(partial) : entity.getYRot());
				yaw += Math.atan2(Math.sin(targetYaw - yaw), Math.cos(targetYaw - yaw)) * -Math.expm1(-Math.max(0, dt) / 0.065);
			}
			prevX = bodyX; prevY = bodyY; prevZ = bodyZ; previousAge = age;
			if (age - lungeAt < 0.36f && entity != null) { trail.update(age, bodyX, bodyY, bodyZ); frozenTrailAt = age; }
			boolean near = sqDist(bodyX, bodyY, bodyZ, camX, camY, camZ) < 56 * 56
					|| sqDist(contactX, contactY, contactZ, camX, camY, camZ) < 56 * 56;
			for (var siphon : siphons) {
				if (age < siphon.at + siphon.duration + 0.28f && sqDist(siphon.ox, siphon.oy, siphon.oz, camX, camY, camZ) < 56 * 56) near = true;
			}
			if (!near) { meshVisible = false; return false; }
			rightX = Math.cos(yaw); rightZ = Math.sin(yaw);
			forwardX = -rightZ; forwardZ = rightX;
			Arrays.fill(segA, 0); Arrays.fill(segB, 0);
			Arrays.fill(knotExt, 0); Arrays.fill(knotIng, 0);

			float auraAge = age - auraAt, lungeAge = age - lungeAt;
			float aura = GhoulAnimation.aura(auraAge, offAt - auraAt);
			float release = GhoulAnimation.smooth(offAt, offAt + GhoulAnimation.EXIT, age);
			float swing = entity instanceof LivingEntity living ? living.getSwingAnimation(partial) : 0;
			float absorbed = 0;
			for (var siphon : siphons) absorbed += GhoulAnimation.absorption(age - siphon.at, siphon.duration) * siphon.strength;
			float compression = GhoulAnimation.compression(lungeAge);
			float flash = confirmed ? GhoulAnimation.flash(lungeAge) : 0;
			float burst = GhoulAnimation.burst(auraAge) + Math.min(0.85f, absorbed * 0.75f) + flash * 0.55f;
			boolean inside = client.getCameraEntity() == client.player && client.player != null
					&& client.player.getId() == sourceId && client.options.getCameraType().isFirstPerson();
			float heartbeat = GhoulAnimation.heartbeat(age);

			meshVisible = true;
			meshInside = inside;
			meshAuraAge = auraAge;
			meshAura = aura;
			meshSwing = swing;
			meshLungeAge = lungeAge;
			meshLungeFlash = flash;
			meshLungeCompression = compression;
			meshFeeding = Math.min(1f, absorbed);
			meshHeartbeat = heartbeat;

			// Post-pass only runs the cheap 2D screen-edge vignette when in 1st person.
			int style = slot.style;
			u[style] = 1;
			u[style + 1] = age;
			u[style + 2] = 12;
			u[style + 3] = inside ? Math.max(aura, Math.max(compression * 0.8f, Math.min(1f, absorbed))) : 0f;
			u[material + 39] = client.options.screenEffectScale().get().floatValue();
			u[material + 40] = burst;
			u[material + 41] = heartbeat;

			if (aura > 0.001 || compression > 0.001) drawAura(auraAge, aura, release, swing, absorbed, compression);
			if (lungeAge >= 0 && lungeAge < GhoulAnimation.LUNGE_SECONDS) {
				drawTrail(lungeAge);
				if (confirmed) drawImpact(lungeAge);
			}
			for (int i = 0; i < siphons.length; i++) drawSiphon(siphons[i], i);
			return false;
		}

		private void drawAura(float localAge, float aura, float release, float swing, float absorbed, float compression) {
			double ix = lagX - bodyX, iy = lagY - bodyY, iz = lagZ - bodyZ;
			double ilenSq = ix * ix + iy * iy + iz * iz;
			if (ilenSq > 0.16) {
				double scale = 0.4 / Math.sqrt(ilenSq);
				ix *= scale; iy *= scale; iz *= scale;
			}
			float attack = (float) Math.sin(Math.PI * Math.clamp(swing, 0, 1));
			float motionAge = age - Math.max(0, age - offAt) * release * 0.85f;
			for (int tendon = 0; tendon < 2; tendon++) {
				double seed = tendon * 2.17 + sourceId * 0.27, wing = tendon % 2 == 0 ? -1 : 1;
				float rise = GhoulAnimation.smooth(tendon * 0.026f, 0.26f + tendon * 0.026f, localAge);
				double breathe = Math.sin(motionAge * 3.4 + seed) * 0.05 + Math.sin(motionAge * 6.8 - seed) * 0.018;
				double reach = (0.54 + attack * 0.26 + breathe + absorbed * 0.12) * (1 - release * 0.65);

				double rootX = localX(wing * 0.25, -0.14), rootY = bodyY + height * 0.34, rootZ = localZ(wing * 0.25, -0.14);
				double cAx = localX(wing * (reach + 0.16), -0.24 + Math.sin(seed + age * 2.1) * 0.15) + ix * 0.6;
				double cAy = bodyY + height * 0.62 + iy * 0.6;
				double cAz = localZ(wing * (reach + 0.16), -0.24 + Math.sin(seed + age * 2.1) * 0.15) + iz * 0.6;

				double cBx = localX(wing * (reach + 0.08), 0.15 + attack * 0.22) + ix;
				double cBy = bodyY + height * (0.98 + breathe) + iy;
				double cBz = localZ(wing * (reach + 0.08), 0.15 + attack * 0.22) + iz;

				double tipX = localX(wing * (0.35 + attack * 0.28), 0.36 + attack * 0.40) + ix * 1.4;
				double tipY = bodyY + height * (0.74 + breathe) + iy * 1.4;
				double tipZ = localZ(wing * (0.35 + attack * 0.28), 0.36 + attack * 0.40) + iz * 1.4;

				float intensity = Math.max(aura * rise, compression * 0.9f) * (1.15f + absorbed * 0.85f);
				if (localAge >= 0 && localAge < 0.38f) {
					double lowX = localX(wing * 0.25, 0.05), lowY = bodyY + 0.08, lowZ = localZ(wing * 0.25, 0.05);
					rootX = lerp(lowX, rootX, rise); rootY = lerp(lowY, rootY, rise); rootZ = lerp(lowZ, rootZ, rise);
					cAx = lerp(lowX, cAx, rise); cAy = lerp(lowY, cAy, rise); cAz = lerp(lowZ, cAz, rise);
					cBx = lerp(lowX, cBx, rise); cBy = lerp(lowY, cBy, rise); cBz = lerp(lowZ, cBz, rise);
					tipX = lerp(lowX, tipX, rise); tipY = lerp(lowY, tipY, rise); tipZ = lerp(lowZ, tipZ, rise);
				}
				if (compression > 0 && aura < 0.01) {
					double collapse = 1 - GhoulAnimation.smooth(0, 0.085f, age - lungeAt) * 0.52;
					double cx = bodyX, cy = bodyY + height * 0.55, cz = bodyZ;
					rootX = lerp(cx, rootX, collapse); rootY = lerp(cy, rootY, collapse); rootZ = lerp(cz, rootZ, collapse);
					cAx = lerp(cx, cAx, collapse); cAy = lerp(cy, cAy, collapse); cAz = lerp(cz, cAz, collapse);
					cBx = lerp(cx, cBx, collapse); cBy = lerp(cy, cBy, collapse); cBz = lerp(cz, cBz, collapse);
					tipX = lerp(cx, tipX, collapse) + Math.cos(lungeYaw) * 0.22;
					tipY = lerp(cy, tipY, collapse);
					tipZ = lerp(cz, tipZ, collapse) + Math.sin(lungeYaw) * 0.22;
				}
				double lastX = rootX, lastY = rootY, lastZ = rootZ;
				for (int j = 1; j <= 8; j++) {
					double t = j / 8.0;
					double waveOff = Math.sin(t * 8 + seed - motionAge * 2.4) * Math.sin(t * Math.PI) * 0.042;
					double px = bezier(rootX, cAx, cBx, tipX, t) + rightX * waveOff;
					double py = bezier(rootY, cAy, cBy, tipY, t);
					double pz = bezier(rootZ, cAz, cBz, tipZ, t) + rightZ * waveOff;
					float width = (float) ((0.145 + 0.085 * Math.sin(t * Math.PI)) * (1 - t * 0.62) * (1 - release * 0.6));
					segment(tendon * 8 + j - 1, lastX, lastY, lastZ, px, py, pz, width * (1 + attack * 0.30f), intensity);
					lastX = px; lastY = py; lastZ = pz;
				}
				// Secondary predatory blood-claws curling forward from the upper ribs
				double crX = localX(wing * 0.40, -0.05) + ix * 0.45, crY = bodyY + height * 0.74 + iy * 0.45, crZ = localZ(wing * 0.40, -0.05) + iz * 0.45;
				double caX = localX(wing * (0.78 + breathe), 0.10) + ix * 0.8, caY = bodyY + height * 0.93 + iy * 0.8, caZ = localZ(wing * (0.78 + breathe), 0.10) + iz * 0.8;
				double cbX = localX(wing * (0.84 + attack * 0.16), 0.50) + ix, cbY = bodyY + height * (0.75 + breathe) + iy, cbZ = localZ(wing * (0.84 + attack * 0.16), 0.50) + iz;
				double ctX = localX(wing * 0.46, 0.34 + attack * 0.34) + ix * 1.3, ctY = bodyY + height * 0.55 + iy * 1.3, ctZ = localZ(wing * 0.46, 0.34 + attack * 0.34) + iz * 1.3;
				lastX = crX; lastY = crY; lastZ = crZ;
				for (int j = 1; j <= 4; j++) {
					double t = j / 4.0;
					double px = bezier(crX, caX, cbX, ctX, t);
					double py = bezier(crY, caY, cbY, ctY, t);
					double pz = bezier(crZ, caZ, cbZ, ctZ, t);
					segment(56 + tendon * 4 + j - 1, lastX, lastY, lastZ, px, py, pz, (0.088f - j * 0.013f) * (1 - release * 0.7f), intensity * 0.75f);
					lastX = px; lastY = py; lastZ = pz;
				}
			}
		}

		private void drawTrail(float localAge) {
			double headTime = Math.min(age, frozenTrailAt), phase = sourceId * 0.37;
			double dirX = Math.cos(lungeYaw), dirZ = Math.sin(lungeYaw);
			double sideX = -dirZ, sideZ = dirX;
			int ribbons = quality == 0 ? 2 : 3;
			for (int ribbon = 0; ribbon < ribbons; ribbon++) {
				double lastX = 0, lastY = 0, lastZ = 0;
				double lastRawX = 0, lastRawY = 0, lastRawZ = 0;
				boolean hasLast = false;
				for (int j = 0; j <= 8; j++) {
					double at = headTime - j * 0.035;
					trail.sample(at, sampled);
					double angle = ribbon * 2.0944 + j * 0.31 + phase + Math.sin(sampled[3] * 15 + ribbon) * 0.20;
					double radius = (0.24 + ribbon * 0.07) * (1 - j / 11.5) * (1 - GhoulAnimation.smooth(0.38f, 0.82f, localAge) * 0.7);
					double rawX = sampled[0], rawY = sampled[1], rawZ = sampled[2];
					double cosA = Math.cos(angle) * radius;
					double px = rawX + sideX * cosA;
					double py = rawY + height * (0.46 + ribbon * 0.08) + Math.sin(angle) * radius * 0.85;
					double pz = rawZ + sideZ * cosA;
					if (hasLast) {
						double dRx = rawX - lastRawX, dRy = rawY - lastRawY, dRz = rawZ - lastRawZ;
						float movement = (float) Math.clamp(Math.sqrt(dRx * dRx + dRy * dRy + dRz * dRz) * 3.8, 0, 1);
						float alpha = (1 - GhoulAnimation.smooth(0.08f, 0.52f, (float) (age - sampled[3])))
								* (1 - GhoulAnimation.smooth(0.42f, 0.82f, localAge)) * movement * 1.15f;
						float width = (float) ((0.155 + ribbon * 0.016) * Math.pow(1 - (j - 0.5) / 9.0, 0.62));
						segment(16 + ribbon * 8 + j - 1, lastX, lastY, lastZ, px, py, pz, width, alpha);
					}
					lastX = px; lastY = py; lastZ = pz;
					lastRawX = rawX; lastRawY = rawY; lastRawZ = rawZ;
					hasLast = true;
				}
			}
		}

		private void drawImpact(float localAge) {
			float t = localAge - GhoulAnimation.CONTACT_DELAY;
			if (t < 0 || t > 0.38f) return;
			double dirX = Math.cos(lungeYaw), dirZ = Math.sin(lungeYaw);
			double sideX = -dirZ, sideZ = dirX;
			double open = 1 - Math.exp(-t * 25), r = 0.22 + open * 0.62;
			for (int cut = 0; cut < 2; cut++) {
				double lastX = 0, lastY = 0, lastZ = 0;
				boolean hasLast = false;
				for (int j = 0; j <= 4; j++) {
					double arc = -1.25 + j * 0.70 * Math.min(1, t / 0.045) + cut * 2.6 + t * (cut == 0 ? 4.2 : -3.2);
					double cosA = Math.cos(arc) * r;
					double fwdOff = Math.sin(arc * 1.5 + cut) * 0.16 + t * 0.72;
					double px = contactX + sideX * cosA + dirX * fwdOff;
					double py = contactY + Math.sin(arc) * r * (cut == 0 ? 0.82 : 1.18);
					double pz = contactZ + sideZ * cosA + dirZ * fwdOff;
					if (hasLast) {
						segment(56 + cut * 4 + j - 1, lastX, lastY, lastZ, px, py, pz, 0.095f * (1 - j / 6.2f),
								(1 - GhoulAnimation.smooth(0.10f, 0.38f, t)) * (cut == 0 ? 1.15f : 0.88f));
					}
					lastX = px; lastY = py; lastZ = pz;
					hasLast = true;
				}
			}
		}

		private void drawSiphon(Siphon siphon, int slot) {
			float elapsed = age - siphon.at, duration = siphon.duration;
			if (elapsed < 0 || elapsed > duration + 0.28f) return;
			double recX = bodyX, recY = bodyY + height * 0.58, recZ = bodyZ;
			double incX = siphon.ox - recX, incY = siphon.oy - recY, incZ = siphon.oz - recZ;
			double incLenSq = incX * incX + incY * incY + incZ * incZ;
			if (incLenSq > 0.0001) {
				double inv = 0.33 / Math.sqrt(incLenSq);
				recX += incX * inv; recY += incY * inv; recZ += incZ * inv;
			}
			float extraction = (1 - GhoulAnimation.smooth(0.035f, 0.16f, elapsed)) * siphon.strength;
			int kIdx = slot * 4;
			knotExt[kIdx] = (float) (siphon.ox - camX);
			knotExt[kIdx + 1] = (float) (siphon.oy - camY);
			knotExt[kIdx + 2] = (float) (siphon.oz - camZ);
			knotExt[kIdx + 3] = extraction;
			knotIng[kIdx] = (float) (recX - camX);
			knotIng[kIdx + 1] = (float) (recY - camY);
			knotIng[kIdx + 2] = (float) (recZ - camZ);
			knotIng[kIdx + 3] = GhoulAnimation.absorption(elapsed, duration) * siphon.strength;
			double dx = recX - siphon.ox, dy = recY - siphon.oy, dz = recZ - siphon.oz;
			double span = Math.sqrt(dx * dx + dy * dy + dz * dz);
			// Cross with (0, 1, 0) -> (-dz, 0, dx)
			double bendLen = Math.hypot(dx, dz);
			double bendX = bendLen > 0.0001 ? -dz / bendLen : rightX;
			double bendZ = bendLen > 0.0001 ? dx / bendLen : rightZ;
			int strands = quality == 0 ? 1 : 2;
			for (int strand = 0; strand < strands; strand++) {
				float strandAge = elapsed - strand * 0.022f;
				if (strandAge <= 0) continue;
				double head = Math.pow(Math.clamp(strandAge / duration, 0, 1), 1.65);
				double tail = Math.pow(Math.clamp((strandAge - 0.095) / duration, 0, 1), 1.65);
				if (head - tail < 0.001) continue;
				double sgn = strand == 0 ? 1 : -1;
				double b1 = sgn * (0.27 + span * 0.10);
				double b2 = -sgn * (0.20 + span * 0.06);
				double c1x = siphon.ox + dx * 0.27 + bendX * b1;
				double c1y = siphon.oy + dy * 0.27 + 0.22 + span * 0.065;
				double c1z = siphon.oz + dz * 0.27 + bendZ * b1;
				double c2x = recX - dx * 0.27 + bendX * b2;
				double c2y = recY - dy * 0.27 + 0.14;
				double c2z = recZ - dz * 0.27 + bendZ * b2;
				double lastX = 0, lastY = 0, lastZ = 0;
				boolean hasLast = false;
				for (int j = 0; j <= 4; j++) {
					double t = tail + (head - tail) * j / 4.0;
					double wobble = Math.sin(t * 10 + strand * 2 + age * 9.5) * Math.sin(t * Math.PI) * 0.05;
					double px = bezier(siphon.ox, c1x, c2x, recX, t) + bendX * wobble;
					double py = bezier(siphon.oy, c1y, c2y, recY, t);
					double pz = bezier(siphon.oz, c1z, c2z, recZ, t) + bendZ * wobble;
					if (hasLast) {
						segment(40 + slot * 8 + strand * 4 + j - 1, lastX, lastY, lastZ, px, py, pz,
								(0.082f + strand * 0.026f) * (0.65f + j / 8f),
								siphon.strength * 1.15f * (1 - GhoulAnimation.smooth(duration, duration + 0.12f, strandAge)));
					}
					lastX = px; lastY = py; lastZ = pz;
					hasLast = true;
				}
			}
		}

		private double localX(double x, double z) { return bodyX + rightX * x + forwardX * z; }
		private double localZ(double x, double z) { return bodyZ + rightZ * x + forwardZ * z; }
		private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
		private static double bezier(double a, double b, double c, double d, double t) {
			double v = 1 - t;
			return a * (v * v * v) + b * (3 * v * v * t) + c * (3 * v * t * t) + d * (t * t * t);
		}
		private static double sqDist(double x1, double y1, double z1, double x2, double y2, double z2) {
			double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
			return dx * dx + dy * dy + dz * dz;
		}
		private void point(int offset, double px, double py, double pz, float w) {
			u[offset] = (float) (px - camX); u[offset + 1] = (float) (py - camY); u[offset + 2] = (float) (pz - camZ); u[offset + 3] = w;
		}
		private void segment(int local, double ax, double ay, double az, double bx, double by, double bz, float width, float alpha) {
			double dx = ax - bx, dy = ay - by, dz = az - bz;
			if (alpha < 0.001 || width < 0.001 || dx * dx + dy * dy + dz * dz < 0.00002) return;
			int offset = local * 4;
			segA[offset] = (float) (ax - camX);
			segA[offset + 1] = (float) (ay - camY);
			segA[offset + 2] = (float) (az - camZ);
			segA[offset + 3] = width;
			segB[offset] = (float) (bx - camX);
			segB[offset + 1] = (float) (by - camY);
			segB[offset + 2] = (float) (bz - camZ);
			segB[offset + 3] = alpha;
		}

		@Override
		public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
			if (!meshVisible) return;
			collector.submitCustomGeometry(poseStack, GhoulMeshRenderer.GHOUL_RENDER_TYPE, (pose, vc) -> {
				float bx = (float) (bodyX - cameraPos.x);
				float by = (float) (bodyY - cameraPos.y);
				float bz = (float) (bodyZ - cameraPos.z);

				// 1. Living blood ribbons, predatory claws, lunge wake, and siphon strands
				for (int group = 0; group < 8; group++) {
					int kindIdx = group < 2 ? 0 : (group < 5 ? 1 : (group < 7 ? 2 : 3));
					if (group == 7 && !(confirmed && meshLungeAge >= 0.055f && meshLungeAge < 0.435f)) kindIdx = 0;
					float viewScale = kindIdx == 0 && meshInside ? 0.38f : 1.0f;
					float kind = kindIdx / 3.0f;
					int pathCount = group >= 5 ? 4 : 8;
					for (int j = 0; j < 8; j++) {
						int idx = (group * 8 + j) * 4;
						float wa = segA[idx + 3] * viewScale;
						float alpha = segB[idx + 3];
						if (wa <= 0.001f || alpha <= 0.001f) continue;
						float wb = wa * 0.88f;
						int node = j % pathCount;
						float u0 = (float) node / pathCount;
						float u1 = (float) (node + 1) / pathCount;
						float seed = ((group * 0.13f) % 1.0f);
						GhoulMeshRenderer.emitCrossRibbonSegment(pose, vc,
								segA[idx], segA[idx + 1], segA[idx + 2],
								segB[idx], segB[idx + 1], segB[idx + 2],
								wa, wb, u0, u1, kind, seed, meshHeartbeat, Math.min(1f, alpha));
					}
				}

				// 2. Arterial floor pulse ring + orbiting 3D blood droplets during Bloodlust
				float mistAlpha = Math.max(meshAura, meshLungeCompression * 0.65f) * (meshInside ? 0.35f : 1.0f);
				if (mistAlpha > 0.005f) {
					if (meshAuraAge >= 0f && meshAuraAge < 0.45f) {
						float ringEnv = (1f - GhoulAnimation.smooth(0.08f, 0.45f, meshAuraAge)) * mistAlpha;
						float rMid = 0.25f + meshAuraAge * 2.8f;
						GhoulMeshRenderer.emitFloorRing(pose, vc, bx, by + 0.04f, bz,
								Math.max(0.05f, rMid - 0.08f), rMid + 0.08f, 28,
								0.85f, 0.2f, meshHeartbeat, ringEnv * 0.85f);
					}
					int dropCount = quality == 0 ? 5 : (quality == 1 ? 8 : 10);
					for (int k = 0; k < dropCount; k++) {
						float fk = k;
						float h1 = (fk * 0.618034f + 0.17f) % 1.0f;
						float h2 = (fk * 0.381966f + 0.53f) % 1.0f;
						float speed = 0.72f + 0.42f * h1 + meshFeeding * 0.35f;
						float phase = (age * speed + fk / 10.0f) % 1.0f;
						float env = (float) Math.sin(phase * Math.PI);
						float ang = fk * 2.39996f + age * (1.35f + 0.8f * (h2 - 0.5f) + meshSwing * 1.2f) + phase * 1.5f;
						float rad = (meshInside ? 0.62f : 0.36f) + 0.24f * h2 + 0.06f * (float) Math.sin(phase * 6.28318 + fk);
						float dy = 0.10f + 0.25f * h1 + phase * (height * 0.90f + 0.25f);
						float sz = (0.028f + 0.018f * h1) * (0.45f + 0.55f * env) * (1.0f + meshFeeding * 0.25f);
						GhoulMeshRenderer.emitOctahedron(pose, vc,
								bx + (float) Math.cos(ang) * rad, by + dy, bz + (float) Math.sin(ang) * rad,
								sz, sz * 1.55f, sz, 0.9f, h1, meshHeartbeat, mistAlpha * env * 0.95f);
					}
				}

				// 3. Blood Siphon extraction & ingestion knots
				for (int i = 0; i < 2; i++) {
					int kIdx = i * 4;
					if (knotExt[kIdx + 3] > 0.005f) {
						GhoulMeshRenderer.emitOctahedron(pose, vc,
								knotExt[kIdx], knotExt[kIdx + 1], knotExt[kIdx + 2],
								0.12f, 0.16f, 0.11f, 0.75f, 0.45f, meshHeartbeat, Math.min(1f, knotExt[kIdx + 3]));
					}
					if (knotIng[kIdx + 3] > 0.005f) {
						GhoulMeshRenderer.emitOctahedron(pose, vc,
								knotIng[kIdx], knotIng[kIdx + 1], knotIng[kIdx + 2],
								0.16f, 0.25f, 0.15f, 0.75f, 0.35f, meshHeartbeat, Math.min(1f, knotIng[kIdx + 3]));
					}
				}
			});
		}
	}

	private static final class Siphon {
		float at = -100, duration = 0.2f, strength;
		double ox, oy, oz;
	}
}
