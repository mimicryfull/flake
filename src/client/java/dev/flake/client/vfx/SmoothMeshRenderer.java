package dev.flake.client.vfx;

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
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Smooth non-voxel polygonal mesh renderer (spheres, cylinders, tapered rifle bodies)
 * free of cube bevel grid borders, supporting spherical SDF face painting via UV0.
 */
public final class SmoothMeshRenderer {
	public static final RenderPipeline CORE_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "pipeline/smooth_mesh_core"))
					.withVertexShader(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "core/smooth_mesh"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "core/smooth_mesh"))
					.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
					.withPrimitiveTopology(PrimitiveTopology.QUADS)
					.withCull(false)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
					.build());

	public static final RenderType CORE_RENDER_TYPE = RenderType.create(
			"flake_smooth_mesh_core", RenderSetup.builder(CORE_PIPELINE).createRenderSetup());

	private SmoothMeshRenderer() {}

	/**
	 * Emits a smooth UV-sphere whose vertices encode (theta, phi) spherical angles in UV0
	 * when faceMapped == true so the fragment shader paints anti-aliased features seamlessly.
	 */
	public static void emitSphere(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float radius, int stacks, int slices,
			float r, float g, float b, float a, boolean faceMapped) {
		if (a <= 0.01f || radius <= 0.002f) return;
		Matrix4f mat = pose.pose();
		float uvModeX = faceMapped ? 0.0f : 12.0f;

		for (int i = 0; i < stacks; i++) {
			float v0 = i / (float) stacks;
			float v1 = (i + 1) / (float) stacks;
			float phi0 = -Mth.HALF_PI + v0 * Mth.PI;
			float phi1 = -Mth.HALF_PI + v1 * Mth.PI;
			float y0 = Mth.sin(phi0) * radius;
			float y1 = Mth.sin(phi1) * radius;
			float c0 = Mth.cos(phi0) * radius;
			float c1 = Mth.cos(phi1) * radius;

			for (int j = 0; j < slices; j++) {
				float u0 = j / (float) slices;
				float u1 = (j + 1) / (float) slices;
				float th0 = -Mth.PI + u0 * Mth.TWO_PI;
				float th1 = -Mth.PI + u1 * Mth.TWO_PI;
				float s0 = Mth.sin(th0), cs0 = Mth.cos(th0);
				float s1 = Mth.sin(th1), cs1 = Mth.cos(th1);

				float uvx0 = faceMapped ? th0 : uvModeX;
				float uvx1 = faceMapped ? th1 : uvModeX;
				float uvy0 = faceMapped ? phi0 : 0.0f;
				float uvy1 = faceMapped ? phi1 : 0.0f;

				vertex(mat, vc, cx + c0 * s0, cy + y0, cz + c0 * cs0, uvx0, uvy0, r, g, b, a);
				vertex(mat, vc, cx + c0 * s1, cy + y0, cz + c0 * cs1, uvx1, uvy0, r, g, b, a);
				vertex(mat, vc, cx + c1 * s1, cy + y1, cz + c1 * cs1, uvx1, uvy1, r, g, b, a);
				vertex(mat, vc, cx + c1 * s0, cy + y1, cz + c1 * cs0, uvx0, uvy1, r, g, b, a);
			}
		}
	}

	/** Emits a closed smooth N-sided cylinder aligned along the Z axis from z0 to z1. */
	public static void emitCylinderZ(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float z0, float z1, float r0, float r1, int sides,
			float r, float g, float b, float a) {
		if (a <= 0.01f) return;
		Matrix4f mat = pose.pose();
		for (int i = 0; i < sides; i++) {
			float a0 = (i / (float) sides) * Mth.TWO_PI;
			float a1 = ((i + 1) / (float) sides) * Mth.TWO_PI;
			float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
			float c1 = Mth.cos(a1), s1 = Mth.sin(a1);

			quad(mat, vc,
					cx + c0 * r0, cy + s0 * r0, z0,
					cx + c1 * r0, cy + s1 * r0, z0,
					cx + c1 * r1, cy + s1 * r1, z1,
					cx + c0 * r1, cy + s0 * r1, z1,
					r, g, b, a);
			// End caps (quad fan pairs)
			quad(mat, vc,
					cx, cy, z0,
					cx + c1 * r0, cy + s1 * r0, z0,
					cx + c0 * r0, cy + s0 * r0, z0,
					cx, cy, z0,
					r, g, b, a);
			quad(mat, vc,
					cx, cy, z1,
					cx + c0 * r1, cy + s0 * r1, z1,
					cx + c1 * r1, cy + s1 * r1, z1,
					cx, cy, z1,
					r, g, b, a);
		}
	}

	/** Emits an arbitrary tapered hexagonal/rectangular lofted prism between two YZ profiles along Z. */
	public static void emitTaperedBoxZ(PoseStack.Pose pose, VertexConsumer vc,
			float cx0, float cy0, float z0, float hx0, float hy0,
			float cx1, float cy1, float z1, float hx1, float hy1,
			float r, float g, float b, float a) {
		if (a <= 0.01f) return;
		Matrix4f mat = pose.pose();
		float ax0 = cx0 - hx0, ax1 = cx0 + hx0, ay0 = cy0 - hy0, ay1 = cy0 + hy0;
		float bx0 = cx1 - hx1, bx1 = cx1 + hx1, by0 = cy1 - hy1, by1 = cy1 + hy1;

		quad(mat, vc, ax0, ay1, z0, bx0, by1, z1, bx1, by1, z1, ax1, ay1, z0, r, g, b, a);
		quad(mat, vc, ax0, ay0, z0, ax1, ay0, z0, bx1, by0, z1, bx0, by0, z1, r, g, b, a);
		quad(mat, vc, ax1, ay0, z0, ax1, ay1, z0, bx1, by1, z1, bx1, by0, z1, r, g, b, a);
		quad(mat, vc, ax0, ay1, z0, ax0, ay0, z0, bx0, by0, z1, bx0, by1, z1, r, g, b, a);
		quad(mat, vc, ax0, ay0, z0, ax0, ay1, z0, ax1, ay1, z0, ax1, ay0, z0, r, g, b, a);
		quad(mat, vc, bx0, by1, z1, bx0, by0, z1, bx1, by0, z1, bx1, by1, z1, r, g, b, a);
	}

	/** Emits a smooth lofted prism between two XZ cross-sections along Y (for curved magazines and grips). */
	public static void emitLoftedY(PoseStack.Pose pose, VertexConsumer vc,
			float cx0, float y0, float cz0, float hx0, float hz0,
			float cx1, float y1, float cz1, float hx1, float hz1,
			float r, float g, float b, float a) {
		if (a <= 0.01f) return;
		Matrix4f mat = pose.pose();
		float ax0 = cx0 - hx0, ax1 = cx0 + hx0, az0 = cz0 - hz0, az1 = cz0 + hz0;
		float bx0 = cx1 - hx1, bx1 = cx1 + hx1, bz0 = cz1 - hz1, bz1 = cz1 + hz1;

		quad(mat, vc, ax0, y0, az1, ax1, y0, az1, bx1, y1, bz1, bx0, y1, bz1, r, g, b, a);
		quad(mat, vc, ax1, y0, az0, ax0, y0, az0, bx0, y1, bz0, bx1, y1, bz0, r, g, b, a);
		quad(mat, vc, ax1, y0, az1, ax1, y0, az0, bx1, y1, bz0, bx1, y1, bz1, r, g, b, a);
		quad(mat, vc, ax0, y0, az0, ax0, y0, az1, bx0, y1, bz1, bx0, y1, bz0, r, g, b, a);
		quad(mat, vc, ax0, y0, az0, ax1, y0, az0, ax1, y0, az1, ax0, y0, az1, r, g, b, a);
		quad(mat, vc, bx0, y1, bz1, bx1, y1, bz1, bx1, y1, bz0, bx0, y1, bz0, r, g, b, a);
	}

	public static void quad(Matrix4f mat, VertexConsumer vc,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float r, float g, float b, float a) {
		vertex(mat, vc, x0, y0, z0, 12.0f, 0.0f, r, g, b, a);
		vertex(mat, vc, x1, y1, z1, 12.0f, 0.0f, r, g, b, a);
		vertex(mat, vc, x2, y2, z2, 12.0f, 0.0f, r, g, b, a);
		vertex(mat, vc, x3, y3, z3, 12.0f, 0.0f, r, g, b, a);
	}

	private static void vertex(Matrix4f mat, VertexConsumer vc,
			float x, float y, float z, float u, float v,
			float r, float g, float b, float a) {
		Vector3f p = mat.transformPosition(x, y, z, new Vector3f());
		vc.addVertex(p.x, p.y, p.z).setUv(u, v).setColor(r, g, b, a);
	}
}
