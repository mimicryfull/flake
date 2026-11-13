package dev.flake.features.demolitionist.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RadialActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/**
 * Shared thermal voxel + shockwave actor for Demolitionist's Proximity Mine detonation and Blast Jump launch.
 * Combines a shock-ring matching server AOE radius, ballistic iron shrapnel, fire/smoke plume voxels, and additive bloom.
 */
public final class DemolitionExplosionActor extends RadialActor {
	private static final float MINE_DURATION_SEC = 0.85f;
	private static final float JUMP_DURATION_SEC = 0.72f;
	private static final float MINE_MAX_RADIUS = 2.8f;
	private static final float JUMP_MAX_RADIUS = 3.5f;

	private static final int SHRAPNEL_COUNT = 20;
	private static final int PLUME_COUNT = 42;
	private static final int RING_COUNT = 32;

	private static final float[] SHRAPNEL_DX = new float[SHRAPNEL_COUNT];
	private static final float[] SHRAPNEL_DY = new float[SHRAPNEL_COUNT];
	private static final float[] SHRAPNEL_DZ = new float[SHRAPNEL_COUNT];
	private static final float[] SHRAPNEL_SPEED = new float[SHRAPNEL_COUNT];

	private static final float[] PLUME_ANG = new float[PLUME_COUNT];
	private static final float[] PLUME_RADIAL = new float[PLUME_COUNT];
	private static final float[] PLUME_UP = new float[PLUME_COUNT];
	private static final float[] PLUME_SIZE = new float[PLUME_COUNT];

	static {
		for (int i = 0; i < SHRAPNEL_COUNT; i++) {
			float angle = i * 2.3999632f + 0.4f;
			float up = 0.35f + 0.65f * hash(i * 3.17f);
			float horiz = (float) Math.sqrt(Math.max(0.05f, 1.0f - up * up * 0.45f));
			SHRAPNEL_DX[i] = (float) Math.cos(angle) * horiz;
			SHRAPNEL_DY[i] = up;
			SHRAPNEL_DZ[i] = (float) Math.sin(angle) * horiz;
			SHRAPNEL_SPEED[i] = 4.8f + 5.6f * hash(i * 5.41f);
		}
		for (int i = 0; i < PLUME_COUNT; i++) {
			PLUME_ANG[i] = i * 2.3999632f;
			PLUME_RADIAL[i] = 0.4f + 2.1f * hash(i * 2.71f);
			PLUME_UP[i] = 1.6f + 3.4f * hash(i * 4.33f);
			PLUME_SIZE[i] = 0.055f + 0.065f * hash(i * 6.13f);
		}
	}

	private final boolean mineDetonation;
	private final Vec3 launchDir;
	private final VfxTimeline geometryTime = new VfxTimeline();
	private float geoAgeSec;

	public DemolitionExplosionActor(CombatVfxPayload payload) {
		super(payload);
		this.mineDetonation = payload.phase() == CombatVfxPayload.Phase.MINE_DETONATE;
		Vec3 rawAim = payload.aim();
		this.launchDir = (!mineDetonation && rawAim.lengthSqr() > 0.001) ? rawAim.normalize() : new Vec3(0, 1, 0);
	}

	@Override protected float duration() { return mineDetonation ? MINE_DURATION_SEC : JUMP_DURATION_SEC; }

	@Override
	protected float radius(float age) {
		float maxR = mineDetonation ? MINE_MAX_RADIUS : JUMP_MAX_RADIUS;
		return 0.35f + (maxR - 0.35f) * (1.0f - (float) Math.exp(-age * 7.8));
	}

	@Override
	protected float opacity(float age) {
		float dur = duration();
		float fade = Math.clamp(1.0f - age / dur, 0f, 1f);
		return Math.min(1.0f, age / 0.03f) * fade * fade;
	}

	@Override protected int style() { return 16; }
	@Override protected float styleExtra(float age) { return mineDetonation ? 1.0f : 0.0f; }

	@Override
	public boolean expired() {
		return super.expired() && geoAgeSec >= duration();
	}

	@Override
	protected void details(RenderFrame frame, SlotWriter slot, float age, float x, float y, float z) {
		geoAgeSec = age;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		geoAgeSec = geometryTime.sample(System.nanoTime(), mc.isPaused());
		float dur = duration();
		if (geoAgeSec >= dur) return;

		double dx = origin.x - cameraPos.x;
		double dy = origin.y - cameraPos.y;
		double dz = origin.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float cx = (float) dx, cy = (float) dy, cz = (float) dz;
		float progress = Math.clamp(geoAgeSec / dur, 0f, 1f);
		float waveRadius = radius(geoAgeSec);
		float opacity = Math.clamp(1.0f - progress * progress, 0f, 1f);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitCoreFlashRingAndShrapnel(pose, vc, cx, cy, cz, waveRadius, progress, opacity));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitPlumeAndJet(pose, vc, cx, cy, cz, progress, opacity));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitBlastGlow(pose, vc, cx, cy, cz, waveRadius, progress, opacity));
	}

	private void emitCoreFlashRingAndShrapnel(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress, float opacity) {
		// 1. Rapid initial white-gold fireball core cube
		if (geoAgeSec < 0.22f) {
			float f = 1.0f - geoAgeSec / 0.22f;
			float coreR = (mineDetonation ? 0.56f : 0.44f) * (0.4f + 0.9f * (geoAgeSec / 0.22f));
			WeaponVoxelRenderer.emitCube(pose, vc, cx, cy + 0.28f, cz, coreR, coreR * 0.85f, coreR,
					1.0f, 0.86f * f + 0.28f * (1f - f), 0.35f * f, f * 0.95f);
		}

		// 2. Ground shock-ring matching server AOE radius
		float ringFade = opacity * (1.0f - 0.35f * progress);
		for (int i = 0; i < RING_COUNT; i++) {
			float ang = (i / (float) RING_COUNT) * (float) (Math.PI * 2.0) + progress * 0.3f;
			float jitter = 0.92f + 0.16f * hash(i * 1.91f);
			float r = waveRadius * jitter;
			float vx = cx + (float) Math.cos(ang) * r;
			float vz = cz + (float) Math.sin(ang) * r;
			float h = (0.055f + 0.085f * hash(i * 4.13f)) * (1.0f - 0.65f * progress);
			float w = 0.095f * (1.0f - 0.3f * progress);
			boolean hot = (i % 3 == 0);
			float cr = hot ? 1.0f : (mineDetonation ? 0.88f : 0.96f);
			float cg = hot ? (0.58f - 0.30f * progress) : (0.28f - 0.16f * progress);
			float cb = hot ? 0.14f : 0.07f;
			WeaponVoxelRenderer.emitCube(pose, vc, vx, cy + 0.03f + h * 0.5f, vz, w, h, w, cr, cg, cb, ringFade);
		}

		// 3. Ballistic hot shrapnel chunks (tumble and settle near floor)
		float travel = (1.0f - (float) Math.exp(-geoAgeSec * 4.5)) / 4.5f;
		float gravity = 9.2f * geoAgeSec * geoAgeSec * 0.5f;
		float shrapFade = Math.clamp(1.0f - progress * 1.15f, 0f, 1f);
		if (shrapFade > 0.02f) {
			for (int i = 0; i < SHRAPNEL_COUNT; i++) {
				float speed = SHRAPNEL_SPEED[i] * (mineDetonation ? 1.05f : 0.85f);
				float px = cx + SHRAPNEL_DX[i] * speed * travel;
				float py = Math.max(cy + 0.04f, cy + 0.18f + SHRAPNEL_DY[i] * speed * travel - gravity);
				float pz = cz + SHRAPNEL_DZ[i] * speed * travel;
				float sz = (0.030f + 0.022f * hash(i * 7.3f)) * (0.55f + 0.45f * shrapFade);
				boolean ember = (i % 2 == 0);
				WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, sz, sz * 0.8f, sz,
						ember ? 1.0f : 0.24f, ember ? 0.45f * shrapFade : 0.22f, ember ? 0.09f : 0.22f, shrapFade);
			}
		}
	}

	private void emitPlumeAndJet(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float progress, float opacity) {
		// Rising billowing fire-to-smoke voxel column
		float dragT = (1.0f - (float) Math.exp(-geoAgeSec * 3.6)) / 3.6f;
		for (int i = 0; i < PLUME_COUNT; i++) {
			float phaseShift = hash(i * 1.73f) * 0.12f;
			float localAge = Math.max(0f, geoAgeSec - phaseShift);
			float localProg = Math.clamp(localAge / (duration() - phaseShift), 0f, 1f);
			if (localProg <= 0.01f || localProg >= 0.99f) continue;

			float ang = PLUME_ANG[i] + localProg * 0.9f;
			float radial = PLUME_RADIAL[i] * (0.28f + 0.72f * (float) Math.sqrt(localProg));
			float px = cx + (float) Math.cos(ang) * radial;
			float pz = cz + (float) Math.sin(ang) * radial;
			float py = cy + 0.12f + PLUME_UP[i] * dragT * (mineDetonation ? 1.05f : 0.78f);

			float sz = PLUME_SIZE[i] * (0.5f + 0.85f * localProg) * (1.0f - 0.3f * localProg);
			float heat = Math.clamp(1.0f - localProg * 1.45f, 0f, 1f);
			// Hot golden-orange flame cooling into charcoal soot smoke
			float r = 0.18f + 0.82f * heat;
			float g = 0.17f + (mineDetonation ? 0.46f : 0.62f) * heat * heat;
			float b = 0.17f + 0.12f * heat * heat * heat;
			float a = (1.0f - localProg * localProg) * 0.84f * opacity;
			WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, sz, sz, sz, r, g, b, a);
		}

		// Rocket-jump propulsion trail along launch vector for Blast Jump
		if (!mineDetonation && geoAgeSec < 0.42f) {
			float wakeFade = 1.0f - geoAgeSec / 0.42f;
			for (int k = 0; k < 14; k++) {
				float dist = 0.25f + (k / 13.0f) * (1.2f + geoAgeSec * 5.2f);
				float sway = 0.14f * (hash(k * 9.1f) - 0.5f);
				float px = cx + (float) launchDir.x * dist + sway;
				float py = cy + 0.35f + (float) launchDir.y * dist;
				float pz = cz + (float) launchDir.z * dist - sway;
				float s = (0.042f + 0.032f * (1.0f - k / 13.0f)) * wakeFade;
				WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, s, s, s,
						1.0f, 0.52f + 0.35f * (1.0f - k / 13.0f), 0.12f, wakeFade * 0.85f);
			}
		}
	}

	private void emitBlastGlow(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float waveRadius, float progress, float opacity) {
		float coreFlash = (float) Math.exp(-geoAgeSec * 8.5) * opacity;
		if (coreFlash > 0.01f) {
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, cx, cy + 0.45f, cz,
					(mineDetonation ? 2.4f : 2.1f) * (0.65f + 0.55f * progress),
					1.0f, mineDetonation ? 0.52f : 0.68f, 0.16f, coreFlash * 0.72f);
		}
		int rimGlows = 12;
		float rimAlpha = opacity * 0.30f;
		for (int i = 0; i < rimGlows; i++) {
			float ang = (i / (float) rimGlows) * (float) (Math.PI * 2.0);
			float gx = cx + (float) Math.cos(ang) * waveRadius * 0.92f;
			float gz = cz + (float) Math.sin(ang) * waveRadius * 0.92f;
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, gx, cy + 0.16f, gz,
					0.85f * (1.0f - 0.25f * progress), 1.0f, 0.42f, 0.10f, rimAlpha);
		}
	}

	private static float hash(float n) {
		float x = (float) Math.sin(n) * 43758.5453f;
		return x - (float) Math.floor(x);
	}
}
