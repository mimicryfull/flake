package dev.flake.features.armorsets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.util.List;

/**
 * Lightweight pre-baked 3D armor geometry builder & renderer in HumanoidModel bone pixel space
 * (1 entity pixel = 1/16 block, +Y down, -Z front, -X player right).
 * Supports both 64x64 CubeUV armor boxes, per-face rectangular UV boxes, and rotated Blockbench cuboids.
 */
public final class ArmorMesh3D {
	private static final float PX = 1.0f / 16.0f;

	private ArmorMesh3D() {}

	public record Quad(
			float x0, float y0, float z0, float u0, float v0,
			float x1, float y1, float z1, float u1, float v1,
			float x2, float y2, float z2, float u2, float v2,
			float x3, float y3, float z3, float u3, float v3,
			float nx, float ny, float nz) {}

	public enum Face { NORTH, SOUTH, WEST, EAST, UP, DOWN }

	public record BbFace(Face dir, float u0, float v0, float u1, float v1, int uvRot) {}

	public static BbFace bbFace(Face dir, float u0, float v0, float u1, float v1) {
		return new BbFace(dir, u0, v0, u1, v1, 0);
	}

	public static BbFace bbFace(Face dir, float u0, float v0, float u1, float v1, int uvRot) {
		return new BbFace(dir, u0, v0, u1, v1, uvRot);
	}

	public static final int FULL_BRIGHT = 0x00F000F0;

	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, int packedLight, List<Quad> quads) {
		if (quads == null || quads.isEmpty()) return;
		collector.submitCustomGeometry(poseStack, RenderTypes.armorCutoutNoCull(texture), (pose, consumer) -> {
			for (Quad q : quads) {
				emitQuad(pose, consumer, q, packedLight);
			}
		});
	}

	private static void emitQuad(PoseStack.Pose pose, VertexConsumer vc, Quad q, int light) {
		addVtx(pose, vc, q.x0, q.y0, q.z0, q.u0, q.v0, q.nx, q.ny, q.nz, light);
		addVtx(pose, vc, q.x1, q.y1, q.z1, q.u1, q.v1, q.nx, q.ny, q.nz, light);
		addVtx(pose, vc, q.x2, q.y2, q.z2, q.u2, q.v2, q.nx, q.ny, q.nz, light);
		addVtx(pose, vc, q.x3, q.y3, q.z3, q.u3, q.v3, q.nx, q.ny, q.nz, light);
	}

	private static void addVtx(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z,
			float u, float v, float nx, float ny, float nz, int light) {
		vc.addVertex(pose, x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	public static final class Builder {
		private final java.util.ArrayList<Quad> quads = new java.util.ArrayList<>();
		private final float texW;
		private final float texH;

		public Builder(float texW, float texH) {
			this.texW = texW;
			this.texH = texH;
		}

		public List<Quad> build() {
			return List.copyOf(quads);
		}

		/** Standard Minecraft CubeUV cuboid centered in HumanoidModel bone pixel coords, unrotated. */
		public Builder cubeUv(int u, int v, float x, float y, float z, float w, float h, float d, float inflate, boolean mirror) {
			return cubeUvRot(u, v, x, y, z, w, h, d, inflate, mirror, 0f, 0f, 0f, x + w * 0.5f, y + h * 0.5f, z + d * 0.5f);
		}

		/** Standard Minecraft CubeUV cuboid with Euler XYZ rotation (degrees) around a bone-local pivot. */
		public Builder cubeUvRot(int u, int v, float x, float y, float z, float w, float h, float d, float inflate, boolean mirror,
				float rxDeg, float ryDeg, float rzDeg, float px, float py, float pz) {
			float x0 = x - inflate, y0 = y - inflate, z0 = z - inflate;
			float x1 = x + w + inflate, y1 = y + h + inflate, z1 = z + d + inflate;
			if (mirror) {
				float tmp = x0; x0 = x1; x1 = tmp;
			}
			// Top (y = y0, outward -Y)
			addEntityRectRot(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0,
					u + d, v, u + d + w, v + d, rxDeg, ryDeg, rzDeg, px, py, pz);
			// Bottom (y = y1, outward +Y)
			addEntityRectRot(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0,
					u + d + w, v + d, u + d + w + w, v, rxDeg, ryDeg, rzDeg, px, py, pz);
			// Right (-X when !mirror)
			addEntityRectRot(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, mirror ? 1 : -1, 0, 0,
					u, v + d, u + d, v + d + h, rxDeg, ryDeg, rzDeg, px, py, pz);
			// Front (z = z0, outward -Z)
			addEntityRectRot(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1,
					u + d, v + d, u + d + w, v + d + h, rxDeg, ryDeg, rzDeg, px, py, pz);
			// Left (+X when !mirror)
			addEntityRectRot(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, mirror ? -1 : 1, 0, 0,
					u + d + w, v + d, u + d + w + d, v + d + h, rxDeg, ryDeg, rzDeg, px, py, pz);
			// Back (z = z1, outward +Z)
			addEntityRectRot(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1,
					u + d + w + d, v + d, u + d + w + d + w, v + d + h, rxDeg, ryDeg, rzDeg, px, py, pz);
			return this;
		}

		/** Axis-aligned or rotated entity box using arbitrary rectangular UV patches per face (front/back/right/left/top/bot). */
		public Builder faceUvRot(float x, float y, float z, float w, float h, float d,
				float rxDeg, float ryDeg, float rzDeg, float px, float py, float pz,
				float[] frontUv, float[] backUv, float[] rightUv, float[] leftUv, float[] topUv, float[] botUv) {
			float x0 = x, y0 = y, z0 = z, x1 = x + w, y1 = y + h, z1 = z + d;
			if (topUv != null) addEntityRectRot(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0,
					topUv[0], topUv[1], topUv[2], topUv[3], rxDeg, ryDeg, rzDeg, px, py, pz);
			if (botUv != null) addEntityRectRot(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0,
					botUv[0], botUv[1], botUv[2], botUv[3], rxDeg, ryDeg, rzDeg, px, py, pz);
			if (rightUv != null) addEntityRectRot(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0,
					rightUv[0], rightUv[1], rightUv[2], rightUv[3], rxDeg, ryDeg, rzDeg, px, py, pz);
			if (frontUv != null) addEntityRectRot(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1,
					frontUv[0], frontUv[1], frontUv[2], frontUv[3], rxDeg, ryDeg, rzDeg, px, py, pz);
			if (leftUv != null) addEntityRectRot(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0,
					leftUv[0], leftUv[1], leftUv[2], leftUv[3], rxDeg, ryDeg, rzDeg, px, py, pz);
			if (backUv != null) addEntityRectRot(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1,
					backUv[0], backUv[1], backUv[2], backUv[3], rxDeg, ryDeg, rzDeg, px, py, pz);
			return this;
		}

		/**
		 * Converts a raw Blockbench JSON element ([0..16] normalized UVs) directly into bone-local entity space:
		 * entity = dstOffset + scale * (-(bb - srcRef.x), -(bb - srcRef.y), +(bb - srcRef.z))
		 * preserving inside-out boxes (from > to) and per-face UV rotation.
		 */
		public Builder blockbenchBox(
				float fx, float fy, float fz, float tx, float ty, float tz,
				float angleDeg, char axis, float ox, float oy, float oz,
				float scale, float refX, float refY, float refZ,
				float dstX, float dstY, float dstZ,
				float modelRotX, float modelRotY, float modelRotZ,
				BbFace... faces) {
			boolean inv = (fx > tx && fy > ty) || (fy > ty && fz > tz) || (fx > tx && fz > tz);
			float sign = inv ? -1.0f : 1.0f;
			for (BbFace f : faces) {
				Vector3f[] corners = switch (f.dir) {
					case NORTH -> new Vector3f[]{ vec(tx, ty, fz), vec(fx, ty, fz), vec(fx, fy, fz), vec(tx, fy, fz) };
					case SOUTH -> new Vector3f[]{ vec(fx, ty, tz), vec(tx, ty, tz), vec(tx, fy, tz), vec(fx, fy, tz) };
					case EAST  -> new Vector3f[]{ vec(tx, ty, tz), vec(tx, ty, fz), vec(tx, fy, fz), vec(tx, fy, tz) };
					case WEST  -> new Vector3f[]{ vec(fx, ty, fz), vec(fx, ty, tz), vec(fx, fy, tz), vec(fx, fy, fz) };
					case UP    -> new Vector3f[]{ vec(fx, ty, fz), vec(tx, ty, fz), vec(tx, ty, tz), vec(fx, ty, tz) };
					case DOWN  -> new Vector3f[]{ vec(fx, fy, tz), vec(tx, fy, tz), vec(tx, fy, fz), vec(fx, fy, fz) };
				};
				Vector3f norm = switch (f.dir) {
					case NORTH -> new Vector3f(0, 0, -sign);
					case SOUTH -> new Vector3f(0, 0, sign);
					case EAST  -> new Vector3f(sign, 0, 0);
					case WEST  -> new Vector3f(-sign, 0, 0);
					case UP    -> new Vector3f(0, sign, 0);
					case DOWN  -> new Vector3f(0, -sign, 0);
				};
				for (Vector3f p : corners) {
					rotBb(p, angleDeg, axis, ox, oy, oz);
					// Map BB space -> entity pixel space (-X, -Y, +Z) around (refX, refY, refZ) -> (0,0,0), then tilt, then shift to dst
					float ex = scale * -(p.x - refX);
					float ey = scale * -(p.y - refY);
					float ez = scale *  (p.z - refZ);
					p.set(ex, ey, ez);
					rotEntityEuler(p, modelRotX, modelRotY, modelRotZ);
					p.add(dstX, dstY, dstZ).mul(PX);
				}
				rotBbDir(norm, angleDeg, axis);
				norm.set(-norm.x, -norm.y, norm.z);
				rotEntityEuler(norm, modelRotX, modelRotY, modelRotZ);
				norm.normalize();

				// Blockbench UVs are always normalized to 16x16 regardless of texture resolution
				float[] uu = { f.u0 / 16f, f.u1 / 16f, f.u1 / 16f, f.u0 / 16f };
				float[] vv = { f.v0 / 16f, f.v0 / 16f, f.v1 / 16f, f.v1 / 16f };
				int shift = ((f.uvRot / 90) % 4 + 4) % 4;
				int i0 = shift, i1 = (1 + shift) & 3, i2 = (2 + shift) & 3, i3 = (3 + shift) & 3;
				quads.add(new Quad(
						corners[0].x, corners[0].y, corners[0].z, uu[i0], vv[i0],
						corners[1].x, corners[1].y, corners[1].z, uu[i1], vv[i1],
						corners[2].x, corners[2].y, corners[2].z, uu[i2], vv[i2],
						corners[3].x, corners[3].y, corners[3].z, uu[i3], vv[i3],
						norm.x, norm.y, norm.z));
			}
			return this;
		}

		private void addEntityRectRot(
				float x0, float y0, float z0, float x1, float y1, float z1,
				float x2, float y2, float z2, float x3, float y3, float z3,
				float nx, float ny, float nz,
				float uMin, float vMin, float uMax, float vMax,
				float rx, float ry, float rz, float px, float py, float pz) {
			Vector3f p0 = xformEntity(x0, y0, z0, rx, ry, rz, px, py, pz);
			Vector3f p1 = xformEntity(x1, y1, z1, rx, ry, rz, px, py, pz);
			Vector3f p2 = xformEntity(x2, y2, z2, rx, ry, rz, px, py, pz);
			Vector3f p3 = xformEntity(x3, y3, z3, rx, ry, rz, px, py, pz);
			Vector3f n = new Vector3f(nx, ny, nz);
			rotEntityEuler(n, rx, ry, rz);
			n.normalize();
			float su0 = uMin / texW, su1 = uMax / texW;
			float sv0 = vMin / texH, sv1 = vMax / texH;
			quads.add(new Quad(
					p0.x, p0.y, p0.z, su0, sv0,
					p1.x, p1.y, p1.z, su1, sv0,
					p2.x, p2.y, p2.z, su1, sv1,
					p3.x, p3.y, p3.z, su0, sv1,
					n.x, n.y, n.z));
		}

		private static Vector3f xformEntity(float x, float y, float z, float rx, float ry, float rz, float px, float py, float pz) {
			Vector3f v = new Vector3f(x - px, y - py, z - pz);
			rotEntityEuler(v, rx, ry, rz);
			return v.add(px, py, pz).mul(PX);
		}
	}

	private static Vector3f vec(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}

	private static void rotEntityEuler(Vector3f v, float rxDeg, float ryDeg, float rzDeg) {
		if (rzDeg != 0f) v.rotateZ((float) Math.toRadians(rzDeg));
		if (ryDeg != 0f) v.rotateY((float) Math.toRadians(ryDeg));
		if (rxDeg != 0f) v.rotateX((float) Math.toRadians(rxDeg));
	}

	private static void rotBb(Vector3f v, float deg, char axis, float ox, float oy, float oz) {
		if (deg == 0f) return;
		v.sub(ox, oy, oz);
		rotBbDir(v, deg, axis);
		v.add(ox, oy, oz);
	}

	private static void rotBbDir(Vector3f v, float deg, char axis) {
		if (deg == 0f) return;
		float rad = (float) Math.toRadians(deg);
		switch (axis) {
			case 'x' -> v.rotateX(rad);
			case 'y' -> v.rotateY(rad);
			case 'z' -> v.rotateZ(rad);
		}
	}
}
