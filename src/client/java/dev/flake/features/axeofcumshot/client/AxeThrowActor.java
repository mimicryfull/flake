package dev.flake.features.axeofcumshot.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.Flake;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Renders the thrown Axe of Cumshot: spinning item texture flying along the throw path
 * accompanied by a restrained orange-brown glowing voxel trail.
 */
public final class AxeThrowActor implements EffectActor {
	private static final Identifier AXE_TEXTURE =
			Identifier.fromNamespaceAndPath(Flake.MOD_ID, "textures/item/axe_of_cumshot.png");

	private final int sourceId;
	private final Vec3 origin;
	private final Vec3 dir;
	private final double maxDist;
	private final float speedPerTick;
	private final float maxTicks;
	private long startNanos = -1L;
	private float ageTicks;
	private boolean clippedChecked;
	private double clippedDist;

	public AxeThrowActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.origin();
		Vec3 delta = payload.aim().subtract(origin);
		double len = delta.length();
		this.dir = len > 1e-4 ? delta.scale(1.0 / len) : new Vec3(0, 0, 1);
		this.maxDist = Math.max(0.5, len);
		this.speedPerTick = payload.yaw() > 0.05f ? payload.yaw() : 1.50f;
		this.maxTicks = (float) (this.maxDist / this.speedPerTick);
		this.clippedDist = this.maxDist;
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		if (startNanos < 0L) startNanos = frame.nanos;
		ageTicks = (frame.nanos - startNanos) * 20.0f / 1_000_000_000.0f;

		if (!clippedChecked) {
			clippedChecked = true;
			var mc = Minecraft.getInstance();
			if (mc.level != null && mc.player != null) {
				var hit = mc.level.clip(new ClipContext(
						origin, origin.add(dir.scale(maxDist)),
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
				if (hit.getType() != HitResult.Type.MISS) {
					clippedDist = Math.min(maxDist, origin.distanceTo(hit.getLocation()));
				}
			}
		}

		float activeTicks = (float) (clippedDist / speedPerTick) + 4.0f;
		return ageTicks >= Math.min(maxTicks + 4.0f, activeTicks);
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		double currentDist = Math.min(clippedDist, ageTicks * speedPerTick);
		float trailFade = ageTicks * speedPerTick > clippedDist
				? Math.clamp(1.0f - (ageTicks - (float) (clippedDist / speedPerTick)) / 4.0f, 0f, 1f)
				: 1.0f;
		if (trailFade <= 0.01f) return;

		Vec3 axePos = origin.add(dir.scale(currentDist));

		// 1. Spinning axe texture quad (hide once it hits/stops)
		if (ageTicks * speedPerTick <= clippedDist + 0.15) {
			poseStack.pushPose();
			poseStack.translate(axePos.x - cameraPos.x, axePos.y - cameraPos.y, axePos.z - cameraPos.z);

			float yaw = (float) Math.atan2(dir.x, dir.z);
			float horiz = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
			float pitch = (float) Math.atan2(-dir.y, horiz);
			float spin = ageTicks * 0.95f;

			Quaternionf rot = new Quaternionf()
					.rotateY(yaw)
					.rotateX(pitch)
					.rotateY((float) (Math.PI * 0.5))
					.rotateZ(-spin);
			poseStack.mulPose(new Matrix4f().rotate(rot));

			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(AXE_TEXTURE), (pose, vc) -> {
				float s = 0.44f;
				// Front face
				vc.addVertex(pose, -s, -s, 0f).setColor(255, 255, 255, 255).setUv(0f, 1f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
				vc.addVertex(pose, s, -s, 0f).setColor(255, 255, 255, 255).setUv(1f, 1f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
				vc.addVertex(pose, s, s, 0f).setColor(255, 255, 255, 255).setUv(1f, 0f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
				vc.addVertex(pose, -s, s, 0f).setColor(255, 255, 255, 255).setUv(0f, 0f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f);
				// Back face
				vc.addVertex(pose, -s, s, 0f).setColor(255, 255, 255, 255).setUv(0f, 0f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, -1f);
				vc.addVertex(pose, s, s, 0f).setColor(255, 255, 255, 255).setUv(1f, 0f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, -1f);
				vc.addVertex(pose, s, -s, 0f).setColor(255, 255, 255, 255).setUv(1f, 1f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, -1f);
				vc.addVertex(pose, -s, -s, 0f).setColor(255, 255, 255, 255).setUv(0f, 1f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0f, 0f, -1f);
			});
			poseStack.popPose();
		}

		// 2. Restrained orange-brown voxel trail
		Vec3 rawRight = dir.cross(new Vec3(0, 1, 0));
		final Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : rawRight.normalize();
		final Vec3 up = right.cross(dir).normalize();

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
			emitTrailVoxels(pose, vc, cameraPos, currentDist, trailFade, right, up);
		});

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) -> {
			float gx = (float) (axePos.x - cameraPos.x);
			float gy = (float) (axePos.y - cameraPos.y);
			float gz = (float) (axePos.z - cameraPos.z);
			WeaponVoxelRenderer.emitGlowSprite(pose, vc, gx, gy, gz, 0.55f, 1.0f, 0.52f, 0.14f, 0.42f * trailFade);
		});
	}

	private void emitTrailVoxels(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			double currentDist, float trailFade, Vec3 right, Vec3 up) {
		double trailLen = Math.min(currentDist, 2.8);
		int count = 11;
		for (int i = 0; i < count; i++) {
			float t = i / (float) (count - 1); // 0 = near axe, 1 = tail
			double d = currentDist - t * trailLen;
			if (d < 0.0) continue;

			float spiral = (float) (d * 2.6 + i * 0.9);
			float wobble = (0.06f + 0.16f * t);
			Vec3 p = origin.add(dir.scale(d))
					.add(right.scale(Math.cos(spiral) * wobble))
					.add(up.scale(Math.sin(spiral) * wobble));

			float size = (0.068f - 0.036f * t);
			float alpha = (1.0f - t) * 0.88f * trailFade;

			// Alternate warm orange, amber, and rich brown voxels
			float r, g, b;
			if (i % 3 == 0) {
				r = 1.0f; g = 0.52f; b = 0.10f; // bright orange
			} else if (i % 3 == 1) {
				r = 0.52f; g = 0.24f; b = 0.08f; // rich brown
			} else {
				r = 0.95f; g = 0.68f; b = 0.22f; // warm amber
			}

			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
					size, size, size, r, g, b, alpha);
		}
	}
}
