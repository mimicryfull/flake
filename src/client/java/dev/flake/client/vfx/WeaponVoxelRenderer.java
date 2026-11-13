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

/**
 * Lightweight world-space voxel renderer for weapon art VFX.
 * Supports opaque/translucent voxel cubes, soft translucent wind voxels, and additive glow sprites/voxels.
 */
public final class WeaponVoxelRenderer {
	private static RenderPipeline.Builder baseBuilder(String name) {
		return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "pipeline/" + name))
				.withVertexShader(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "core/weapon_voxel"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "core/weapon_voxel"))
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withCull(false);
	}

	public static final RenderPipeline CORE_PIPELINE = RenderPipelines.register(
			baseBuilder("weapon_voxel_core")
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
					.build());

	public static final RenderPipeline SOFT_PIPELINE = RenderPipelines.register(
			baseBuilder("weapon_voxel_soft")
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.build());

	public static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
			baseBuilder("weapon_voxel_glow")
					.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.build());

	public static final RenderType CORE_RENDER_TYPE = RenderType.create(
			"flake_weapon_voxel_core", RenderSetup.builder(CORE_PIPELINE).createRenderSetup());
	public static final RenderType SOFT_RENDER_TYPE = RenderType.create(
			"flake_weapon_voxel_soft", RenderSetup.builder(SOFT_PIPELINE).createRenderSetup());
	public static final RenderType GLOW_RENDER_TYPE = RenderType.create(
			"flake_weapon_voxel_glow", RenderSetup.builder(GLOW_PIPELINE).createRenderSetup());

	private WeaponVoxelRenderer() {}

	public static void emitCube(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float hx, float hy, float hz,
			float r, float g, float b, float a) {
		if (a <= 0.01f || hx <= 0.002f || hy <= 0.002f || hz <= 0.002f) return;
		float x0 = cx - hx, x1 = cx + hx;
		float y0 = cy - hy, y1 = cy + hy;
		float z0 = cz - hz, z1 = cz + hz;

		quad(pose, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
		quad(pose, vc, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, r, g, b, a);
		quad(pose, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
		quad(pose, vc, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, b, a);
		quad(pose, vc, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r, g, b, a);
		quad(pose, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
	}

	/** Camera-facing soft additive glow sprite (UV in [2..3]). */
	public static void emitGlowSprite(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float radius,
			float r, float g, float b, float a) {
		if (a <= 0.005f || radius <= 0.01f) return;
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

		float rvx = rx * radius, rvz = rz * radius;
		float uvx = ux * radius, uvy = uy * radius, uvz = uz * radius;

		vertex(pose, vc, cx - rvx - uvx, cy - uvy, cz - rvz - uvz, 2f, 2f, r, g, b, a);
		vertex(pose, vc, cx + rvx - uvx, cy - uvy, cz + rvz - uvz, 3f, 2f, r, g, b, a);
		vertex(pose, vc, cx + rvx + uvx, cy + uvy, cz + rvz + uvz, 3f, 3f, r, g, b, a);
		vertex(pose, vc, cx - rvx + uvx, cy + uvy, cz - rvz + uvz, 2f, 3f, r, g, b, a);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer vc,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float r, float g, float b, float a) {
		vertex(pose, vc, x0, y0, z0, 0f, 0f, r, g, b, a);
		vertex(pose, vc, x1, y1, z1, 1f, 0f, r, g, b, a);
		vertex(pose, vc, x2, y2, z2, 1f, 1f, r, g, b, a);
		vertex(pose, vc, x3, y3, z3, 0f, 1f, r, g, b, a);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vc,
			float x, float y, float z, float u, float v,
			float r, float g, float b, float a) {
		vc.addVertex(pose, x, y, z)
				.setUv(u, v)
				.setColor(
						Math.clamp(r, 0f, 1f),
						Math.clamp(g, 0f, 1f),
						Math.clamp(b, 0f, 1f),
						Math.clamp(a, 0f, 1f));
	}
}
