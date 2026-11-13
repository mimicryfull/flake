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
 * Animated voxel + glow renderer for Vampire ("Упир").
 * Renders articulated glowing red voxel cubes along moving splines plus soft atmospheric glow halos.
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

	/** Emits a 3D glowing voxel cube centered at (cx, cy, cz) with half-extents (hx, hy, hz). */
	public static void emitVoxelCube(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float hx, float hy, float hz,
			float kind, float seed, float pulse, float alpha) {
		if (alpha <= 0.01f || hx <= 0.002f || hy <= 0.002f || hz <= 0.002f) return;
		float x0 = cx - hx, x1 = cx + hx;
		float y0 = cy - hy, y1 = cy + hy;
		float z0 = cz - hz, z1 = cz + hz;

		// +Y (top)
		quad(pose, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, kind, seed, pulse, alpha);
		// -Y (bottom)
		quad(pose, vc, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, kind, seed, pulse, alpha);
		// +Z (south)
		quad(pose, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, kind, seed, pulse, alpha);
		// -Z (north)
		quad(pose, vc, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, kind, seed, pulse, alpha);
		// +X (east)
		quad(pose, vc, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, kind, seed, pulse, alpha);
		// -X (west)
		quad(pose, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, kind, seed, pulse, alpha);
	}

	/** Emits a camera-facing soft red glow halo behind/around voxel clusters (kind >= 0.90). */
	public static void emitGlowHalo(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float radiusX, float radiusY,
			float seed, float pulse, float alpha) {
		if (alpha <= 0.005f || radiusX <= 0.01f) return;
		float distSq = cx * cx + cy * cy + cz * cz;
		if (distSq < 1e-6f) return;

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

		vertex(pose, vc, cx - rvx - uvx, cy - uvy, cz - rvz - uvz, 0f, 0f, 0.95f, seed, pulse, alpha);
		vertex(pose, vc, cx + rvx - uvx, cy - uvy, cz + rvz - uvz, 1f, 0f, 0.95f, seed, pulse, alpha);
		vertex(pose, vc, cx + rvx + uvx, cy + uvy, cz + rvz + uvz, 1f, 1f, 0.95f, seed, pulse, alpha);
		vertex(pose, vc, cx - rvx + uvx, cy + uvy, cz - rvz + uvz, 0f, 1f, 0.95f, seed, pulse, alpha);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float kind, float seed, float pulse, float alpha) {
		vertex(pose, vc, x0, y0, z0, 0f, 0f, kind, seed, pulse, alpha);
		vertex(pose, vc, x1, y1, z1, 1f, 0f, kind, seed, pulse, alpha);
		vertex(pose, vc, x2, y2, z2, 1f, 1f, kind, seed, pulse, alpha);
		vertex(pose, vc, x3, y3, z3, 0f, 1f, kind, seed, pulse, alpha);
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
