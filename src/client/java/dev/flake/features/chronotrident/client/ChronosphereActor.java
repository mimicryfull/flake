package dev.flake.features.chronotrident.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.chronotrident.ChronosphereAnimation;
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
 * Purple Dota 2 Chronosphere hybrid VFX:
 * - Post-shader (`style = 15`): Concussed Gravitational Dissonance dome reimagined in cosmic violet/amethyst
 *   with rim refraction, meridian clock ticks, suspended energy motes, and collapse singularity.
 * - World-space voxels: 3 counter-rotating tilted orbital voxel rings, a 12-hour ground clock dial
 *   with time-frozen stutter hands, and suspended lavender/violet crystal shards.
 */
public final class ChronosphereActor extends RadialActor {
	private static final int MOTE_COUNT = 12;
	private static final float[] MOTE_ANGLES = new float[MOTE_COUNT];
	private static final float[] MOTE_HEIGHTS = new float[MOTE_COUNT];
	private static final float[] MOTE_RADII = new float[MOTE_COUNT];

	static {
		for (int i = 0; i < MOTE_COUNT; i++) {
			MOTE_ANGLES[i] = i * 2.39996f;
			MOTE_HEIGHTS[i] = -0.35f + ((i * 37) % 100) / 100.0f * 2.35f;
			MOTE_RADII[i] = 0.34f + ((i * 53) % 100) / 100.0f * 0.56f;
		}
	}

	private long startNanos = -1L;
	private float ageSec;
	private float lastAgeSec;
	private float victimOverlay;

	public ChronosphereActor(CombatVfxPayload payload) {
		super(payload);
		ChronoPlantPose.start(this.sourceId);
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.phase() == CombatVfxPayload.Phase.CHRONOSPHERE && payload.sourceId() == sourceId;
	}

	@Override protected float duration() { return ChronosphereAnimation.DURATION_SECONDS; }
	@Override protected float radius(float age) { return ChronosphereAnimation.radius(age); }
	@Override protected float opacity(float age) { return ChronosphereAnimation.opacity(age); }
	@Override protected int style() { return 15; }
	@Override protected float styleExtra(float age) { return ChronosphereAnimation.collapseProgress(age); }

	@Override
	protected void details(RenderFrame frame, SlotWriter slot, float age, float ox, float oy, float oz) {
		if (startNanos < 0L) startNanos = frame.nanos;
		ageSec = age;

		float currentRadius = radius(age);
		float alpha = opacity(age);
		float dt = Math.max(0.0f, age - lastAgeSec);
		lastAgeSec = age;

		boolean stunnedVictim = false;
		if (frame.client.player != null && frame.client.player.getId() != sourceId) {
			var p = frame.client.player;
			double distSq = p.position().add(0.0, p.getBbHeight() * 0.5, 0.0).distanceToSqr(origin);
			double rCheck = currentRadius + 0.25;
			stunnedVictim = distSq <= rCheck * rCheck;
		}
		float targetOverlay = stunnedVictim ? 1.0f : 0.0f;
		float blendRate = Math.min(1.0f, dt * 10.0f);
		victimOverlay = victimOverlay + (targetOverlay - victimOverlay) * blendRate;
		slot.values[slot.material] = victimOverlay * alpha;

		float[] values = slot.values;
		int count = Math.min(MOTE_COUNT, (int) values[35]);
		for (int p = 0; p < count; p++) {
			float angle = MOTE_ANGLES[p] + age * (0.28f + (p % 3) * 0.10f);
			float r = currentRadius * MOTE_RADII[p];
			float px = ox + (float) Math.cos(angle) * r;
			float pz = oz + (float) Math.sin(angle) * r;
			float py = oy + MOTE_HEIGHTS[p] * (currentRadius / ChronosphereAnimation.MAX_RADIUS)
					+ (float) Math.sin(age * 2.2f + p) * 0.08f;

			// Short orbital energy streak tangent to the sphere
			float tx = -(float) Math.sin(angle) * 0.42f;
			float tz = (float) Math.cos(angle) * 0.42f;
			float ty = 0.14f * (float) Math.cos(age * 1.8f + p);

			int base = slot.particle + p * 4;
			values[base] = px;
			values[base + 1] = py;
			values[base + 2] = pz;
			values[base + 3] = 0.055f + (p % 3) * 0.015f;

			base = slot.tail + p * 4;
			values[base] = tx;
			values[base + 1] = ty;
			values[base + 2] = tz;
			values[base + 3] = alpha * (0.65f + 0.35f * (float) Math.sin(age * 4.0f + p));
		}
	}

	@Override
	public boolean expired() {
		return startNanos >= 0L && ageSec >= ChronosphereAnimation.DURATION_SECONDS;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		if (startNanos < 0L) startNanos = System.nanoTime();
		if (!mc.isPaused()) {
			ageSec = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
		}
		if (ageSec >= ChronosphereAnimation.DURATION_SECONDS) return;

		float r = ChronosphereAnimation.radius(ageSec);
		float alpha = ChronosphereAnimation.opacity(ageSec);
		if (alpha <= 0.01f || r <= 0.08f) return;

		float cx = (float) (origin.x - cameraPos.x);
		float cy = (float) (origin.y - cameraPos.y);
		float cz = (float) (origin.z - cameraPos.z);
		float floorY = cy - 0.84f;

		// 1. Three tilted gyroscopic voxel orbital rings hugging the Chronosphere shell
		emitOrbitalRing(poseStack, collector, cx, cy, cz, r * 0.99f, 16, 0.0f, 0.0f, ageSec * 0.55f, alpha, 0);
		emitOrbitalRing(poseStack, collector, cx, cy, cz, r * 0.985f, 14, 0.66f, 0.25f, -ageSec * 0.72f, alpha, 1);
		emitOrbitalRing(poseStack, collector, cx, cy, cz, r * 0.985f, 14, -0.66f, 1.82f, ageSec * 0.64f, alpha, 2);

		// 2. Ground 12-hour voxel clockwork dial & frozen-time stuttering hands
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitClockworkGround(pose, vc, cx, floorY, cz, r, ageSec, alpha));

		// 3. Suspended time-frozen lavender/violet crystal voxels inside the dome
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitFrozenTimeVoxels(pose, vc, cx, cy, cz, r, ageSec, alpha));

		// 4. Additive violet glow sprites on the 12 clock markers + cast/collapse core flare
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitChronoGlows(pose, vc, cx, cy, floorY, cz, r, ageSec, alpha));
	}

	private void emitOrbitalRing(PoseStack poseStack, SubmitNodeCollector collector,
			float cx, float cy, float cz, float ringRadius, int count,
			float tiltX, float tiltY, float spin, float alpha, int ringIndex) {
		poseStack.pushPose();
		poseStack.translate(cx, cy, cz);
		poseStack.mulPose(new Matrix4f().rotate(
				new Quaternionf().rotateY(tiltY).rotateX(tiltX).rotateY(spin)
		));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			float scaleFactor = ringRadius / ChronosphereAnimation.MAX_RADIUS;
			for (int i = 0; i < count; i++) {
				float ang = (i / (float) count) * (float) (Math.PI * 2.0);
				float px = (float) Math.cos(ang) * ringRadius;
				float pz = (float) Math.sin(ang) * ringRadius;
				float py = (float) Math.sin(ang * 3.0f + ageSec * 2.2f + ringIndex) * 0.08f * scaleFactor;

				boolean bright = (i % 2 == 0);
				float s = (bright ? 0.095f : 0.068f) * (0.55f + 0.45f * scaleFactor);
				float cr = bright ? 0.78f : 0.38f;
				float cg = bright ? 0.34f : 0.10f;
				float cb = bright ? 1.00f : 0.72f;
				WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, s, s * 0.75f, s, cr, cg, cb, 0.90f * alpha);
			}
		});
		poseStack.popPose();
	}

	private void emitClockworkGround(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float floorY, float cz, float r, float t, float alpha) {
		float scaleFactor = Mth.clamp(r / ChronosphereAnimation.MAX_RADIUS, 0.1f, 1.0f);
		float dialRadius = r * 0.92f;

		// 12 perimeter clock rune pillars
		for (int i = 0; i < 12; i++) {
			float ang = (i / 12.0f) * (float) (Math.PI * 2.0);
			float px = cx + (float) Math.cos(ang) * dialRadius;
			float pz = cz + (float) Math.sin(ang) * dialRadius;
			boolean cardinal = (i % 3 == 0);
			float h = (cardinal ? 0.24f : 0.15f) * scaleFactor;
			float w = (cardinal ? 0.085f : 0.060f) * (0.6f + 0.4f * scaleFactor);
			WeaponVoxelRenderer.emitCube(
					pose, vc, px, floorY + h, pz, w, h, w,
					cardinal ? 0.86f : 0.60f,
					cardinal ? 0.44f : 0.18f,
					1.00f,
					0.92f * alpha
			);
		}

		// Time-frozen clock hands near floor center: tick-stutter motion (mostly frozen, tiny pulse)
		float stepCycle = (t * 1.4f) % 1.0f;
		float jitter = stepCycle < 0.14f ? (float) Math.sin(stepCycle / 0.14f * Math.PI) * 0.06f : 0.0f;
		float hourAngle = 0.72f + jitter * 0.4f;
		float minuteAngle = -1.35f + jitter;

		// Short hour hand (4 voxel segments)
		for (int s = 1; s <= 4; s++) {
			float d = s * 0.34f * scaleFactor;
			float hx = cx + (float) Math.cos(hourAngle) * d;
			float hz = cz + (float) Math.sin(hourAngle) * d;
			float size = 0.065f * (0.6f + 0.4f * scaleFactor);
			WeaponVoxelRenderer.emitCube(pose, vc, hx, floorY + 0.07f, hz, size, 0.045f, size, 0.74f, 0.28f, 0.98f, 0.88f * alpha);
		}

		// Long minute hand (7 voxel segments)
		for (int s = 1; s <= 7; s++) {
			float d = s * 0.34f * scaleFactor;
			float mx = cx + (float) Math.cos(minuteAngle) * d;
			float mz = cz + (float) Math.sin(minuteAngle) * d;
			float size = 0.052f * (0.6f + 0.4f * scaleFactor);
			WeaponVoxelRenderer.emitCube(pose, vc, mx, floorY + 0.085f, mz, size, 0.038f, size, 0.92f, 0.66f, 1.00f, 0.90f * alpha);
		}
	}

	private void emitFrozenTimeVoxels(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float r, float t, float alpha) {
		float scaleFactor = Mth.clamp(r / ChronosphereAnimation.MAX_RADIUS, 0.08f, 1.0f);
		for (int i = 0; i < 28; i++) {
			float ang = i * 2.39996f + 0.15f * (float) Math.sin(t * 0.4f + i);
			float radialFrac = 0.24f + ((i * 41) % 100) / 100.0f * 0.68f;
			float heightFrac = -0.35f + ((i * 29) % 100) / 100.0f * 0.95f;
			float dist = r * radialFrac;

			// Suspended almost motionless, vibrating subtly like frozen dust in stopped time
			float microHover = (float) Math.sin(t * 1.5f + i * 1.3f) * 0.035f;
			float vx = cx + (float) Math.cos(ang) * dist;
			float vz = cz + (float) Math.sin(ang) * dist;
			float vy = cy + heightFrac * r * 0.72f + microHover;

			float size = (0.048f + (i % 3) * 0.020f) * (0.55f + 0.45f * scaleFactor);
			boolean pale = (i % 3 == 0);
			WeaponVoxelRenderer.emitCube(
					pose, vc, vx, vy, vz, size, size * 1.25f, size,
					pale ? 0.90f : 0.62f,
					pale ? 0.66f : 0.22f,
					1.00f,
					0.62f * alpha
			);
		}
	}

	private void emitChronoGlows(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float floorY, float cz, float r, float t, float alpha) {
		float scaleFactor = Mth.clamp(r / ChronosphereAnimation.MAX_RADIUS, 0.1f, 1.0f);
		float dialRadius = r * 0.92f;

		for (int i = 0; i < 12; i++) {
			float ang = (i / 12.0f) * (float) (Math.PI * 2.0);
			float px = cx + (float) Math.cos(ang) * dialRadius;
			float pz = cz + (float) Math.sin(ang) * dialRadius;
			float pulse = 0.75f + 0.25f * (float) Math.sin(t * 3.8f + i * 0.52f);
			WeaponVoxelRenderer.emitGlowSprite(
					pose, vc, px, floorY + 0.18f * scaleFactor, pz,
					(i % 3 == 0 ? 0.34f : 0.22f) * scaleFactor,
					0.82f, 0.38f, 1.00f,
					0.46f * pulse * alpha
			);
		}

		// Cast opening burst + collapse singularity core flare
		float castFlare = Math.max(0.0f, 1.0f - t / 0.42f);
		float collapseFlare = ChronosphereAnimation.collapseProgress(t);
		float centerFlare = Math.max(castFlare, collapseFlare);
		if (centerFlare > 0.01f) {
			WeaponVoxelRenderer.emitGlowSprite(
					pose, vc, cx, cy, cz,
					0.95f * (0.4f + 0.6f * centerFlare),
					0.92f, 0.58f, 1.00f,
					0.72f * centerFlare * alpha
			);
		}
	}
}
