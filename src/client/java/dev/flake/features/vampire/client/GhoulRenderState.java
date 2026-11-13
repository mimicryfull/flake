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
	private static float localFovBoost;
	private static long localFovBoostNanos;

	private GhoulRenderState() {}

	public static float localBloodlustFovBoost() {
		if (localFovBoost <= 0.0001f) return 0f;
		if (System.nanoTime() - localFovBoostNanos > 150_000_000L) {
			localFovBoost = 0f;
			return 0f;
		}
		return localFovBoost;
	}

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
		private static final int MAX_PATHS = 16;
		private final float[] pathPts = new float[256 * 5];
		private final int[] pathStart = new int[MAX_PATHS], pathCount = new int[MAX_PATHS];
		private final float[] pathKind = new float[MAX_PATHS], pathSeed = new float[MAX_PATHS];
		private int activePaths, nextPt;
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
			activePaths = 0; nextPt = 0;
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

			boolean isLocal = client.player != null && client.player.getId() == sourceId;
			if (isLocal) {
				float scale = client.options.fovEffectScale().get().floatValue();
				localFovBoost = (aura * 0.075f + Math.max(compression * 0.045f, flash * 0.06f)) * scale;
				localFovBoostNanos = nanos;
			}

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
			for (int tendon = 0; tendon < (quality == 0 ? 2 : 3); tendon++) {
				double seed = tendon * 2.17 + sourceId * 0.27, wing = tendon % 2 == 0 ? -1 : 1;
				float rise = GhoulAnimation.smooth(tendon * 0.026f, 0.26f + tendon * 0.026f, localAge);
				double breathe = Math.sin(motionAge * 3.4 + seed) * 0.05 + Math.sin(motionAge * 6.8 - seed) * 0.018;
				double reach = (0.95 + attack * 0.40 + breathe * 2.0 + absorbed * 0.12) * (tendon == 2 ? 0.72 : 1.0) * (1 - release * 0.65);

				double rootX = localX(wing * 0.25, -0.14), rootY = bodyY + height * 0.34, rootZ = localZ(wing * 0.25, -0.14);
				double cAx = localX(wing * (reach + 0.16), -0.24 + Math.sin(seed + age * 2.1) * 0.15) + ix * 0.6;
				double cAy = bodyY + height * 0.62 + iy * 0.6;
				double cAz = localZ(wing * (reach + 0.16), -0.24 + Math.sin(seed + age * 2.1) * 0.15) + iz * 0.6;

				double cBx = localX(wing * (reach + 0.08), 0.15 + attack * 0.22) + ix;
				double cBy = bodyY + height * (0.98 + breathe) + iy;
				double cBz = localZ(wing * (reach + 0.08), 0.15 + attack * 0.22) + iz;

				double tipX = localX(wing * (0.60 + attack * 0.40), 0.50 + attack * 0.50) + ix * 1.4;
				double tipY = bodyY + height * (0.74 + breathe) + iy * 1.4;
				double tipZ = localZ(wing * (0.60 + attack * 0.40), 0.50 + attack * 0.50) + iz * 1.4;

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
				beginPath(0.15f, (float) (tendon * 0.37));
				for (int j = 0; j <= 20; j++) {
					double t = j / 20.0;
					double waveOff = Math.sin(t * 8 + seed - motionAge * 2.4) * Math.sin(t * Math.PI) * 0.042;
					double px = bezier(rootX, cAx, cBx, tipX, t) + rightX * waveOff;
					double py = bezier(rootY, cAy, cBy, tipY, t);
					double pz = bezier(rootZ, cAz, cBz, tipZ, t) + rightZ * waveOff;
					float taper = (float) Math.sin(t * Math.PI);
					float width = (float) ((0.11 + 0.11 * taper) * (1 - t * 0.45) * (1 - release * 0.6));
					pushPoint(px, py, pz, width * (1 + attack * 0.30f), intensity * Math.min(1f, 0.35f + taper * 0.85f));
				}
				// Secondary predatory blood-claws curling forward from the upper ribs
				double crX = localX(wing * 0.40, -0.05) + ix * 0.45, crY = bodyY + height * 0.74 + iy * 0.45, crZ = localZ(wing * 0.40, -0.05) + iz * 0.45;
				double caX = localX(wing * (1.25 + breathe), 0.10) + ix * 0.8, caY = bodyY + height * 0.93 + iy * 0.8, caZ = localZ(wing * (1.25 + breathe), 0.10) + iz * 0.8;
				double cbX = localX(wing * (1.30 + attack * 0.20), 0.50) + ix, cbY = bodyY + height * (0.75 + breathe) + iy, cbZ = localZ(wing * (1.30 + attack * 0.20), 0.50) + iz;
				double ctX = localX(wing * 0.70, 0.34 + attack * 0.34) + ix * 1.3, ctY = bodyY + height * 0.55 + iy * 1.3, ctZ = localZ(wing * 0.70, 0.34 + attack * 0.34) + iz * 1.3;
				beginPath(0.45f, (float) (tendon * 0.51 + 0.2));
				for (int j = 0; j <= 10; j++) {
					double t = j / 10.0;
					double px = bezier(crX, caX, cbX, ctX, t);
					double py = bezier(crY, caY, cbY, ctY, t);
					double pz = bezier(crZ, caZ, cbZ, ctZ, t);
					float width = (float) ((0.088 - t * 0.055) * (0.4 + 0.6 * Math.sin(t * Math.PI)) * (1 - release * 0.7f));
					pushPoint(px, py, pz, width, intensity * 0.78f);
				}
			}
		}

		private void drawTrail(float localAge) {
			double headTime = Math.min(age, frozenTrailAt), phase = sourceId * 0.37;
			double dirX = Math.cos(lungeYaw), dirZ = Math.sin(lungeYaw);
			double sideX = -dirZ, sideZ = dirX;
			int ribbons = quality == 0 ? 2 : 3;
			for (int ribbon = 0; ribbon < ribbons; ribbon++) {
				beginPath(0.35f, (float) (ribbon * 0.33));
				double lastRawX = 0, lastRawY = 0, lastRawZ = 0;
				for (int j = 0; j <= 15; j++) {
					double frac = j / 15.0;
					double at = headTime - frac * 0.28;
					trail.sample(at, sampled);
					double angle = ribbon * 2.0944 + frac * 2.48 + phase + Math.sin(sampled[3] * 15 + ribbon) * 0.20;
					double radius = (0.24 + ribbon * 0.07) * (1 - frac * 0.69) * (1 - GhoulAnimation.smooth(0.38f, 0.82f, localAge) * 0.7);
					double rawX = sampled[0], rawY = sampled[1], rawZ = sampled[2];
					double cosA = Math.cos(angle) * radius;
					double px = rawX + sideX * cosA;
					double py = rawY + height * (0.46 + ribbon * 0.08) + Math.sin(angle) * radius * 0.85;
					double pz = rawZ + sideZ * cosA;
					float movement = j == 0 ? 1f : (float) Math.clamp(Math.sqrt(
							(rawX - lastRawX) * (rawX - lastRawX) + (rawY - lastRawY) * (rawY - lastRawY) + (rawZ - lastRawZ) * (rawZ - lastRawZ)) * 6.5, 0, 1);
					float alpha = (1 - GhoulAnimation.smooth(0.08f, 0.52f, (float) (age - sampled[3])))
							* (1 - GhoulAnimation.smooth(0.42f, 0.82f, localAge)) * movement * 1.15f;
					float width = (float) ((0.145 + ribbon * 0.015) * Math.pow(1 - frac * 0.88, 0.62));
					pushPoint(px, py, pz, width, alpha);
					lastRawX = rawX; lastRawY = rawY; lastRawZ = rawZ;
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
				beginPath(0.60f, cut * 0.5f);
				for (int j = 0; j <= 9; j++) {
					double frac = j / 9.0;
					double arc = -1.25 + frac * 2.80 * Math.min(1, t / 0.045) + cut * 2.6 + t * (cut == 0 ? 4.2 : -3.2);
					double cosA = Math.cos(arc) * r;
					double fwdOff = Math.sin(arc * 1.5 + cut) * 0.16 + t * 0.72;
					double px = contactX + sideX * cosA + dirX * fwdOff;
					double py = contactY + Math.sin(arc) * r * (cut == 0 ? 0.82 : 1.18);
					double pz = contactZ + sideZ * cosA + dirZ * fwdOff;
					float width = (float) (0.095f * (1 - frac * 0.65) * Math.sin(frac * Math.PI));
					float alpha = (1 - GhoulAnimation.smooth(0.10f, 0.38f, t)) * (cut == 0 ? 1.15f : 0.88f);
					pushPoint(px, py, pz, width, alpha);
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
				beginPath(0.50f, (slot * 2 + strand) * 0.23f);
				for (int j = 0; j <= 10; j++) {
					double frac = j / 10.0;
					double t = tail + (head - tail) * frac;
					double wobble = Math.sin(t * 10 + strand * 2 + age * 9.5) * Math.sin(t * Math.PI) * 0.05;
					double px = bezier(siphon.ox, c1x, c2x, recX, t) + bendX * wobble;
					double py = bezier(siphon.oy, c1y, c2y, recY, t);
					double pz = bezier(siphon.oz, c1z, c2z, recZ, t) + bendZ * wobble;
					float width = (float) ((0.082f + strand * 0.026f) * (0.55f + frac * 0.55f) * Math.sin(frac * Math.PI));
					float alpha = siphon.strength * 1.15f * (1 - GhoulAnimation.smooth(duration, duration + 0.12f, strandAge));
					pushPoint(px, py, pz, width, alpha);
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
		private void beginPath(float kind, float seed) {
			if (activePaths >= MAX_PATHS) return;
			pathStart[activePaths] = nextPt;
			pathCount[activePaths] = 0;
			pathKind[activePaths] = kind;
			pathSeed[activePaths] = seed;
			activePaths++;
		}

		private void pushPoint(double px, double py, double pz, float width, float alpha) {
			if (activePaths <= 0 || nextPt * 5 + 5 > pathPts.length) return;
			int idx = nextPt * 5;
			pathPts[idx] = (float) (px - camX);
			pathPts[idx + 1] = (float) (py - camY);
			pathPts[idx + 2] = (float) (pz - camZ);
			pathPts[idx + 3] = Math.max(0f, width);
			pathPts[idx + 4] = Math.clamp(alpha, 0f, 1f);
			nextPt++;
			pathCount[activePaths - 1]++;
		}

		@Override
		public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
			if (!meshVisible) return;
			collector.submitCustomGeometry(poseStack, GhoulMeshRenderer.GHOUL_RENDER_TYPE, (pose, vc) -> {
				final double cX = cameraPos.x, cY = cameraPos.y, cZ = cameraPos.z;
				final double G = 1.0 / 16.0;
				float bx = (float) (bodyX - cameraPos.x);
				float by = (float) (bodyY - cameraPos.y);
				float bz = (float) (bodyZ - cameraPos.z);

				// 1. Subtle dark-crimson fog glow behind silhouette (3rd person only)
				float mistAlpha = Math.max(meshAura, meshLungeCompression * 0.65f);
				if (mistAlpha > 0.005f && !meshInside) {
					GhoulMeshRenderer.emitGlowHalo(pose, vc,
							bx, by + height * 0.48f, bz,
							1.9f, height * 0.85f, 0.15f, meshHeartbeat, mistAlpha * 0.45f);
					GhoulMeshRenderer.emitGlowHalo(pose, vc,
							bx, by + height * 0.34f, bz,
							1.2f, height * 0.55f, 0.62f, meshHeartbeat, mistAlpha * 0.32f);
				}

				// 2. 3-tier voxel splines: razor-thin dark spine + staggered shard barbs + rising ember motes
				for (int p = 0; p < activePaths; p++) {
					int start = pathStart[p] * 5;
					int count = pathCount[p];
					if (count <= 0) continue;
					float kind = pathKind[p];
					float seed = pathSeed[p];
					boolean isWing = kind < 0.25f;
					// In 1st person, suppress central wings so they don't clutter the crosshair
					if (meshInside && isWing) continue;

					int midIdx = count / 2;
					for (int i = 0; i < count; i++) {
						int idx = start + i * 5;
						float px = pathPts[idx], py = pathPts[idx + 1], pz = pathPts[idx + 2];
						float w = pathPts[idx + 3];
						float a = pathPts[idx + 4];
						if (w <= 0.004f || a <= 0.01f) continue;

						float u = count > 1 ? (float) i / (count - 1) : 0f;
						float h1 = ((i * 0.618034f + seed * 3.17f) % 1.0f);
						float h2 = ((i * 0.381966f + seed * 5.41f) % 1.0f);

						// Tier 1: Razor-thin spline spine (0.025..0.045 blocks, tapering to 0.012 at tips)
						float spineBase = isWing ? (0.042f * (1f - u * 0.68f)) : (0.032f * (1f - u * 0.55f));
						float spineHalf = (float) (G * 0.5) * (isWing ? 2.4f - 1.4f * u : 1.8f - 0.8f * u) * (0.9f + 0.2f * h1);
						float spineTone = u > 0.78f ? 0.72f : 0.14f + 0.16f * h2;
						// True voxel look: snap centers to a world-space 1/32 grid
						float sPx = (float) (Math.round((px + cX) / G) * G - cX);
						float sPy = (float) (Math.round((py + cY) / G) * G - cY);
						float sPz = (float) (Math.round((pz + cZ) / G) * G - cZ);
						GhoulMeshRenderer.emitVoxelCube(pose, vc,
								sPx, sPy, sPz, spineHalf, spineHalf, spineHalf,
								spineTone, h1, meshHeartbeat, a);

						// Subtle fog halo on wing midpoint
						if (i == midIdx && isWing && !meshInside) {
							GhoulMeshRenderer.emitGlowHalo(pose, vc,
									px, py, pz, 0.52f, 0.52f, seed, meshHeartbeat, a * 0.18f);
						}

						// Tier 2: Fragmented shard barbs staggered outward with gaps (every 2nd/3rd point)
						if ((i + p) % 2 == 0 && u > 0.08f && u < 0.94f) {
							double ang = seed * 12.0 + i * 2.39996 + age * 0.35;
							float dist = 0.08f + 0.14f * h2;
							float sx = (float) (Math.round((px + (float) Math.cos(ang) * dist + cX) / G) * G - cX);
							float sy = (float) (Math.round((py + (h1 - 0.35f) * 0.08f + cY) / G) * G - cY);
							float sz = (float) (Math.round((pz + (float) Math.sin(ang) * dist + cZ) / G) * G - cZ);
							float shardSize = (float) (G * 0.5) * (h1 > 0.6f ? 1f : 0.75f);
							float shardTone = h2 > 0.78f ? 0.68f : 0.32f + 0.20f * h1;
							GhoulMeshRenderer.emitVoxelCube(pose, vc,
									sx, sy, sz, shardSize, shardSize * (1.1f + 0.6f * h2), shardSize,
									shardTone, h2, meshHeartbeat, a * 0.88f);
						}

						// Tier 3: Tiny blood-ash ember motes detaching and drifting upward
						if ((i + p) % 3 == 0 && quality > 0) {
							float driftPhase = (age * 0.55f + h1 + seed) % 1.0f;
							float driftFade = (float) Math.sin(driftPhase * Math.PI);
							float ex = px + (float) Math.cos(h2 * 6.28318 + age * 1.1f) * (0.06f + driftPhase * 0.16f);
							float ey = py + driftPhase * 0.28f;
							float ez = pz + (float) Math.sin(h2 * 6.28318 + age * 1.1f) * (0.06f + driftPhase * 0.16f);
							float emberSize = (0.020f + 0.020f * h1) * (1.0f - driftPhase * 0.4f);
							GhoulMeshRenderer.emitVoxelCube(pose, vc,
									ex, ey, ez, emberSize, emberSize, emberSize,
									0.74f, h1, meshHeartbeat, a * driftFade * 0.82f);
						}
					}
				}

				// 3. Grounded thin voxel floor pulse ring + rising peripheral ember voxels
				float envScale = mistAlpha * (meshInside ? 0.35f : 1.0f);
				if (envScale > 0.005f) {
					if (meshAuraAge >= 0f && meshAuraAge < 0.45f) {
						float ringEnv = (1f - GhoulAnimation.smooth(0.08f, 0.45f, meshAuraAge)) * envScale;
						float rMid = 0.30f + meshAuraAge * 2.8f;
						int ringVoxels = 24;
						for (int v = 0; v < ringVoxels; v++) {
							double ang = v * (Math.PI * 2.0 / ringVoxels) + meshAuraAge * 1.8;
							float h1 = (v * 0.618034f) % 1.0f;
							float vs = (0.040f + 0.020f * h1) * (1f - meshAuraAge * 1.3f);
							if (vs <= 0.004f) continue;
							GhoulMeshRenderer.emitVoxelCube(pose, vc,
									bx + (float) Math.cos(ang) * rMid,
									by + 0.025f + meshAuraAge * 0.12f * h1,
									bz + (float) Math.sin(ang) * rMid,
									vs, vs * 0.8f, vs, 0.45f + 0.35f * h1, h1, meshHeartbeat, ringEnv * 0.88f);
						}
					}
					int dropCount = quality == 0 ? 8 : (quality == 1 ? 14 : 18);
					for (int k = 0; k < dropCount; k++) {
						float fk = k;
						float h1 = (fk * 0.618034f + 0.17f) % 1.0f;
						float h2 = (fk * 0.381966f + 0.53f) % 1.0f;
						float speed = 0.48f + 0.32f * h1 + meshFeeding * 0.25f;
						float phase = (age * speed + fk / (float) dropCount) % 1.0f;
						float env = (float) Math.sin(phase * Math.PI);
						float ang = fk * 2.39996f + age * (0.95f + 0.5f * (h2 - 0.5f) + meshSwing * 0.8f) + phase * 1.1f;
						float rad = (meshInside ? 0.78f : 0.45f) + 0.34f * h2;
						float dy = 0.04f + 0.18f * h1 + phase * (height * 0.92f + 0.25f);
						float sz = (0.030f + 0.025f * h1) * (0.55f + 0.45f * env);
						GhoulMeshRenderer.emitVoxelCube(pose, vc,
								bx + (float) Math.cos(ang) * rad, by + dy, bz + (float) Math.sin(ang) * rad,
								sz, sz * (1.0f + 0.4f * h2), sz,
								h2 > 0.75f ? 0.74f : 0.28f + 0.25f * h1, h1, meshHeartbeat, envScale * env * 0.90f);
					}
				}

				// 4. Fine-grained Blood Siphon extraction & ingestion clusters
				for (int i = 0; i < 2; i++) {
					int kIdx = i * 4;
					if (knotExt[kIdx + 3] > 0.005f) {
						float a = Math.min(1f, knotExt[kIdx + 3]);
						GhoulMeshRenderer.emitGlowHalo(pose, vc, knotExt[kIdx], knotExt[kIdx + 1], knotExt[kIdx + 2], 0.28f, 0.28f, 0.45f, 0f, a * 0.24f);
						GhoulMeshRenderer.emitVoxelCube(pose, vc, knotExt[kIdx], knotExt[kIdx + 1], knotExt[kIdx + 2], 0.045f, 0.055f, 0.045f, 0.72f, 0.45f, meshHeartbeat, a);
					}
					if (knotIng[kIdx + 3] > 0.005f) {
						float a = Math.min(1f, knotIng[kIdx + 3]);
						GhoulMeshRenderer.emitGlowHalo(pose, vc, knotIng[kIdx], knotIng[kIdx + 1], knotIng[kIdx + 2], 0.32f, 0.32f, 0.35f, 0f, a * 0.24f);
						GhoulMeshRenderer.emitVoxelCube(pose, vc, knotIng[kIdx], knotIng[kIdx + 1], knotIng[kIdx + 2], 0.055f, 0.070f, 0.055f, 0.75f, 0.35f, meshHeartbeat, a);
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
