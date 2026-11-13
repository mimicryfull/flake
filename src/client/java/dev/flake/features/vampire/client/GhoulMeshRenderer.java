package dev.flake.features.vampire.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.flake.Flake;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Smooth hardware-rasterized 3D blood mesh renderer for Vampire ("Упир").
 * Uses continuous camera-aligned mitered ribbon strips and smooth-SDF billboard orbs/auras.
 */
public final class GhoulMeshRenderer {
	public static final RenderPipeline GHOUL_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "pipeline/ghoul_blood"))
					.withVertexShader(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "core/ghoul_blood"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "core/ghoul_blood"))
					.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
					.withPrimitiveTopology(PrimitiveTopology.QUADS)
					.withCull(false)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.build());

	public static final RenderType GHOUL_RENDER_TYPE = RenderType.create(
			"flake_ghoul_blood",
			RenderSetup.builder(GHOUL_PIPELINE).createRenderSetup());

	private GhoulMeshRenderer() {}

	/**
	 * Emits a continuous, gap-free camera-aligned ribbon strip from packed points (x, y, z, width, alpha).
	 * Adjacent quads share exact edge vertices so curved tendrils have zero kinks or seams.
	 */
	public static void emitSmoothPolyline(PoseStack.Pose pose, VertexConsumer vc,
			float[] pts, int startOffset, int count, float widthScale,
			float kind, float seed, float pulse) {
		if (count < 2) return;
		float prevLx = 0, prevLy = 0, prevLz = 0;
		float prevRx = 0, prevRy = 0, prevRz = 0;
		float prevU = 0, prevA = 0;
		boolean hasPrev = false;

		for (int i = 0; i < count; i++) {
			int cur = startOffset + i * 5;
			float px = pts[cur], py = pts[cur + 1], pz = pts[cur + 2];
			float w = pts[cur + 3] * widthScale;
			float a = pts[cur + 4];

			int prevIdx = startOffset + Math.max(0, i - 1) * 5;
			int nextIdx = startOffset + Math.min(count - 1, i + 1) * 5;
			float tx = pts[nextIdx] - pts[prevIdx];
			float ty = pts[nextIdx + 1] - pts[prevIdx + 1];
			float tz = pts[nextIdx + 2] - pts[prevIdx + 2];
			float tLenSq = tx * tx + ty * ty + tz * tz;
			if (tLenSq < 1e-8f) {
				hasPrev = false;
				continue;
			}

			// Camera-relative position is (px, py, pz), so vector toward camera is (-px, -py, -pz)
			float cx = -px, cy = -py, cz = -pz;
			float sx = ty * cz - tz * cy;
			float sy = tz * cx - tx * cz;
			float sz = tx * cy - ty * cx;
			float sLenSq = sx * sx + sy * sy + sz * sz;
			if (sLenSq < 1e-8f) {
				// Fallback if tangent points straight at camera
				sx = -tz; sy = 0f; sz = tx;
				sLenSq = sx * sx + sz * sz;
				if (sLenSq < 1e-8f) { sx = 1f; sy = 0f; sz = 0f; sLenSq = 1f; }
			}
			float invS = (float) (1.0 / Math.sqrt(sLenSq));
			sx *= invS * w;
			sy *= invS * w;
			sz *= invS * w;

			float lx = px - sx, ly = py - sy, lz = pz - sz;
			float rx = px + sx, ry = py + sy, rz = pz + sz;
			float u = (float) i / (count - 1);

			if (hasPrev && (prevA > 0.005f || a > 0.005f)) {
				vertex(pose, vc, prevLx, prevLy, prevLz, prevU, 0f, kind, seed, pulse, prevA);
				vertex(pose, vc, prevRx, prevRy, prevRz, prevU, 1f, kind, seed, pulse, prevA);
				vertex(pose, vc, rx, ry, rz, u, 1f, kind, seed, pulse, a);
				vertex(pose, vc, lx, ly, lz, u, 0f, kind, seed, pulse, a);
			}

			prevLx = lx; prevLy = ly; prevLz = lz;
			prevRx = rx; prevRy = ry; prevRz = rz;
			prevU = u; prevA = a;
			hasPrev = true;
		}
	}

	/**
	 * Emits a camera-facing billboard quad for smooth-SDF blood droplets (kind ~ 0.75)
	 * or soft atmospheric aura mist (kind >= 0.90).
	 */
	public static void emitBillboardOrb(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float radiusX, float radiusY,
			float kind, float seed, float pulse, float alpha) {
		if (alpha <= 0.005f || radiusX <= 0.0005f) return;
		float distSq = cx * cx + cy * cy + cz * cz;
		if (distSq < 1e-6f) return;

		// Right vector = normalize(cross(toCam, worldUp))
		float toCamX = -cx, toCamY = -cy, toCamZ = -cz;
		float rx = -toCamZ, rz = toCamX;
		float rLenSq = rx * rx + rz * rz;
		float ux, uy, uz;
		if (rLenSq < 1e-6f) {
			rx = 1f; rz = 0f;
			ux = 0f; uy = 0f; uz = 1f;
		} else {
			float invR = (float) (1.0 / Math.sqrt(rLenSq));
			rx *= invR; rz *= invR;
			// Up vector = normalize(cross(right, toCam))
			ux = -(rz * toCamY);
			uy = rz * toCamX - rx * toCamZ;
			uz = rx * toCamY;
			float uLen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
			if (uLen > 1e-5f) {
				ux /= uLen; uy /= uLen; uz /= uLen;
			}
		}

		float rvx = rx * radiusX, rvz = rz * radiusX;
		float uvx = ux * radiusY, uvy = uy * radiusY, uvz = uz * radiusY;

		vertex(pose, vc, cx - rvx - uvx, cy - uvy, cz - rvz - uvz, 0f, 0f, kind, seed, pulse, alpha);
		vertex(pose, vc, cx + rvx - uvx, cy - uvy, cz + rvz - uvz, 1f, 0f, kind, seed, pulse, alpha);
		vertex(pose, vc, cx + rvx + uvx, cy + uvy, cz + rvz + uvz, 1f, 1f, kind, seed, pulse, alpha);
		vertex(pose, vc, cx - rvx + uvx, cy + uvy, cz - rvz + uvz, 0f, 1f, kind, seed, pulse, alpha);
	}

	/** Emits a horizontal arterial floor pulse ring. */
	public static void emitFloorRing(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float innerR, float outerR, int segments,
			float kind, float seed, float pulse, float alpha) {
		if (alpha <= 0.005f || outerR <= innerR) return;
		double step = Math.PI * 2.0 / segments;
		for (int i = 0; i < segments; i++) {
			double a0 = i * step, a1 = (i + 1) * step;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float u0 = (float) i / segments, u1 = (float) (i + 1) / segments;
			vertex(pose, vc, cx + c0 * innerR, cy, cz + s0 * innerR, u0, 0f, kind, seed, pulse, alpha);
			vertex(pose, vc, cx + c0 * outerR, cy, cz + s0 * outerR, u0, 1f, kind, seed, pulse, alpha);
			vertex(pose, vc, cx + c1 * outerR, cy, cz + s1 * outerR, u1, 1f, kind, seed, pulse, alpha);
			vertex(pose, vc, cx + c1 * innerR, cy, cz + s1 * innerR, u1, 0f, kind, seed, pulse, alpha);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc,
			float x, float y, float z, float u, float v,
			float kind, float seed, float pulse, float alpha) {
		vc.addVertex(pose, x, y, z)
				.setUv(u, v)
				.setColor(
						Math.clamp(kind, 0f, 1f),
						Math.clamp(seed, 0f, 1f),
						Math.clamp(pulse, 0f, 1f),
						Math.clamp(alpha, 0f, 1f));
	}
}
