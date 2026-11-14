package dev.flake.features.portal.client;

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
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.portal.client.PortalVortexTracker.ExposedFace;
import dev.flake.features.portal.client.PortalVortexTracker.PortalCluster;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Renders merged multi-block portal surfaces with the custom 3D parallax vortex shader
 * and emits delicate purple glow motes + occasional tiny violet micro-voxels drifting outward.
 */
public final class PortalVortexRenderer {
	public static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "pipeline/portal_vortex"))
					.withVertexShader(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "core/portal_vortex"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "core/portal_vortex"))
					.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
					.withPrimitiveTopology(PrimitiveTopology.QUADS)
					.withCull(false)
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
					.build());

	public static final RenderType RENDER_TYPE = RenderType.create(
			"flake_portal_vortex", RenderSetup.builder(PIPELINE).createRenderSetup());

	private static final double MAX_RENDER_DIST_SQ = 72.0 * 72.0;
	private static final double MAX_PARTICLE_DIST_SQ = 40.0 * 40.0;
	private static final int MAX_MOTES = 120;

	private static final class PortalMote {
		double x, y, z;
		float vx, vy, vz;
		float cx, cy, cz;
		float nx, ny, nz;
		float bornSec, lifeSec, size;
		boolean voxel;
		float colorMix;
	}

	private static final List<PortalMote> MOTES = new ArrayList<>(MAX_MOTES);
	private static final Random RNG = new Random(0x504F5254L);
	private static float lastFrameSec = -1.0f;
	private static float spawnAccum = 0.0f;
	private static ClientLevel moteLevel;

	private PortalVortexRenderer() {}

	public static void clearMotes() {
		MOTES.clear();
		lastFrameSec = -1.0f;
		spawnAccum = 0.0f;
		moteLevel = null;
	}

	public static void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		PortalVortexTracker.checkWorld(level);
		if (level == null) {
			clearMotes();
			return;
		}
		if (moteLevel != level) {
			clearMotes();
			moteLevel = level;
		}

		List<PortalCluster> clusters = PortalVortexTracker.clusters();
		if (clusters.isEmpty()) {
			MOTES.clear();
			return;
		}

		List<PortalCluster> visible = new ArrayList<>(clusters.size());
		for (PortalCluster c : clusters) {
			double dx = c.centerX - cameraPos.x;
			double dy = c.centerY - cameraPos.y;
			double dz = c.centerZ - cameraPos.z;
			double maxRadius = Math.max(c.halfX, Math.max(c.halfY, c.halfZ)) + 72.0;
			if (dx * dx + dy * dy + dz * dz <= maxRadius * maxRadius && dx * dx + dy * dy + dz * dz <= MAX_RENDER_DIST_SQ + (c.halfX + c.halfY + c.halfZ) * 48.0) {
				visible.add(c);
			}
		}
		if (visible.isEmpty()) return;

		float nowSec = System.nanoTime() / 1_000_000_000.0f;
		float dt = lastFrameSec < 0.0f || mc.isPaused() ? 0.0f : Mth.clamp(nowSec - lastFrameSec, 0.0f, 0.08f);
		lastFrameSec = nowSec;

		updateAndSpawnMotes(level, visible, cameraPos, nowSec, dt);

		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, vc) -> {
			for (PortalCluster cluster : visible) {
				emitClusterFaces(pose, vc, level, cluster, cameraPos);
			}
		});

		if (!MOTES.isEmpty()) {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
					(pose, vc) -> emitGlowMotes(pose, vc, cameraPos, nowSec));
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
					(pose, vc) -> emitVoxelMotes(pose, vc, cameraPos, nowSec));
		}
	}

	private static void emitClusterFaces(PoseStack.Pose pose, VertexConsumer vc, ClientLevel level,
			PortalCluster cluster, Vec3 cam) {
		for (ExposedFace face : cluster.faces) {
			if (level.getBlockState(face.neighbor()).isSolidRender()) continue;

			int bx = face.pos().getX();
			int by = face.pos().getY();
			int bz = face.pos().getZ();
			float x0 = bx, x1 = bx + 1.0f;
			float y0 = by, y1 = by + 1.0f;
			float z0 = bz, z1 = bz + 1.0f;

			Direction dir = face.dir();
			int dirIdx = switch (dir) {
				case DOWN -> 0;
				case UP -> 1;
				case NORTH -> 2;
				case SOUTH -> 3;
				case WEST -> 4;
				case EAST -> 5;
			};

			float halfU, halfV;
			switch (dir) {
				case DOWN, UP -> { halfU = cluster.halfX; halfV = cluster.halfZ; }
				case NORTH, SOUTH -> { halfU = cluster.halfX; halfV = cluster.halfY; }
				default -> { halfU = cluster.halfZ; halfV = cluster.halfY; }
			}

			float metaR = Mth.clamp(halfU / 16.0f, 0.0f, 1.0f);
			float metaG = Mth.clamp(halfV / 16.0f, 0.0f, 1.0f);
			float metaB = (dirIdx + 0.5f) / 6.0f;
			float metaA = cluster.seed;

			switch (dir) {
				case DOWN -> emitQuad(pose, vc, cluster, cam, dir, halfU, halfV, metaR, metaG, metaB, metaA,
						x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
				case UP -> emitQuad(pose, vc, cluster, cam, dir, halfU, halfV, metaR, metaG, metaB, metaA,
						x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
				case NORTH -> emitQuad(pose, vc, cluster, cam, dir, halfU, halfV, metaR, metaG, metaB, metaA,
						x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
				case SOUTH -> emitQuad(pose, vc, cluster, cam, dir, halfU, halfV, metaR, metaG, metaB, metaA,
						x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
				case WEST -> emitQuad(pose, vc, cluster, cam, dir, halfU, halfV, metaR, metaG, metaB, metaA,
						x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
				case EAST -> emitQuad(pose, vc, cluster, cam, dir, halfU, halfV, metaR, metaG, metaB, metaA,
						x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
			}
		}
	}

	private static void emitQuad(PoseStack.Pose pose, VertexConsumer vc, PortalCluster c, Vec3 cam,
			Direction dir, float halfU, float halfV, float r, float g, float b, float a,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3) {
		putVertex(pose, vc, c, cam, dir, halfU, halfV, x0, y0, z0, r, g, b, a);
		putVertex(pose, vc, c, cam, dir, halfU, halfV, x1, y1, z1, r, g, b, a);
		putVertex(pose, vc, c, cam, dir, halfU, halfV, x2, y2, z2, r, g, b, a);
		putVertex(pose, vc, c, cam, dir, halfU, halfV, x3, y3, z3, r, g, b, a);
	}

	private static void putVertex(PoseStack.Pose pose, VertexConsumer vc, PortalCluster c, Vec3 cam,
			Direction dir, float halfU, float halfV, float wx, float wy, float wz,
			float r, float g, float b, float a) {
		float u, v;
		switch (dir) {
			case DOWN, UP -> {
				u = (wx - c.centerX) / halfU;
				v = (wz - c.centerZ) / halfV;
			}
			case NORTH, SOUTH -> {
				u = (wx - c.centerX) / halfU;
				v = (wy - c.centerY) / halfV;
			}
			default -> {
				u = (wz - c.centerZ) / halfU;
				v = (wy - c.centerY) / halfV;
			}
		}
		vc.addVertex(pose, (float) (wx - cam.x), (float) (wy - cam.y), (float) (wz - cam.z))
				.setUv(u, v)
				.setColor(r, g, b, a);
	}

	private static void updateAndSpawnMotes(ClientLevel level, List<PortalCluster> clusters,
			Vec3 cam, float nowSec, float dt) {
		for (int i = MOTES.size() - 1; i >= 0; i--) {
			PortalMote m = MOTES.get(i);
			float age = nowSec - m.bornSec;
			if (age >= m.lifeSec) {
				MOTES.remove(i);
				continue;
			}
			if (dt > 0.0f) {
				// Gentle tangential vortex swirl around portal center as particle drifts outward
				float rx = (float) (m.x - m.cx);
				float ry = (float) (m.y - m.cy);
				float rz = (float) (m.z - m.cz);
				// swirl = cross(normal, offset)
				float sx = m.ny * rz - m.nz * ry;
				float sy = m.nz * rx - m.nx * rz;
				float sz = m.nx * ry - m.ny * rx;
				float sLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
				if (sLen > 0.05f) {
					float inv = 0.28f / sLen;
					sx *= inv; sy *= inv; sz *= inv;
				} else {
					sx = sy = sz = 0.0f;
				}
				m.x += (m.vx + sx) * dt;
				m.y += (m.vy + sy) * dt;
				m.z += (m.vz + sz) * dt;
			}
		}

		if (dt <= 0.0f || MOTES.size() >= MAX_MOTES) return;

		int totalFaces = 0;
		for (PortalCluster c : clusters) {
			double dx = c.centerX - cam.x;
			double dy = c.centerY - cam.y;
			double dz = c.centerZ - cam.z;
			if (dx * dx + dy * dy + dz * dz <= MAX_PARTICLE_DIST_SQ) {
				totalFaces += c.faces.size();
			}
		}
		if (totalFaces == 0) return;

		float ratePerSec = Math.min(34.0f, 5.5f + totalFaces * 0.55f);
		spawnAccum += dt * ratePerSec;
		while (spawnAccum >= 1.0f && MOTES.size() < MAX_MOTES) {
			spawnAccum -= 1.0f;
			PortalCluster c = clusters.get(RNG.nextInt(clusters.size()));
			double dx = c.centerX - cam.x;
			double dy = c.centerY - cam.y;
			double dz = c.centerZ - cam.z;
			if (dx * dx + dy * dy + dz * dz > MAX_PARTICLE_DIST_SQ) continue;

			ExposedFace face = c.faces.get(RNG.nextInt(c.faces.size()));
			if (level.getBlockState(face.neighbor()).isSolidRender()) continue;

			spawnMote(c, face, nowSec);
		}
	}

	private static void spawnMote(PortalCluster c, ExposedFace face, float nowSec) {
		Direction d = face.dir();
		float nx = d.getStepX();
		float ny = d.getStepY();
		float nz = d.getStepZ();

		float bx = face.pos().getX() + 0.5f + nx * 0.54f;
		float by = face.pos().getY() + 0.5f + ny * 0.54f;
		float bz = face.pos().getZ() + 0.5f + nz * 0.54f;

		float j1 = (RNG.nextFloat() - 0.5f) * 0.88f;
		float j2 = (RNG.nextFloat() - 0.5f) * 0.88f;
		if (Math.abs(nx) > 0.5f) {
			by += j1; bz += j2;
		} else if (Math.abs(ny) > 0.5f) {
			bx += j1; bz += j2;
		} else {
			bx += j1; by += j2;
		}

		PortalMote m = new PortalMote();
		m.x = bx; m.y = by; m.z = bz;
		m.cx = c.centerX; m.cy = c.centerY; m.cz = c.centerZ;
		m.nx = nx; m.ny = ny; m.nz = nz;

		float outSpeed = 0.24f + RNG.nextFloat() * 0.44f;
		m.vx = nx * outSpeed + (RNG.nextFloat() - 0.5f) * 0.08f;
		m.vy = ny * outSpeed + 0.04f + (RNG.nextFloat() - 0.5f) * 0.08f;
		m.vz = nz * outSpeed + (RNG.nextFloat() - 0.5f) * 0.08f;

		m.bornSec = nowSec;
		m.lifeSec = 1.05f + RNG.nextFloat() * 1.15f;
		m.voxel = RNG.nextFloat() < 0.18f; // rare tiny purple outer micro-voxels, mostly soft glow motes
		m.size = m.voxel ? (0.022f + RNG.nextFloat() * 0.014f) : (0.055f + RNG.nextFloat() * 0.055f);
		m.colorMix = RNG.nextFloat();
		MOTES.add(m);
	}

	private static void emitGlowMotes(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, float nowSec) {
		for (PortalMote m : MOTES) {
			if (m.voxel) continue;
			float t = Mth.clamp((nowSec - m.bornSec) / m.lifeSec, 0.0f, 1.0f);
			float alpha = Mth.sin(t * Mth.PI) * 0.72f;
			if (alpha <= 0.01f) continue;

			float r = Mth.lerp(m.colorMix, 0.58f, 0.88f);
			float g = Mth.lerp(m.colorMix, 0.22f, 0.42f);
			float b = 1.0f;
			float size = m.size * (0.75f + 0.45f * Mth.sin(t * Mth.PI));

			WeaponVoxelRenderer.emitGlowSprite(pose, vc,
					(float) (m.x - cam.x), (float) (m.y - cam.y), (float) (m.z - cam.z),
					size, r, g, b, alpha);
		}
	}

	private static void emitVoxelMotes(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, float nowSec) {
		for (PortalMote m : MOTES) {
			if (!m.voxel) continue;
			float t = Mth.clamp((nowSec - m.bornSec) / m.lifeSec, 0.0f, 1.0f);
			float env = Mth.sin(t * Mth.PI);
			float alpha = env * 0.88f;
			if (alpha <= 0.01f) continue;

			float h = m.size * (0.55f + 0.45f * env);
			float r = Mth.lerp(m.colorMix, 0.48f, 0.78f);
			float g = Mth.lerp(m.colorMix, 0.14f, 0.30f);
			float b = 0.98f;

			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (m.x - cam.x), (float) (m.y - cam.y), (float) (m.z - cam.z),
					h, h, h, r, g, b, alpha);
		}
	}
}
