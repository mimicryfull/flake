package dev.flake.features.weather.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.Flake;
import dev.flake.features.weather.SakuraWeather;
import dev.flake.features.weather.SakuraWeatherPayload;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;

/**
 * Lightweight, zero-allocation ambient weather renderer for falling sakura cherry-blossom petals.
 * Petals tumble in 3D inside a rolling camera volume with wind gusts, leaf flutter, and smooth alpha fades.
 */
public final class SakuraWeatherRenderer {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "textures/vfx/sakura_petals.png");

	private static final int MAX_PETALS = 260;
	private static final float H_RADIUS = 24.0f;
	private static final float H_SPAN = H_RADIUS * 2.0f;
	private static final float Y_MIN = -6.0f;
	private static final float Y_MAX = 18.0f;
	private static final float Y_SPAN = Y_MAX - Y_MIN;

	private static final class Petal {
		double x, y, z;
		float fallSpeed;
		float size;
		float phase;
		float flutterFreq;
		float spinSpeed;
		float pitchAmp;
		float rollAmp;
		float tintR, tintG, tintB;
		int sprite; // 0..3 in 2x2 atlas
	}

	private static final Petal[] POOL = new Petal[MAX_PETALS];
	private static boolean poolReady;
	private static boolean enabled;
	private static float intensity = SakuraWeather.DEFAULT_INTENSITY;
	private static ClientLevel lastLevel;
	private static double lastCamX, lastCamY, lastCamZ;
	private static float lastTimeSec = -1.0f;

	private SakuraWeatherRenderer() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SakuraWeatherPayload.TYPE, (payload, context) ->
				context.client().execute(() -> applyPayload(payload)));
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static float intensity() {
		return intensity;
	}

	public static void clear() {
		enabled = false;
		intensity = SakuraWeather.DEFAULT_INTENSITY;
		lastLevel = null;
		lastTimeSec = -1.0f;
	}

	private static void applyPayload(SakuraWeatherPayload payload) {
		var mode = SakuraWeather.Mode.parse(payload.mode());
		enabled = mode == SakuraWeather.Mode.SAKURA && payload.intensity() > 0.0f;
		intensity = enabled ? SakuraWeather.clampIntensity(payload.intensity()) : SakuraWeather.DEFAULT_INTENSITY;
		var mc = Minecraft.getInstance();
		if (mc != null && mc.gui != null && mc.gui.screen() instanceof dev.flake.features.lobby.client.FlakeScreen screen) {
			screen.refresh();
		}
	}

	private static void ensurePool(Vec3 cam) {
		if (poolReady) return;
		poolReady = true;
		var rng = new Random(0x53414B555241L);
		for (int i = 0; i < MAX_PETALS; i++) {
			Petal p = new Petal();
			p.x = cam.x + (rng.nextDouble() * 2.0 - 1.0) * H_RADIUS;
			p.y = cam.y + Y_MIN + rng.nextDouble() * Y_SPAN;
			p.z = cam.z + (rng.nextDouble() * 2.0 - 1.0) * H_RADIUS;
			p.fallSpeed = 1.05f + rng.nextFloat() * 0.90f;
			p.size = 0.095f + rng.nextFloat() * 0.085f;
			p.phase = rng.nextFloat() * Mth.TWO_PI;
			p.flutterFreq = 1.45f + rng.nextFloat() * 1.35f;
			p.spinSpeed = (rng.nextBoolean() ? 1f : -1f) * (0.7f + rng.nextFloat() * 1.1f);
			p.pitchAmp = 0.45f + rng.nextFloat() * 0.45f;
			p.rollAmp = 0.55f + rng.nextFloat() * 0.50f;
			p.sprite = i & 3;
			float shade = rng.nextFloat();
			p.tintR = 1.0f;
			p.tintG = Mth.lerp(shade, 0.88f, 0.99f);
			p.tintB = Mth.lerp(shade, 0.93f, 1.0f);
			POOL[i] = p;
		}
	}

	public static void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cam) {
		if (!enabled) return;
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;

		ensurePool(cam);

		float nowSec = (System.nanoTime() & 0x3FFFFFFFFFL) * 1e-9f;
		if (level != lastLevel) {
			lastLevel = level;
			recenterPool(cam);
			lastTimeSec = nowSec;
		}
		float dt = lastTimeSec < 0f ? 0.016f : Math.min(0.10f, Math.max(0.0f, nowSec - lastTimeSec));
		lastTimeSec = nowSec;
		lastCamX = cam.x;
		lastCamY = cam.y;
		lastCamZ = cam.z;

		int activeCount = Mth.clamp(Math.round(135.0f * intensity), 24, MAX_PETALS);
		stepPetals(cam, nowSec, dt, activeCount);

		final int renderCount = activeCount;
		final float time = nowSec;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TEXTURE), (pose, vc) -> {
			BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
			Matrix3f rot = new Matrix3f();
			for (int i = 0; i < renderCount; i++) {
				Petal p = POOL[i];
				float dx = (float) (p.x - cam.x);
				float dy = (float) (p.y - cam.y);
				float dz = (float) (p.z - cam.z);

				// Soft cylindrical distance & vertical top/bottom envelope fade
				float horizDistSq = dx * dx + dz * dz;
				if (horizDistSq >= H_RADIUS * H_RADIUS) continue;
				float distFade = 1.0f - Mth.clamp((Mth.sqrt(horizDistSq) - (H_RADIUS - 6.0f)) / 6.0f, 0.0f, 1.0f);
				float topFade = Mth.clamp((Y_MAX - dy) / 3.5f, 0.0f, 1.0f);
				float botFade = Mth.clamp((dy - Y_MIN) / 2.5f, 0.0f, 1.0f);
				float alpha = distFade * topFade * botFade * 0.92f;
				if (alpha <= 0.02f) continue;

				mpos.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
				if (!level.getBlockState(mpos).getCollisionShape(level, mpos).isEmpty()) continue;

				int rawLight = LightCoordsUtil.getLightCoords(level, mpos);
				int packedLight = LightCoordsUtil.pack(
						Math.max(5, LightCoordsUtil.block(rawLight)),
						Math.max(11, LightCoordsUtil.sky(rawLight)));

				emitPetalQuad(pose, vc, rot, p, dx, dy, dz, time, packedLight, alpha);
			}
		});
	}

	private static void recenterPool(Vec3 cam) {
		for (Petal p : POOL) {
			p.x = wrapCoord(p.x, cam.x, H_RADIUS, H_SPAN);
			p.y = wrapCoord(p.y, cam.y + (Y_MIN + Y_MAX) * 0.5, Y_SPAN * 0.5, Y_SPAN);
			p.z = wrapCoord(p.z, cam.z, H_RADIUS, H_SPAN);
		}
	}

	private static void stepPetals(Vec3 cam, float nowSec, float dt, int count) {
		if (dt <= 0.0f) return;
		float breezeX = 1.15f + 0.55f * Mth.sin(nowSec * 0.27f) + 0.25f * Mth.cos(nowSec * 0.63f);
		float breezeZ = 0.55f + 0.45f * Mth.cos(nowSec * 0.21f);
		double midY = cam.y + (Y_MIN + Y_MAX) * 0.5;

		for (int i = 0; i < count; i++) {
			Petal p = POOL[i];
			float fArg = p.phase + nowSec * p.flutterFreq;
			float gust = Mth.sin((float) (p.x * 0.09 + p.z * 0.07) + nowSec * 1.1f);
			float swayX = Mth.sin(fArg) * 0.65f + gust * 0.35f;
			float swayZ = Mth.cos(fArg * 0.85f) * 0.55f + gust * 0.25f;
			float updraft = 1.0f - 0.35f * Mth.sin(fArg * 1.3f);

			p.x += (breezeX + swayX) * dt;
			p.z += (breezeZ + swayZ) * dt;
			p.y -= p.fallSpeed * updraft * dt;

			p.x = wrapCoord(p.x, cam.x, H_RADIUS, H_SPAN);
			p.z = wrapCoord(p.z, cam.z, H_RADIUS, H_SPAN);
			p.y = wrapCoord(p.y, midY, Y_SPAN * 0.5, Y_SPAN);
		}
	}

	private static double wrapCoord(double value, double center, double halfSpan, double span) {
		double min = center - halfSpan;
		double rel = (value - min) % span;
		if (rel < 0.0) rel += span;
		return min + rel;
	}

	private static void emitPetalQuad(PoseStack.Pose pose, VertexConsumer vc, Matrix3f rot,
			Petal p, float dx, float dy, float dz, float time, int light, float alpha) {
		float arg = p.phase + time * p.flutterFreq;
		float yaw = p.phase + time * p.spinSpeed;
		float pitch = Mth.sin(arg) * p.pitchAmp;
		float roll = Mth.cos(arg * 1.15f) * p.rollAmp;

		rot.identity().rotateY(yaw).rotateX(pitch).rotateZ(roll);
		// Basis vectors (s * X, s * Z) and normal (+Y) rotated cleanly without allocations
		float s = p.size;
		float ux = rot.m00 * s, uy = rot.m01 * s, uz = rot.m02 * s;
		float vx = rot.m20 * s, vy = rot.m21 * s, vz = rot.m22 * s;
		float nx = rot.m10, ny = rot.m11, nz = rot.m12;

		int col = (p.sprite & 1);
		int row = (p.sprite >> 1) & 1;
		float u0 = col * 0.5f;
		float u1 = u0 + 0.5f;
		float v0 = row * 0.5f;
		float v1 = v0 + 0.5f;

		int a = Mth.clamp(Math.round(alpha * 255.0f), 0, 255);
		int r = Mth.clamp(Math.round(p.tintR * 255.0f), 0, 255);
		int g = Mth.clamp(Math.round(p.tintG * 255.0f), 0, 255);
		int b = Mth.clamp(Math.round(p.tintB * 255.0f), 0, 255);
		int argb = (a << 24) | (r << 16) | (g << 8) | b;

		vc.addVertex(pose, dx - ux - vx, dy - uy - vy, dz - uz - vz)
				.setColor(argb).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(pose, dx + ux - vx, dy + uy - vy, dz + uz - vz)
				.setColor(argb).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(pose, dx + ux + vx, dy + uy + vy, dz + uz + vz)
				.setColor(argb).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(pose, dx - ux + vx, dy - uy + vy, dz - uz + vz)
				.setColor(argb).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}
}
