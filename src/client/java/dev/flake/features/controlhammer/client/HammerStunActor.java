package dev.flake.features.controlhammer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.concussed.ShockwaveAnimation;
import dev.flake.features.controlhammer.HammerAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RadialActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Shared HAMMER_STUN actor:
 * - Control Hammer (yaw <= 0.5): keeps the classic white radial explosion burst.
 * - Axe of Cumshot enemy hit (yaw > 0.5, targetId > 0): drops a jagged voxel boulder
 *   from above the target's head that passes straight through them while crumbling apart.
 * - Axe of Cumshot block hit (yaw > 0.5, targetId <= 0): crisp earthen voxel debris burst.
 */
public final class HammerStunActor implements EffectActor {
	private static final float BOULDER_DURATION_SEC = 0.86f;
	private static final float BLOCK_BURST_DURATION_SEC = 0.44f;

	// Precomputed jagged boulder fragments (14 multi-voxel stone chunks forming an irregular ~0.95m boulder).
	// Each entry: {ox, oy, oz, hx, hy, hz, colorTier, breakDelayBias, scatterSpeed, spinRate}
	private static final float[][] BOULDER_CHUNKS = {
			// Core chunks (punch straight down through the centerline)
			{  0.00f,  0.02f,  0.00f, 0.20f, 0.21f, 0.19f, 1f, 0.05f, 0.65f, 4.2f },
			{ -0.10f, -0.18f,  0.08f, 0.16f, 0.15f, 0.15f, 0f, 0.00f, 1.05f, 5.6f },
			{  0.11f,  0.19f, -0.07f, 0.15f, 0.16f, 0.14f, 2f, 0.08f, 0.90f, 4.8f },
			// Outer jagged shell chunks (peel outward and tumble down around/through the body)
			{  0.26f, -0.05f,  0.14f, 0.14f, 0.13f, 0.13f, 1f, 0.02f, 2.35f, 7.2f },
			{ -0.25f,  0.04f,  0.16f, 0.13f, 0.15f, 0.12f, 3f, 0.03f, 2.50f, 6.8f },
			{  0.18f, -0.14f, -0.22f, 0.13f, 0.12f, 0.14f, 0f, 0.01f, 2.65f, 7.8f },
			{ -0.20f, -0.12f, -0.20f, 0.14f, 0.13f, 0.13f, 2f, 0.02f, 2.45f, 6.4f },
			{  0.02f, -0.26f,  0.19f, 0.13f, 0.11f, 0.13f, 1f, 0.00f, 2.25f, 8.1f },
			{ -0.04f, -0.24f, -0.19f, 0.12f, 0.12f, 0.14f, 3f, 0.00f, 2.40f, 7.4f },
			{  0.25f,  0.16f, -0.11f, 0.13f, 0.13f, 0.12f, 2f, 0.06f, 2.15f, 6.9f },
			{ -0.24f,  0.17f, -0.09f, 0.12f, 0.13f, 0.13f, 0f, 0.06f, 2.30f, 7.5f },
			{  0.13f,  0.24f,  0.21f, 0.12f, 0.12f, 0.12f, 1f, 0.07f, 2.05f, 6.2f },
			{ -0.12f,  0.26f,  0.19f, 0.13f, 0.11f, 0.12f, 3f, 0.07f, 2.20f, 7.1f },
			{  0.01f,  0.32f, -0.04f, 0.14f, 0.11f, 0.13f, 2f, 0.09f, 1.75f, 5.9f },
	};

	private final int sourceId;
	private final int targetId;
	private final Vec3 fallbackPos;
	private final boolean axeImpact;
	private final boolean dropBoulder;
	private final float durationSec;
	private final RadialActor controlHammerBurst;
	private long startNanos = -1L;
	private float ageSec;

	public HammerStunActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.targetId = payload.targetId();
		this.fallbackPos = payload.origin();
		this.axeImpact = payload.yaw() > 0.5f;
		this.dropBoulder = this.axeImpact && this.targetId > 0;
		this.durationSec = this.dropBoulder ? BOULDER_DURATION_SEC : BLOCK_BURST_DURATION_SEC;

		if (!this.axeImpact) {
			this.controlHammerBurst = new RadialActor(payload) {
				@Override protected float duration() { return HammerAnimation.DURATION; }
				@Override protected float radius(float age) { return HammerAnimation.radius(age); }
				@Override protected float opacity(float age) { return HammerAnimation.opacity(age); }
				@Override protected int style() { return 0; }
				@Override protected float styleExtra(float age) { return ShockwaveAnimation.chargeProgress(age); }
			};
		} else {
			this.controlHammerBurst = null;
		}
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean expired() {
		if (controlHammerBurst != null) return controlHammerBurst.expired();
		return startNanos >= 0L && ageSec >= durationSec;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		if (controlHammerBurst != null) {
			return controlHammerBurst.write(frame, slot);
		}
		if (startNanos < 0L) startNanos = frame.nanos;
		ageSec = (frame.nanos - startNanos) / 1_000_000_000.0f;
		return ageSec >= durationSec;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		if (!axeImpact) return;

		if (startNanos < 0L) startNanos = System.nanoTime();
		ageSec = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
		if (ageSec >= durationSec) return;

		if (!dropBoulder) {
			submitBlockBurst(poseStack, collector, cameraPos);
			return;
		}

		submitCrumblingBoulder(poseStack, collector, cameraPos);
	}

	private void submitCrumblingBoulder(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		Entity target = mc.level != null ? mc.level.getEntity(targetId) : null;

		double anchorX;
		double footY;
		float bodyH;
		double anchorZ;
		if (target != null) {
			anchorX = target.getX();
			footY = target.getY();
			bodyH = Math.max(1.0f, (float) target.getBbHeight());
			anchorZ = target.getZ();
		} else {
			anchorX = fallbackPos.x;
			footY = fallbackPos.y - 1.35;
			bodyH = 1.8f;
			anchorZ = fallbackPos.z;
		}

		double headY = footY + bodyH;
		// Start ~1.85m above the head and accelerate straight down through the body to the ground.
		double startCenterY = headY + 1.85;
		float t = ageSec;
		double boulderCenterY = startCenterY - 8.4 * t - 12.0 * t * t;

		float fadeStart = 0.56f;
		float fadeT = Mth.clamp((t - fadeStart) / (BOULDER_DURATION_SEC - fadeStart), 0f, 1f);
		float globalAlpha = Math.min(1f, t / 0.04f) * (1f - fadeT * fadeT * (3f - 2f * fadeT));
		if (globalAlpha <= 0.01f) return;

		double relAnchorX = anchorX - cameraPos.x;
		double relAnchorZ = anchorZ - cameraPos.z;
		double relFootY = footY - cameraPos.y;
		double relHeadY = headY - cameraPos.y;

		// Emit each multi-voxel jagged boulder chunk independently so it stays packed on the drop,
		// breaks apart sequentially as it passes through the head/body, and scatters along the floor.
		for (int i = 0; i < BOULDER_CHUNKS.length; i++) {
			float[] c = BOULDER_CHUNKS[i];
			float ox = c[0];
			float oy = c[1];
			float oz = c[2];
			float hx = c[3];
			float hy = c[4];
			float hz = c[5];
			int colorTier = (int) c[6];
			float breakDelayBias = c[7];
			float scatterSpeed = c[8];
			float spinRate = c[9];

			// Solve roughly when this chunk reaches head height (~1.85 + oy below start at ~10.8 m/s avg)
			float distToHead = 1.80f + oy;
			float breakTime = Mth.clamp(distToHead / 11.2f + breakDelayBias, 0.09f, 0.27f);

			double chunkX;
			double chunkY;
			double chunkZ;
			float rotX;
			float rotY;
			float rotZ;
			float scale = 1.0f;

			if (t <= breakTime) {
				// Still descending together inside the boulder above/entering the head
				float intactSpin = t * 1.9f;
				float cosS = (float) Math.cos(intactSpin);
				float sinS = (float) Math.sin(intactSpin);
				chunkX = relAnchorX + ox * cosS - oz * sinS;
				chunkY = (boulderCenterY - cameraPos.y) + oy;
				chunkZ = relAnchorZ + ox * sinS + oz * cosS;
				rotX = t * 1.4f + i * 0.37f;
				rotY = intactSpin + i * 0.61f;
				rotZ = t * 1.1f + i * 0.23f;
			} else {
				float dt = t - breakTime;
				float intactSpinAtBreak = breakTime * 1.9f;
				float cosB = (float) Math.cos(intactSpinAtBreak);
				float sinB = (float) Math.sin(intactSpinAtBreak);
				float bx = ox * cosB - oz * sinB;
				float bz = ox * sinB + oz * cosB;
				double breakCenterY = startCenterY - 8.4 * breakTime - 12.0 * breakTime * breakTime;
				double startChunkY = (breakCenterY - cameraPos.y) + oy;

				// Radial outward burst + slight tangent swirl so shards crumble outward while falling through
				float horizLen = (float) Math.sqrt(bx * bx + bz * bz);
				float dirX = horizLen > 0.02f ? bx / horizLen : (float) Math.cos(i * 1.7f);
				float dirZ = horizLen > 0.02f ? bz / horizLen : (float) Math.sin(i * 1.7f);
				float tangX = -dirZ * 0.28f;
				float tangZ = dirX * 0.28f;

				float initialDownSpeed = 8.4f + 24.0f * breakTime;
				float chunkDownSpeed = initialDownSpeed * (0.78f + 0.14f * (i % 3));
				// Dragged outward scatter slows slightly over time
				float lateralDist = scatterSpeed * dt * (1.0f - 0.35f * Math.min(1.0f, dt / 0.55f));
				chunkX = relAnchorX + bx + (dirX + tangX) * lateralDist;
				chunkZ = relAnchorZ + bz + (dirZ + tangZ) * lateralDist;

				double rawY = startChunkY - chunkDownSpeed * dt - 7.5 * dt * dt;
				double floorY = relFootY + hy * 0.72;
				if (rawY < floorY) {
					// Small rock bounce on ground contact, then settle and slide outward
					double penetration = floorY - rawY;
					double bounce = Math.sin(Math.min(1.0, penetration * 2.2) * Math.PI) * 0.16 * Math.max(0.0, 1.0 - dt * 1.6);
					chunkY = floorY + bounce;
				} else {
					chunkY = rawY;
				}

				rotX = breakTime * 1.4f + i * 0.37f + dt * spinRate;
				rotY = intactSpinAtBreak + i * 0.61f + dt * (spinRate * 0.85f);
				rotZ = breakTime * 1.1f + i * 0.23f - dt * (spinRate * 0.65f);
				scale = Math.max(0.28f, 1.0f - dt * 0.72f);
			}

			float[] rgb = stoneColor(colorTier);
			final float fx = (float) chunkX;
			final float fy = (float) chunkY;
			final float fz = (float) chunkZ;
			final float shx = hx * scale;
			final float shy = hy * scale;
			final float shz = hz * scale;
			final float r = rgb[0], g = rgb[1], b = rgb[2];
			final int chipIdx = i;

			poseStack.pushPose();
			poseStack.translate(fx, fy, fz);
			poseStack.mulPose(new Matrix4f().rotate(new Quaternionf().rotateY(rotY).rotateX(rotX).rotateZ(rotZ)));
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
				WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0f, shx, shy, shz, r, g, b, 0.96f * globalAlpha);
				// Add a smaller asymmetric stone ridge so every chunk reads as rough broken rock
				float subSignX = (chipIdx % 2 == 0) ? 0.55f : -0.55f;
				float subSignY = (chipIdx % 3 == 0) ? 0.45f : -0.45f;
				WeaponVoxelRenderer.emitCube(
						pose, vc,
						shx * subSignX, shy * subSignY, shz * 0.35f,
						shx * 0.58f, shy * 0.52f, shz * 0.55f,
						r * 0.84f, g * 0.84f, b * 0.85f, 0.92f * globalAlpha
				);
			});
			poseStack.popPose();
		}

		// Trailing gravel pebbles + soft crumbling rock dust along the pass-through column
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitPassThroughDust(pose, vc, (float) relAnchorX, (float) relHeadY, (float) relFootY, (float) relAnchorZ, t, globalAlpha));

		// Restrained warm dust/crack pulse when crushing through head and ground
		if (t > 0.11f && t < 0.48f) {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) -> {
				float headPulse = 1.0f - Math.abs(t - 0.19f) / 0.10f;
				if (headPulse > 0.0f) {
					WeaponVoxelRenderer.emitGlowSprite(
							pose, vc,
							(float) relAnchorX, (float) (relHeadY - 0.15), (float) relAnchorZ,
							0.52f, 0.88f, 0.66f, 0.42f, 0.28f * headPulse * globalAlpha
					);
				}
				float footPulse = 1.0f - Math.abs(t - 0.34f) / 0.14f;
				if (footPulse > 0.0f) {
					WeaponVoxelRenderer.emitGlowSprite(
							pose, vc,
							(float) relAnchorX, (float) (relFootY + 0.12), (float) relAnchorZ,
							0.68f, 0.82f, 0.60f, 0.38f, 0.24f * footPulse * globalAlpha
					);
				}
			});
		}
	}

	private void emitPassThroughDust(PoseStack.Pose pose, VertexConsumer vc,
			float ax, float headY, float footY, float az, float t, float globalAlpha) {
		if (t < 0.10f) return;
		float dustAge = t - 0.10f;
		float bodySpan = Math.max(0.9f, headY - footY);

		for (int i = 0; i < 24; i++) {
			float frac = (i % 8) / 7.0f;
			float spawnTime = 0.11f + frac * 0.22f;
			if (t < spawnTime) continue;

			float pAge = t - spawnTime;
			float angle = i * 2.39996f + dustAge * 0.7f;
			float startY = headY - frac * bodySpan * 0.92f;
			float radial = 0.18f + (0.95f + (i % 4) * 0.28f) * (1.0f - (float) Math.exp(-pAge * 4.8));
			float px = ax + (float) Math.cos(angle) * radial;
			float pz = az + (float) Math.sin(angle) * radial;
			float py = (float) Math.max(footY + 0.06f, startY - pAge * (1.25f + (i % 3) * 0.45f));

			float pFade = Mth.clamp(1.0f - pAge / 0.56f, 0.0f, 1.0f);
			float size = (0.06f + (i % 3) * 0.025f) * (0.65f + 0.65f * (1.0f - pFade));
			float shade = 0.42f + (i % 4) * 0.055f;
			WeaponVoxelRenderer.emitCube(
					pose, vc, px, py, pz, size, size, size,
					shade, shade * 0.95f, shade * 0.90f,
					0.48f * pFade * globalAlpha
			);
		}
	}

	private void submitBlockBurst(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		float progress = Mth.clamp(ageSec / BLOCK_BURST_DURATION_SEC, 0f, 1f);
		float opacity = 1f - progress * progress;
		if (opacity <= 0.01f) return;

		float rx = (float) (fallbackPos.x - cameraPos.x);
		float ry = (float) (fallbackPos.y - cameraPos.y);
		float rz = (float) (fallbackPos.z - cameraPos.z);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			for (int i = 0; i < 14; i++) {
				float ang = i * 2.39996f;
				float speed = 1.6f + (i % 4) * 0.55f;
				float dist = speed * ageSec;
				float px = rx + (float) Math.cos(ang) * dist;
				float pz = rz + (float) Math.sin(ang) * dist;
				float py = ry + (0.65f + (i % 3) * 0.35f) * ageSec - 4.2f * ageSec * ageSec;
				float s = (0.065f - 0.03f * progress) * (0.8f + 0.2f * (i % 2));
				float[] rgb = stoneColor(i % 4);
				WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, s, s, s, rgb[0], rgb[1], rgb[2], 0.92f * opacity);
			}
		});
	}

	private static float[] stoneColor(int tier) {
		return switch (tier) {
			case 0 -> new float[] { 0.24f, 0.25f, 0.28f }; // dark slate
			case 1 -> new float[] { 0.40f, 0.41f, 0.44f }; // granite midtone
			case 2 -> new float[] { 0.55f, 0.55f, 0.57f }; // chipped limestone highlight
			default -> new float[] { 0.46f, 0.36f, 0.29f }; // earthy mineral rock
		};
	}
}
