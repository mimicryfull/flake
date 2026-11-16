package dev.flake.features.bloodcontroller.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.bloodcontroller.BloodControllerRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * World-space voxel Voodoo Doll (~0.62x player size) connected to the cursed victim via a pulsing
 * dark-red & crimson blood thread, complete with hit recoil and arterial droplet splashes.
 */
public final class VoodooDollActor implements EffectActor {
	private static final float MAX_LIFE_SEC = BloodControllerRuntime.VOODOO_TICKS / 20.0f;
	private static final float OFF_FADE_SEC = 0.25f;
	private static final int THREAD_SEGMENTS = 24;
	private static final int DOLL_DROPLETS = 12;
	private static final int HIT_BURST_DROPS = 14;

	private final int sourceId;
	private final Vec3 dollOrigin;
	private int targetId;
	private Vec3 lastTargetChest;
	private final float initialYaw;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float offAt = Float.NaN;
	private float lastHitAt = -10.0f;

	public VoodooDollActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.dollOrigin = payload.origin();
		this.initialYaw = payload.yaw();
		this.targetId = payload.targetId();
		this.lastTargetChest = payload.aim();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId) return false;
		if (payload.phase() == CombatVfxPayload.Phase.VOODOO_DOLL_HIT) {
			this.lastHitAt = ageSec;
			if (payload.targetId() >= 0) this.targetId = payload.targetId();
			this.lastTargetChest = payload.aim();
			return true;
		}
		if (payload.phase() == CombatVfxPayload.Phase.VOODOO_DOLL_OFF) {
			if (Float.isNaN(offAt)) offAt = ageSec;
			return true;
		}
		return false;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.VOODOO_DOLL_ON;
	}

	@Override
	public boolean expired() {
		return ageSec >= MAX_LIFE_SEC + OFF_FADE_SEC || (!Float.isNaN(offAt) && ageSec >= offAt + OFF_FADE_SEC);
	}

	private float computeOpacity() {
		float in = Mth.clamp(ageSec / 0.14f, 0.0f, 1.0f);
		float offFade = Float.isNaN(offAt) ? 1.0f : Mth.clamp(1.0f - (ageSec - offAt) / OFF_FADE_SEC, 0.0f, 1.0f);
		float tailFade = Mth.clamp((MAX_LIFE_SEC + 0.15f - ageSec) / 0.25f, 0.0f, 1.0f);
		return in * offFade * tailFade;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		slot.values[slot.style] = 0.0f;
		return expired();
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		ClientLevel level = mc.level;
		if (level != null && targetId >= 0) {
			Entity target = level.getEntity(targetId);
			if (target != null && target.isAlive()) {
				lastTargetChest = target.position().add(0.0, target.getBbHeight() * 0.58, 0.0);
			}
		}

		double dx = dollOrigin.x - cameraPos.x;
		double dy = dollOrigin.y - cameraPos.y;
		double dz = dollOrigin.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 72.0 * 72.0) return;

		float alpha = computeOpacity();
		if (alpha <= 0.01f) return;

		float hitElapsed = ageSec - lastHitAt;
		float hitFlash = (hitElapsed >= 0.0f && hitElapsed < 0.36f) ? 1.0f - (hitElapsed / 0.36f) : 0.0f;
		float pulse = 0.5f + 0.5f * Mth.sin(ageSec * 7.2f);
		float bob = Mth.sin(ageSec * 3.1f) * 0.045f + 0.06f;

		// Orient doll toward cursed victim smoothly
		float yaw = (float) Math.atan2(lastTargetChest.x - dollOrigin.x, lastTargetChest.z - dollOrigin.z);
		if (!Float.isFinite(yaw)) yaw = initialYaw;
		float recoilPitch = -hitFlash * 0.38f * Mth.sin(Mth.clamp(hitElapsed / 0.18f, 0.0f, 1.0f) * Mth.PI);
		float swayRoll = Mth.sin(ageSec * 2.4f) * 0.05f;

		// 1. Voxel Voodoo Doll (~0.62x player proportions)
		poseStack.pushPose();
		poseStack.translate((float) dx, (float) (dy + bob), (float) dz);
		poseStack.mulPose(new Matrix4f().rotationY(yaw).rotateX(recoilPitch).rotateZ(swayRoll));

		float scale = (0.72f + 0.28f * Mth.clamp(ageSec / 0.14f, 0.0f, 1.0f)) * alpha;
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitDollBody(pose, vc, scale, pulse, hitFlash, alpha));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> {
					float heartY = 0.58f * scale;
					WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0.0f, heartY, 0.0f,
							0.30f + 0.10f * pulse + 0.24f * hitFlash,
							0.95f, 0.08f + 0.18f * hitFlash, 0.12f,
							alpha * (0.36f + 0.35f * hitFlash));
				});
		poseStack.popPose();

		// 2. World-space Blood Thread + Dark-Red Droplets + Victim Crimson Aura
		float threadStartX = (float) dx;
		float threadStartY = (float) (dy + bob + 0.56f * scale);
		float threadStartZ = (float) dz;
		float threadEndX = (float) (lastTargetChest.x - cameraPos.x);
		float threadEndY = (float) (lastTargetChest.y - cameraPos.y);
		float threadEndZ = (float) (lastTargetChest.z - cameraPos.z);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			emitBloodThread(pose, vc, threadStartX, threadStartY, threadStartZ,
					threadEndX, threadEndY, threadEndZ, ageSec, hitElapsed, hitFlash, alpha, false);
			emitSwirlDroplets(pose, vc, (float) dx, (float) dy, (float) dz,
					threadEndX, threadEndY, threadEndZ, ageSec, hitElapsed, hitFlash, alpha);
		});

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE, (pose, vc) -> {
			emitBloodThread(pose, vc, threadStartX, threadStartY, threadStartZ,
					threadEndX, threadEndY, threadEndZ, ageSec, hitElapsed, hitFlash, alpha * 0.55f, true);
			emitBaseRing(pose, vc, (float) dx, (float) dy + 0.02f, (float) dz, ageSec, hitFlash, alpha);
		});
	}

	private static void emitDollBody(PoseStack.Pose pose, VertexConsumer vc,
			float s, float pulse, float hitFlash, float alpha) {
		// Stitched dark burlap-blood doll shades with arterial highlight on hit
		float clothR = Mth.lerp(hitFlash, 0.26f, 0.92f);
		float clothG = Mth.lerp(hitFlash, 0.045f, 0.12f);
		float clothB = Mth.lerp(hitFlash, 0.06f, 0.14f);

		float darkR = Mth.lerp(hitFlash, 0.15f, 0.70f);
		float darkG = 0.02f;
		float darkB = 0.03f;

		float veinR = Mth.lerp(pulse, 0.72f, 1.0f);
		float veinG = 0.05f + 0.10f * hitFlash;
		float veinB = 0.09f + 0.08f * hitFlash;

		// Left & right legs (~0.38 tall)
		WeaponVoxelRenderer.emitCube(pose, vc, -0.065f * s, 0.18f * s, 0.0f, 0.048f * s, 0.18f * s, 0.048f * s, darkR, darkG, darkB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.065f * s, 0.18f * s, 0.0f, 0.048f * s, 0.18f * s, 0.048f * s, darkR, darkG, darkB, alpha);
		// Crimson ankle bindings
		WeaponVoxelRenderer.emitCube(pose, vc, -0.065f * s, 0.10f * s, 0.0f, 0.054f * s, 0.022f * s, 0.054f * s, veinR, veinG, veinB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.065f * s, 0.10f * s, 0.0f, 0.054f * s, 0.022f * s, 0.054f * s, veinR, veinG, veinB, alpha);

		// Torso (~0.39 tall)
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.56f * s, 0.0f, 0.125f * s, 0.195f * s, 0.072f * s, clothR, clothG, clothB, alpha);
		// Stitched blood-seam cross & glowing heart core on chest
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.57f * s, 0.074f * s, 0.018f * s, 0.15f * s, 0.012f * s, veinR, veinG, veinB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.59f * s, 0.074f * s, 0.095f * s, 0.018f * s, 0.012f * s, veinR, veinG, veinB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.02f * s, 0.60f * s, 0.082f * s, 0.036f * s, 0.036f * s, 0.022f * s, 1.0f, 0.14f + 0.18f * hitFlash, 0.18f, alpha);

		// Arms (angled slightly forward & out in classic limp voodoo silhouette)
		WeaponVoxelRenderer.emitCube(pose, vc, -0.185f * s, 0.59f * s, 0.02f * s, 0.044f * s, 0.155f * s, 0.044f * s, clothR, clothG, clothB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.185f * s, 0.59f * s, 0.02f * s, 0.044f * s, 0.155f * s, 0.044f * s, clothR, clothG, clothB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, -0.185f * s, 0.48f * s, 0.02f * s, 0.048f * s, 0.02f * s, 0.048f * s, veinR, veinG, veinB, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.185f * s, 0.48f * s, 0.02f * s, 0.048f * s, 0.02f * s, 0.048f * s, veinR, veinG, veinB, alpha);

		// Slightly oversized voxel head (~0.29 cube) with dark button eyes & glowing forehead sigil
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.91f * s, 0.0f, 0.145f * s, 0.145f * s, 0.145f * s, clothR, clothG, clothB, alpha);
		// Mouth stitch
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.83f * s, 0.148f * s, 0.075f * s, 0.014f * s, 0.010f * s, darkR, darkG, darkB, alpha);
		// Button / X-stitch eyes (dark core + red center)
		WeaponVoxelRenderer.emitCube(pose, vc, -0.055f * s, 0.91f * s, 0.148f * s, 0.026f * s, 0.026f * s, 0.012f * s, 0.05f, 0.01f, 0.02f, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.055f * s, 0.91f * s, 0.148f * s, 0.026f * s, 0.026f * s, 0.012f * s, 0.05f, 0.01f, 0.02f, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, -0.055f * s, 0.91f * s, 0.156f * s, 0.012f * s, 0.012f * s, 0.008f * s, 0.95f, 0.06f, 0.10f, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.055f * s, 0.91f * s, 0.156f * s, 0.012f * s, 0.012f * s, 0.008f * s, 0.95f, 0.06f, 0.10f, alpha);

		// Crimson curse pin sticking diagonally out of the doll's heart
		for (int i = 0; i < 4; i++) {
			float t = (i + 1) * 0.052f * s;
			WeaponVoxelRenderer.emitCube(pose, vc, 0.02f * s + t * 0.35f, 0.60f * s + t * 0.55f, 0.09f * s + t * 0.85f,
					0.011f * s, 0.011f * s, 0.011f * s, 0.25f, 0.22f, 0.24f, alpha);
		}
		WeaponVoxelRenderer.emitCube(pose, vc, 0.02f * s + 0.09f * s, 0.60f * s + 0.13f * s, 0.09f * s + 0.21f * s,
				0.028f * s, 0.028f * s, 0.028f * s, 1.0f, 0.10f, 0.16f, alpha);
	}

	private static void emitBloodThread(PoseStack.Pose pose, VertexConsumer vc,
			float sx, float sy, float sz, float ex, float ey, float ez,
			float age, float hitElapsed, float hitFlash, float alpha, boolean softPass) {
		float pulseTravel = (age * 1.45f) % 1.0f;
		float hitWaveT = (hitElapsed >= 0.0f && hitElapsed < 0.36f) ? hitElapsed / 0.36f : -1.0f;

		for (int i = 0; i <= THREAD_SEGMENTS; i++) {
			float t = i / (float) THREAD_SEGMENTS;
			float sag = Mth.sin(t * Mth.PI) * 0.32f;
			float wave = Mth.sin(t * 11.0f - age * 8.5f) * 0.055f * Mth.sin(t * Mth.PI);
			float px = Mth.lerp(t, sx, ex) + wave;
			float py = Mth.lerp(t, sy, ey) - sag + Mth.cos(t * 9.0f - age * 7.0f) * 0.04f * Mth.sin(t * Mth.PI);
			float pz = Mth.lerp(t, sz, ez) - wave * 0.8f;

			float bead = Math.max(0.0f, 1.0f - Math.abs(t - pulseTravel) * 7.0f);
			float hitBolt = hitWaveT >= 0.0f ? Math.max(0.0f, 1.0f - Math.abs(t - hitWaveT) * 5.0f) : 0.0f;
			float energy = Math.max(bead, hitBolt * 1.35f);

			float size = softPass ? (0.048f + 0.042f * energy) : (0.022f + 0.026f * energy);
			float r = Mth.clamp(0.42f + 0.58f * energy + 0.25f * hitFlash, 0.0f, 1.0f);
			float g = 0.02f + 0.12f * energy + 0.10f * hitBolt;
			float b = 0.03f + 0.10f * energy;
			WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, size, size, size, r, g, b, alpha);
		}
	}

	private static void emitSwirlDroplets(PoseStack.Pose pose, VertexConsumer vc,
			float dx, float dy, float dz, float vx, float vy, float vz,
			float age, float hitElapsed, float hitFlash, float alpha) {
		for (int i = 0; i < DOLL_DROPLETS; i++) {
			float phase = i * (Mth.TWO_PI / DOLL_DROPLETS);
			float ang = age * (2.2f + (i % 3) * 0.45f) + phase;
			float rad = 0.38f + 0.14f * Mth.sin(age * 3.0f + i);
			float h = ((age * 0.55f + i * 0.17f) % 1.0f) * 1.15f;
			float px = dx + Mth.cos(ang) * rad;
			float pz = dz + Mth.sin(ang) * rad;
			float py = dy + h;
			float dropAlpha = alpha * Mth.sin(((age * 0.55f + i * 0.17f) % 1.0f) * Mth.PI);
			float r = (i & 1) == 0 ? 0.88f : 0.46f;
			WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, 0.026f, 0.038f, 0.026f, r, 0.03f, 0.06f, dropAlpha);

			// Victim orbiting crimson droplets
			float vAng = -age * 2.8f + phase;
			float vRad = 0.52f + 0.08f * Mth.sin(age * 4.0f + i);
			WeaponVoxelRenderer.emitCube(pose, vc,
					vx + Mth.cos(vAng) * vRad,
					vy + Mth.sin(age * 3.5f + phase) * 0.35f,
					vz + Mth.sin(vAng) * vRad,
					0.024f, 0.034f, 0.024f,
					0.78f + 0.22f * hitFlash, 0.03f + 0.08f * hitFlash, 0.07f, alpha * 0.85f);
		}

		// Splash burst when doll is hit
		if (hitElapsed >= 0.0f && hitElapsed < 0.40f) {
			float p = hitElapsed / 0.40f;
			float burstAlpha = alpha * (1.0f - p);
			for (int i = 0; i < HIT_BURST_DROPS; i++) {
				float theta = i * 2.39996f;
				float speed = 0.85f + (i % 4) * 0.35f;
				float bx = dx + Mth.cos(theta) * speed * p;
				float bz = dz + Mth.sin(theta) * speed * p;
				float by = dy + 0.62f + (0.45f - p * 1.15f) * p * speed;
				WeaponVoxelRenderer.emitCube(pose, vc, bx, by, bz, 0.032f, 0.032f, 0.032f,
						0.96f, 0.05f, 0.09f, burstAlpha);
			}
		}
	}

	private static void emitBaseRing(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float age, float hitFlash, float alpha) {
		int nodes = 16;
		float rad = 0.52f + 0.08f * Mth.sin(age * 5.0f) + 0.22f * hitFlash;
		for (int i = 0; i < nodes; i++) {
			float a = i * (Mth.TWO_PI / nodes) + age * 1.2f;
			WeaponVoxelRenderer.emitCube(pose, vc,
					cx + Mth.cos(a) * rad, cy, cz + Mth.sin(a) * rad,
					0.055f, 0.015f, 0.055f,
					0.75f + 0.25f * hitFlash, 0.03f, 0.06f, alpha * 0.72f);
		}
	}
}
