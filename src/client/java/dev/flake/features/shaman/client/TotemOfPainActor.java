package dev.flake.features.shaman.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.shaman.ShamanRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** World-space 3D dark-wood & crimson-violet obsidian Totem of Pain with hovering crystal and r=3.5 danger ring. */
public final class TotemOfPainActor implements EffectActor {
	private static final float MAX_LIFE_SEC = ShamanRuntime.TOTEM_LIFE_TICKS / 20.0f;
	private static final float TOTEM_RADIUS = (float) ShamanRuntime.TOTEM_RADIUS;
	private static final float OFF_FADE_SEC = 0.25f;
	private static final int RING_SEGMENTS = 28;

	private final int sourceId;
	private final Vec3 origin;
	private final float yaw;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float offAt = Float.NaN;

	public TotemOfPainActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.origin();
		this.yaw = payload.yaw();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId || payload.phase() != CombatVfxPayload.Phase.SHAMAN_TOTEM_OFF) return false;
		if (Float.isNaN(offAt)) offAt = ageSec;
		return true;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.SHAMAN_TOTEM;
	}

	@Override
	public boolean expired() {
		return ageSec >= MAX_LIFE_SEC || (!Float.isNaN(offAt) && ageSec >= offAt + OFF_FADE_SEC);
	}

	@Override public boolean write(RenderFrame frame, SlotWriter slot) { return expired(); }

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		double dx = origin.x - cameraPos.x;
		double dy = origin.y - cameraPos.y;
		double dz = origin.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float spawnRaw = Math.clamp(ageSec / 0.16f, 0f, 1f);
		float spawnScale = 1.0f - (1.0f - spawnRaw) * (1.0f - spawnRaw);
		float offFade = Float.isNaN(offAt) ? 1.0f : Math.clamp(1.0f - (ageSec - offAt) / OFF_FADE_SEC, 0f, 1f);
		float tailFade = Math.clamp((MAX_LIFE_SEC - ageSec) / 0.35f, 0f, 1f);
		float opacity = spawnScale * offFade * tailFade;
		if (opacity <= 0.01f) return;

		// 1-second pulse matching server damage ticks
		float tickPhase = ageSec % 1.0f;
		float pulse = Math.max(0.0f, 1.0f - tickPhase / 0.35f);
		float scale = (0.72f + 0.28f * spawnScale) * (0.35f + 0.65f * offFade);
		float hoverY = (1.38f + (float) Math.sin(ageSec * 3.1f) * 0.055f) * scale;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);
		poseStack.mulPose(new Matrix4f().rotationY(-yaw));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitTotemPost(pose, vc, scale, pulse, opacity));

		// Hovering rotating crimson-violet obsidian crystal
		poseStack.pushPose();
		poseStack.translate(0f, hoverY, 0f);
		poseStack.mulPose(new Matrix4f().rotate(
				new Quaternionf().rotateY(ageSec * 1.9f).rotateX(0.24f).rotateZ(0.18f)));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitCrystal(pose, vc, scale, pulse, opacity));
		poseStack.popPose();

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitZoneAndWisps(pose, vc, hoverY, pulse, opacity));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitCrystalGlow(pose, vc, hoverY, pulse, opacity));

		poseStack.popPose();
	}

	private void emitTotemPost(PoseStack.Pose pose, VertexConsumer vc, float scale, float pulse, float opacity) {
		// Obsidian plinth base
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.045f * scale, 0f, 0.24f * scale, 0.045f * scale, 0.24f * scale,
				0.10f, 0.07f, 0.14f, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.11f * scale, 0f, 0.17f * scale, 0.035f * scale, 0.17f * scale,
				0.15f, 0.10f, 0.20f, opacity);

		// Carved dark-wood totem column
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.58f * scale, 0f, 0.115f * scale, 0.45f * scale, 0.115f * scale,
				0.19f, 0.12f, 0.08f, opacity);
		// Darker wood collar bands
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.38f * scale, 0f, 0.132f * scale, 0.035f * scale, 0.132f * scale,
				0.13f, 0.08f, 0.05f, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.98f * scale, 0f, 0.145f * scale, 0.045f * scale, 0.145f * scale,
				0.13f, 0.08f, 0.05f, opacity);

		// Carved tribal crossbeam & side obsidian tusks
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.80f * scale, 0f, 0.34f * scale, 0.055f * scale, 0.068f * scale,
				0.16f, 0.10f, 0.07f, opacity);
		for (int side = -1; side <= 1; side += 2) {
			float sx = side * 0.31f * scale;
			WeaponVoxelRenderer.emitCube(pose, vc, sx, 0.90f * scale, 0f, 0.042f * scale, 0.095f * scale, 0.052f * scale,
					0.12f, 0.07f, 0.16f, opacity);
		}

		// Glowing crimson-violet obsidian rune inlays on all 4 faces
		float runeR = 0.78f + 0.22f * pulse;
		float runeG = 0.12f + 0.16f * pulse;
		float runeB = 0.48f + 0.26f * pulse;
		for (int i = 0; i < 4; i++) {
			float ang = i * (float) (Math.PI * 0.5);
			float rx = (float) Math.cos(ang) * 0.122f * scale;
			float rz = (float) Math.sin(ang) * 0.122f * scale;
			WeaponVoxelRenderer.emitCube(pose, vc, rx, 0.56f * scale, rz, 0.028f * scale, 0.09f * scale, 0.028f * scale,
					runeR, runeG, runeB, opacity);
			WeaponVoxelRenderer.emitCube(pose, vc, rx, 0.74f * scale, rz, 0.024f * scale, 0.04f * scale, 0.024f * scale,
					runeR, runeG, runeB, opacity);
		}
	}

	private void emitCrystal(PoseStack.Pose pose, VertexConsumer vc, float scale, float pulse, float opacity) {
		float r = 0.72f + 0.28f * pulse;
		float g = 0.10f + 0.18f * pulse;
		float b = 0.56f + 0.32f * pulse;
		// Tall central crimson-violet obsidian crystal + bright inner heart
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0f, 0.088f * scale, 0.155f * scale, 0.088f * scale,
				r, g, b, opacity * 0.95f);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0f, 0.120f * scale, 0.068f * scale, 0.120f * scale,
				0.26f, 0.08f, 0.30f, opacity * 0.90f);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0f, 0.048f * scale, 0.195f * scale, 0.048f * scale,
				1.0f, 0.36f + 0.24f * pulse, 0.76f, opacity);
	}

	private void emitZoneAndWisps(PoseStack.Pose pose, VertexConsumer vc, float hoverY, float pulse, float opacity) {
		float segStep = (float) (Math.PI * 2.0 / RING_SEGMENTS);
		float ringRot = ageSec * 0.22f;
		float ringAlpha = opacity * (0.26f + 0.26f * pulse);

		for (int i = 0; i < RING_SEGMENTS; i++) {
			float ang = i * segStep + ringRot;
			float x = (float) Math.cos(ang) * TOTEM_RADIUS;
			float z = (float) Math.sin(ang) * TOTEM_RADIUS;
			boolean major = (i % 7 == 0);
			float s = major ? 0.065f : 0.040f;
			float h = major ? 0.028f : 0.014f;
			float r = major ? 0.95f : 0.78f;
			float g = major ? (0.16f + 0.18f * pulse) : 0.10f;
			float b = major ? 0.56f : 0.44f;
			WeaponVoxelRenderer.emitCube(pose, vc, x, 0.022f, z, s, h, s, r, g, b, ringAlpha);
		}

		// Subtle rising crimson-violet wisp embers around the hovering crystal
		for (int k = 0; k < 6; k++) {
			float phase = (ageSec * 0.85f + k / 6.0f) % 1.0f;
			float ang = k * 1.0472f + ageSec * 1.15f;
			float rad = 0.22f + phase * 0.18f;
			float wx = (float) Math.cos(ang) * rad;
			float wz = (float) Math.sin(ang) * rad;
			float wy = hoverY - 0.15f + phase * 0.48f;
			float s = 0.022f * (1.0f - 0.55f * phase);
			float a = opacity * (float) Math.sin(phase * Math.PI) * 0.65f;
			WeaponVoxelRenderer.emitCube(pose, vc, wx, wy, wz, s, s, s, 0.94f, 0.18f, 0.58f, a);
		}
	}

	private void emitCrystalGlow(PoseStack.Pose pose, VertexConsumer vc, float hoverY, float pulse, float opacity) {
		float glowRadius = 0.44f + 0.20f * pulse;
		float alpha = opacity * (0.30f + 0.34f * pulse);
		WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, hoverY, 0f, glowRadius,
				0.92f, 0.18f + 0.16f * pulse, 0.62f, alpha);
	}
}
