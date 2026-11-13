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
 * Hardware-rasterized 3D blood mesh renderer for Vampire ("Упир").
 * Replaces per-pixel fullscreen raymarching with crossed 3D ribbons, octahedron droplets, and floor rings.
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
					.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
					.build());

	public static final RenderType GHOUL_RENDER_TYPE = RenderType.create(
			"flake_ghoul_blood",
			RenderSetup.builder(GHOUL_PIPELINE).createRenderSetup());

	private GhoulMeshRenderer() {}

	/** Emits two orthogonal crossed quads along segment A->B so the ribbon has 3D volume from all viewing angles. */
	public static void emitCrossRibbonSegment(PoseStack.Pose pose, VertexConsumer vc,
			float ax, float ay, float az, float bx, float by, float bz,
			float widthA, float widthB, float u0, float u1,
			float kind, float seed, float pulse, float alpha) {
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		float lenSq = dx * dx + dy * dy + dz * dz;
		if (lenSq < 1e-6f || alpha <= 0.005f) return;
		float invLen = (float) (1.0 / Math.sqrt(lenSq));
		float tx = dx * invLen, ty = dy * invLen, tz = dz * invLen;

		// Build orthonormal basis (n1, n2) perpendicular to segment tangent t
		float ux = Math.abs(ty) < 0.92f ? 0f : 1f;
		float uy = Math.abs(ty) < 0.92f ? 1f : 0f;
		float uz = 0f;
		float n1x = ty * uz - tz * uy;
		float n1y = tz * ux - tx * uz;
		float n1z = tx * uy - ty * ux;
		float n1Len = (float) Math.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
		if (n1Len < 1e-5f) return;
		n1x /= n1Len; n1y /= n1Len; n1z /= n1Len;

		float n2x = ty * n1z - tz * n1y;
		float n2y = tz * n1x - tx * n1z;
		float n2z = tx * n1y - ty * n1x;

		emitQuadStrip(pose, vc, ax, ay, az, bx, by, bz, n1x, n1y, n1z, widthA, widthB, u0, u1, kind, seed, pulse, alpha);
		emitQuadStrip(pose, vc, ax, ay, az, bx, by, bz, n2x, n2y, n2z, widthA * 0.85f, widthB * 0.85f, u0, u1, kind, seed, pulse, alpha * 0.85f);
	}

	private static void emitQuadStrip(PoseStack.Pose pose, VertexConsumer vc,
			float ax, float ay, float az, float bx, float by, float bz,
			float nx, float ny, float nz, float wa, float wb,
			float u0, float u1, float kind, float seed, float pulse, float alpha) {
		float a0x = ax - nx * wa, a0y = ay - ny * wa, a0z = az - nz * wa;
		float a1x = ax + nx * wa, a1y = ay + ny * wa, a1z = az + nz * wa;
		float b0x = bx - nx * wb, b0y = by - ny * wb, b0z = bz - nz * wb;
		float b1x = bx + nx * wb, b1y = by + ny * wb, b1z = bz + nz * wb;

		vertex(pose, vc, a0x, a0y, a0z, u0, 0f, kind, seed, pulse, alpha);
		vertex(pose, vc, a1x, a1y, a1z, u0, 1f, kind, seed, pulse, alpha);
		vertex(pose, vc, b1x, b1y, b1z, u1, 1f, kind, seed, pulse, alpha);
		vertex(pose, vc, b0x, b0y, b0z, u1, 0f, kind, seed, pulse, alpha);
	}

	/** Emits a 3D octahedron (blood droplet or siphon knot) using 8 degenerate-quad triangles. */
	public static void emitOctahedron(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float rx, float ry, float rz,
			float kind, float seed, float pulse, float alpha) {
		if (alpha <= 0.005f) return;
		float topY = cy + ry, botY = cy - ry;
		float px = cx + rx, nx = cx - rx, pz = cz + rz, nz = cz - rz;
		// Top 4 faces
		emitTriQuad(pose, vc, cx, topY, cz, px, cy, cz, cx, cy, pz, kind, seed, pulse, alpha);
		emitTriQuad(pose, vc, cx, topY, cz, cx, cy, pz, nx, cy, cz, kind, seed, pulse, alpha);
		emitTriQuad(pose, vc, cx, topY, cz, nx, cy, cz, cx, cy, nz, kind, seed, pulse, alpha);
		emitTriQuad(pose, vc, cx, topY, cz, cx, cy, nz, px, cy, cz, kind, seed, pulse, alpha);
		// Bottom 4 faces
		emitTriQuad(pose, vc, cx, botY, cz, cx, cy, pz, px, cy, cz, kind, seed, pulse, alpha);
		emitTriQuad(pose, vc, cx, botY, cz, nx, cy, cz, cx, cy, pz, kind, seed, pulse, alpha);
		emitTriQuad(pose, vc, cx, botY, cz, cx, cy, nz, nx, cy, cz, kind, seed, pulse, alpha);
		emitTriQuad(pose, vc, cx, botY, cz, px, cy, cz, cx, cy, nz, kind, seed, pulse, alpha);
	}

	private static void emitTriQuad(PoseStack.Pose pose, VertexConsumer vc,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
			float kind, float seed, float pulse, float alpha) {
		vertex(pose, vc, ax, ay, az, 0.5f, 0.5f, kind, seed, pulse, alpha);
		vertex(pose, vc, bx, by, bz, 0.0f, 0.25f, kind, seed, pulse, alpha);
		vertex(pose, vc, cx, cy, cz, 1.0f, 0.75f, kind, seed, pulse, alpha);
		vertex(pose, vc, cx, cy, cz, 1.0f, 0.75f, kind, seed, pulse, alpha);
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
