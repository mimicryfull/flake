package dev.flake.features.concussed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.concussed.EnergyMotes;
import dev.flake.features.concussed.Shockwave;
import dev.flake.features.concussed.ShockwaveAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RadialActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Experimental Chronosphere-inspired voxel explosion for Concussed's Gravitational Dissonance:
 * fuses the gravitational lens shockwave shader with a geodesic voxel dome flash,
 * an expanding ground voxel ring, and a 3D spherical burst of 64 tumbling ice-cyan cubes.
 */
public final class ShockwaveActor extends RadialActor {
	private static final int BURST_VOXELS = 64;
	private static final int RING_TILT_COUNT = 3;
	private static final float[] DIR_X = new float[BURST_VOXELS];
	private static final float[] DIR_Y = new float[BURST_VOXELS];
	private static final float[] DIR_Z = new float[BURST_VOXELS];
	private static final float[] SPEED = new float[BURST_VOXELS];
	private static final float[] BASE_SIZE = new float[BURST_VOXELS];
	private static final int[] COLOR_TIER = new int[BURST_VOXELS];

	static {
		// Fibonacci sphere distribution with deterministic speed/size jitter for a rich 3D voxel explosion
		for (int i = 0; i < BURST_VOXELS; i++) {
			float y = 1.0f - (i + 0.5f) * 2.0f / BURST_VOXELS;
			float r = (float) Math.sqrt(Math.max(0.0f, 1.0f - y * y));
			float phi = i * 2.3999632f;
			DIR_X[i] = (float) Math.cos(phi) * r;
			DIR_Y[i] = y;
			DIR_Z[i] = (float) Math.sin(phi) * r;
			float var = 0.5f + 0.5f * (float) Math.sin(i * 7.13f + 1.4f);
			SPEED[i] = 8.5f + 9.0f * var;
			BASE_SIZE[i] = 0.065f + 0.080f * (0.5f + 0.5f * (float) Math.cos(i * 4.7f));
			COLOR_TIER[i] = i % 4;
		}
	}

	private final double[] ex = new double[EnergyMotes.COUNT];
	private final double[] ey = new double[EnergyMotes.COUNT];
	private final double[] ez = new double[EnergyMotes.COUNT];
	private long startNanos = -1L;
	private float ageSec;

	public ShockwaveActor(CombatVfxPayload payload) {
		super(payload);
		for (int i = 0; i < EnergyMotes.COUNT; i++) {
			ey[i] = EnergyMotes.directionY(i);
			double r = Math.sqrt(1 - ey[i] * ey[i]);
			double angle = EnergyMotes.angle(i, sourceId);
			ex[i] = Math.cos(angle) * r;
			ez[i] = Math.sin(angle) * r;
		}
	}

	@Override protected float duration() { return ShockwaveAnimation.VFX_DURATION_SECONDS; }
	@Override protected float radius(float age) { return ShockwaveAnimation.radius(age, (float) Shockwave.RADIUS); }
	@Override protected float opacity(float age) {
		return Math.max(ShockwaveAnimation.opacity(age) * 0.72f, age < EnergyMotes.DURATION ? 0.001f : 0f);
	}
	@Override protected int style() { return 0; }
	@Override protected float styleExtra(float age) { return ShockwaveAnimation.chargeProgress(age); }

	@Override
	protected void details(RenderFrame frame, SlotWriter slot, float age, float ox, float oy, float oz) {
		if (startNanos < 0L) startNanos = frame.nanos;
		ageSec = age;

		float[] values = slot.values;
		double travel = EnergyMotes.travel(age);
		double tailTravel = travel - EnergyMotes.travel(Math.max(0, age - 0.035f));
		for (int p = 0; p < (int) values[35]; p++) {
			int seed = frame.quality == 0 ? p * 3 : frame.quality == 1 ? (p * 5) % EnergyMotes.COUNT : p;
			float opacity = EnergyMotes.opacity(seed, age);
			if (opacity <= 0) continue;
			double reach = EnergyMotes.speed(seed) * travel;
			double tail = EnergyMotes.speed(seed) * tailTravel;
			int base = slot.particle + p * 4;
			values[base] = ox + (float) (ex[seed] * reach);
			values[base + 1] = oy + (float) (ey[seed] * reach);
			values[base + 2] = oz + (float) (ez[seed] * reach);
			values[base + 3] = EnergyMotes.size(seed) * (0.7f + opacity * 0.3f);
			base = slot.tail + p * 4;
			values[base] = (float) (-ex[seed] * tail);
			values[base + 1] = (float) (-ey[seed] * tail);
			values[base + 2] = (float) (-ez[seed] * tail);
			values[base + 3] = opacity;
		}
	}

	@Override
	public boolean expired() {
		return startNanos >= 0L && ageSec >= EnergyMotes.DURATION;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		if (startNanos < 0L) startNanos = System.nanoTime();
		if (!mc.isPaused()) {
			ageSec = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
		}
		if (ageSec >= EnergyMotes.DURATION) return;

		float progress = Mth.clamp(ageSec / EnergyMotes.DURATION, 0.0f, 1.0f);
		float fade = 1.0f - smoothstep(0.45f, 1.0f, progress);
		if (fade <= 0.01f) return;

		float shellRadius = ShockwaveAnimation.radius(ageSec, (float) Shockwave.RADIUS);
		float cx = (float) (origin.x - cameraPos.x);
		float cy = (float) (origin.y - cameraPos.y);
		float cz = (float) (origin.z - cameraPos.z);
		float floorY = cy - 0.88f;

		// 1. Chronosphere-style geodesic voxel orbital rings expanding cleanly to the 5m sphere edge
		float domeFade = 1.0f - smoothstep(0.28f, 0.72f, progress);
		if (domeFade > 0.02f) {
			for (int ring = 0; ring < RING_TILT_COUNT; ring++) {
				float tiltX = ring == 0 ? 0.0f : (ring == 1 ? 0.64f : -0.64f);
				float tiltY = ring * 1.15f;
				float spin = (ring % 2 == 0 ? 1.0f : -1.0f) * ageSec * (2.4f - 1.2f * progress);
				emitDomeRing(poseStack, collector, cx, cy, cz, shellRadius * 0.99f, 16, tiltX, tiltY, spin, domeFade);
			}
		}

		// 2. Massive 3D spherical voxel explosion: 64 tumbling cubes bursting outward with drag + gravity drop
		float travel = (float) (-Math.expm1(-3.4 * ageSec) / 3.4);
		int clusterCount = 8;
		int perCluster = BURST_VOXELS / clusterCount;
		for (int c = 0; c < clusterCount; c++) {
			final int clusterIdx = c;
			float clusterRotY = ageSec * (4.2f + c * 0.45f) + c * 0.8f;
			float clusterRotX = ageSec * (3.1f + (c % 3) * 0.6f) + c * 0.4f;
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
				for (int j = 0; j < perCluster; j++) {
					int i = clusterIdx * perCluster + j;
					float dist = SPEED[i] * travel;
					float px = cx + DIR_X[i] * dist;
					float pz = cz + DIR_Z[i] * dist;
					// Slight gravity sag after initial explosive outward blast
					float rawY = cy + DIR_Y[i] * dist - 1.85f * ageSec * ageSec;
					float py = Math.max(floorY + 0.05f, rawY);

					float size = BASE_SIZE[i] * (0.35f + 0.65f * fade) * Math.min(1.0f, ageSec / 0.03f);
					// Asymmetric cuboid aspect + wobble via cluster orientation feel
					float sx = size * (0.85f + 0.35f * (float) Math.sin(clusterRotY + i));
					float sy = size * (0.85f + 0.35f * (float) Math.cos(clusterRotX + i));
					float sz = size;
					float[] col = voxelColor(COLOR_TIER[i]);
					WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, sx, sy, sz, col[0], col[1], col[2], 0.94f * fade);
				}
			});
		}

		// 3. Expanding ground voxel shock-crown + trailing soft plasma ice-mist cubes
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE, (pose, vc) -> {
			// Ground ring crown
			for (int i = 0; i < 24; i++) {
				float ang = (i / 24.0f) * (float) (Math.PI * 2.0) + ageSec * 0.6f;
				float rx = cx + (float) Math.cos(ang) * shellRadius;
				float rz = cz + (float) Math.sin(ang) * shellRadius;
				float h = (0.14f + 0.12f * (i % 2)) * (1.0f - 0.55f * progress);
				WeaponVoxelRenderer.emitCube(pose, vc, rx, floorY + h, rz, 0.08f, h, 0.08f, 0.52f, 0.92f, 1.0f, 0.72f * fade);
			}
			// Soft trailing ice-plasma mist cubes inside the blast
			for (int i = 0; i < 28; i++) {
				float ang = i * 2.39996f + ageSec * 0.45f;
				float radial = (0.8f + (i % 5) * 0.85f) * (0.35f + 0.65f * progress);
				float px = cx + (float) Math.cos(ang) * radial;
				float pz = cz + (float) Math.sin(ang) * radial;
				float py = Math.max(floorY + 0.08f, cy + (-0.5f + (i % 6) * 0.38f) * (0.4f + 0.6f * progress) + ageSec * 0.35f);
				float s = 0.07f + (i % 3) * 0.03f;
				WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, s, s, s, 0.42f, 0.86f, 1.0f, 0.42f * fade);
			}
		});

		// 4. Additive detonation core flash + ring glint
		if (ageSec < 0.42f) {
			float flash = 1.0f - smoothstep(0.02f, 0.42f, ageSec);
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) -> {
				WeaponVoxelRenderer.emitGlowSprite(pose, vc, cx, cy, cz, 1.35f * (0.4f + 0.6f * flash), 0.78f, 0.95f, 1.0f, 0.82f * flash);
				for (int i = 0; i < 8; i++) {
					float ang = (i / 8.0f) * (float) (Math.PI * 2.0) + ageSec * 1.4f;
					WeaponVoxelRenderer.emitGlowSprite(
							pose, vc,
							cx + (float) Math.cos(ang) * shellRadius,
							cy,
							cz + (float) Math.sin(ang) * shellRadius,
							0.36f * flash, 0.48f, 0.90f, 1.0f, 0.55f * flash
					);
				}
			});
		}
	}

	private void emitDomeRing(PoseStack poseStack, SubmitNodeCollector collector,
			float cx, float cy, float cz, float ringRadius, int count,
			float tiltX, float tiltY, float spin, float alpha) {
		poseStack.pushPose();
		poseStack.translate(cx, cy, cz);
		poseStack.mulPose(new Matrix4f().rotate(new Quaternionf().rotateY(tiltY).rotateX(tiltX).rotateY(spin)));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			for (int i = 0; i < count; i++) {
				float ang = (i / (float) count) * (float) (Math.PI * 2.0);
				float px = (float) Math.cos(ang) * ringRadius;
				float pz = (float) Math.sin(ang) * ringRadius;
				boolean bright = (i % 2 == 0);
				float s = (bright ? 0.095f : 0.065f) * (0.5f + 0.5f * alpha);
				float[] col = voxelColor(bright ? 2 : 0);
				WeaponVoxelRenderer.emitCube(pose, vc, px, 0f, pz, s, s * 0.8f, s, col[0], col[1], col[2], 0.86f * alpha);
			}
		});
		poseStack.popPose();
	}

	private static float[] voxelColor(int tier) {
		return switch (tier) {
			case 0 -> new float[] { 0.20f, 0.58f, 0.98f }; // electric cobalt
			case 1 -> new float[] { 0.42f, 0.88f, 1.00f }; // gravitational cyan
			case 2 -> new float[] { 0.88f, 0.97f, 1.00f }; // glacier white core
			default -> new float[] { 0.14f, 0.30f, 0.56f }; // deep slate-ice shard
		};
	}

	private static float smoothstep(float low, float high, float value) {
		float t = Mth.clamp((value - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
