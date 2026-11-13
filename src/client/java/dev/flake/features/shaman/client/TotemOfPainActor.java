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

/** World-space turquoise carved Totem of Pain pedestal paired with shader orb & smooth explosion wave (style = 18). */
public final class TotemOfPainActor implements EffectActor {
	private static final float MAX_LIFE_SEC = ShamanRuntime.TOTEM_LIFE_TICKS / 20.0f;
	private static final float TOTEM_RADIUS = (float) ShamanRuntime.TOTEM_RADIUS;
	private static final float OFF_FADE_SEC = 0.25f;
	private static final int RING_MARKERS = 16;

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

	private float computeOpacity() {
		float spawnRaw = Math.clamp(ageSec / 0.16f, 0f, 1f);
		float spawnScale = 1.0f - (1.0f - spawnRaw) * (1.0f - spawnRaw);
		float offFade = Float.isNaN(offAt) ? 1.0f : Math.clamp(1.0f - (ageSec - offAt) / OFF_FADE_SEC, 0f, 1f);
		float tailFade = Math.clamp((MAX_LIFE_SEC - ageSec) / 0.35f, 0f, 1f);
		return spawnScale * offFade * tailFade;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		float opacity = computeOpacity();
		float[] values = slot.values;
		values[slot.position] = (float) (origin.x - frame.camera.x);
		values[slot.position + 1] = (float) (origin.y - frame.camera.y);
		values[slot.position + 2] = (float) (origin.z - frame.camera.z);
		values[slot.position + 3] = TOTEM_RADIUS;

		values[slot.style] = opacity;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 18.0f;
		values[slot.style + 3] = ageSec;
		return false;
	}

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
		float opacity = computeOpacity();
		if (opacity <= 0.01f) return;

		float tickPhase = ageSec % 1.0f;
		float pulse = Math.max(0.0f, 1.0f - tickPhase / 0.35f);
		float scale = (0.72f + 0.28f * spawnScale) * (0.35f + 0.65f * offFade);
		float orbY = (1.38f + (float) Math.sin(ageSec * 3.2f) * 0.06f) * scale;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);
		poseStack.mulPose(new org.joml.Matrix4f().rotationY(-yaw));

		// Carved turquoise obsidian & dark-wood pedestal post (orb on top is smooth shader sphere)
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitTotemPost(pose, vc, scale, pulse, opacity));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitTurquoiseWisps(pose, vc, orbY, pulse, opacity));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, orbY, 0f,
						0.42f + 0.14f * pulse, 0.14f, 0.96f, 0.88f, opacity * (0.32f + 0.24f * pulse)));

		poseStack.popPose();
	}

	private void emitTotemPost(PoseStack.Pose pose, VertexConsumer vc, float scale, float pulse, float opacity) {
		// Deep slate-teal plinth base
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.045f * scale, 0f, 0.24f * scale, 0.045f * scale, 0.24f * scale,
				0.04f, 0.12f, 0.15f, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.11f * scale, 0f, 0.17f * scale, 0.035f * scale, 0.17f * scale,
				0.06f, 0.18f, 0.21f, opacity);

		// Carved dark weathered totem column
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.54f * scale, 0f, 0.115f * scale, 0.41f * scale, 0.115f * scale,
				0.10f, 0.14f, 0.15f, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.36f * scale, 0f, 0.132f * scale, 0.035f * scale, 0.132f * scale,
				0.05f, 0.10f, 0.12f, opacity);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.92f * scale, 0f, 0.145f * scale, 0.045f * scale, 0.145f * scale,
				0.05f, 0.10f, 0.12f, opacity);

		// Carved crossbeam & side turquoise-slate tusks
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.78f * scale, 0f, 0.34f * scale, 0.055f * scale, 0.068f * scale,
				0.07f, 0.12f, 0.14f, opacity);
		for (int side = -1; side <= 1; side += 2) {
			float sx = side * 0.31f * scale;
			WeaponVoxelRenderer.emitCube(pose, vc, sx, 0.89f * scale, 0f, 0.042f * scale, 0.105f * scale, 0.052f * scale,
					0.06f, 0.28f, 0.30f, opacity);
			WeaponVoxelRenderer.emitCube(pose, vc, sx, 0.99f * scale, 0f, 0.028f * scale, 0.040f * scale, 0.028f * scale,
					0.16f, 0.96f, 0.88f, opacity);
		}

		// Glowing turquoise rune inlays on all 4 faces
		float runeR = 0.08f + 0.16f * pulse;
		float runeG = 0.86f + 0.14f * pulse;
		float runeB = 0.82f + 0.16f * pulse;
		for (int i = 0; i < 4; i++) {
			float ang = i * (float) (Math.PI * 0.5);
			float rx = (float) Math.cos(ang) * 0.122f * scale;
			float rz = (float) Math.sin(ang) * 0.122f * scale;
			WeaponVoxelRenderer.emitCube(pose, vc, rx, 0.54f * scale, rz, 0.028f * scale, 0.095f * scale, 0.028f * scale,
					runeR, runeG, runeB, opacity);
			WeaponVoxelRenderer.emitCube(pose, vc, rx, 0.72f * scale, rz, 0.024f * scale, 0.042f * scale, 0.024f * scale,
					runeR, runeG, runeB, opacity);
		}
	}

	private void emitTurquoiseWisps(PoseStack.Pose pose, VertexConsumer vc, float orbY, float pulse, float opacity) {
		// Subtle perimeter anchor markers synced with the shader ring
		float segStep = (float) (Math.PI * 2.0 / RING_MARKERS);
		float ringRot = ageSec * 0.28f;
		float ringAlpha = opacity * (0.35f + 0.25f * pulse);
		for (int i = 0; i < RING_MARKERS; i++) {
			float ang = i * segStep + ringRot;
			float x = (float) Math.cos(ang) * TOTEM_RADIUS;
			float z = (float) Math.sin(ang) * TOTEM_RADIUS;
			boolean major = (i % 4 == 0);
			float s = major ? 0.055f : 0.032f;
			float h = major ? 0.035f : 0.014f;
			WeaponVoxelRenderer.emitCube(pose, vc, x, 0.025f, z, s, h, s,
					major ? 0.36f : 0.08f, major ? 1.00f : 0.90f, major ? 0.94f : 0.84f, ringAlpha);
		}

		// Rising turquoise spirit sparks circling the orb
		for (int k = 0; k < 10; k++) {
			float phase = (ageSec * 0.95f + k / 10.0f) % 1.0f;
			float ang = k * 2.39996f + ageSec * 1.65f;
			float rad = 0.28f + phase * 0.24f;
			float wx = (float) Math.cos(ang) * rad;
			float wz = (float) Math.sin(ang) * rad;
			float wy = orbY - 0.22f + phase * 0.65f;
			float s = 0.024f * (1.0f - 0.55f * phase);
			float a = opacity * (float) Math.sin(phase * Math.PI) * 0.72f;
			WeaponVoxelRenderer.emitCube(pose, vc, wx, wy, wz, s, s, s, 0.22f, 0.98f, 0.90f, a);
		}
	}
}
