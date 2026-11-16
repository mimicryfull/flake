package dev.flake.features.dispatcher.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Huge 8-block blue / dark-blue ground slash seam, sweeping blade wave, and rising cobalt shards
 * for Dispatcher's Dispatch skill.
 */
public final class DispatchSlashActor implements EffectActor {
	private static final float DURATION_SEC = 1.25f;
	private static final int SEAM_STEPS = 32;

	private final int sourceId;
	private final Vec3 startPos;
	private final Vec3 endPos;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;

	public DispatchSlashActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.startPos = payload.origin();
		this.endPos = payload.aim();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.DISPATCH_SLASH;
	}

	@Override
	public boolean expired() {
		return ageSec >= DURATION_SEC;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		slot.values[slot.style] = 0.0f;
		return expired();
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired()) return;

		float p = Mth.clamp(ageSec / DURATION_SEC, 0.0f, 1.0f);
		float alpha = 1.0f - Mth.clamp((p - 0.55f) / 0.45f, 0.0f, 1.0f);
		if (alpha <= 0.01f) return;

		float sx = (float) (startPos.x - cameraPos.x);
		float sy = (float) (startPos.y - cameraPos.y) + 0.06f;
		float sz = (float) (startPos.z - cameraPos.z);
		float ex = (float) (endPos.x - cameraPos.x);
		float ey = (float) (endPos.y - cameraPos.y) + 0.06f;
		float ez = (float) (endPos.z - cameraPos.z);

		float dx = ex - sx;
		float dz = ez - sz;
		float len = Mth.sqrt(dx * dx + dz * dz);
		float fx = len > 1e-4f ? dx / len : 0.0f;
		float fz = len > 1e-4f ? dz / len : 1.0f;
		float rx = -fz;
		float rz = fx;

		float slashHead = Mth.clamp(ageSec / 0.28f, 0.0f, 1.0f);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitSlashCore(pose, vc, sx, sy, sz, ex, ey, ez, rx, rz, p, slashHead, alpha));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitSlashAura(pose, vc, sx, sy, sz, ex, ey, ez, rx, rz, p, slashHead, alpha));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE, (pose, vc) -> {
			if (slashHead < 0.99f) {
				float bx = Mth.lerp(slashHead, sx, ex);
				float by = Mth.lerp(slashHead, sy, ey) + 0.7f;
				float bz = Mth.lerp(slashHead, sz, ez);
				WeaponVoxelRenderer.emitGlowSprite(pose, vc, bx, by, bz, 1.35f, 0.20f, 0.58f, 1.0f, (1.0f - slashHead * 0.4f) * 0.85f);
			}
			for (int k = 0; k <= 4; k++) {
				float u = k / 4.0f;
				if (u > slashHead) break;
				WeaponVoxelRenderer.emitGlowSprite(pose, vc,
						Mth.lerp(u, sx, ex), Mth.lerp(u, sy, ey) + 0.14f, Mth.lerp(u, sz, ez),
						0.85f, 0.12f, 0.36f, 0.98f, alpha * 0.34f);
			}
		});
	}

	private static void emitSlashCore(PoseStack.Pose pose, VertexConsumer vc,
			float sx, float sy, float sz, float ex, float ey, float ez,
			float rx, float rz, float p, float head, float alpha) {
		for (int i = 0; i <= SEAM_STEPS; i++) {
			float u = i / (float) SEAM_STEPS;
			if (u > head) break;
			float cx = Mth.lerp(u, sx, ex);
			float cy = Mth.lerp(u, sy, ey);
			float cz = Mth.lerp(u, sz, ez);
			float env = Mth.sin(u * Mth.PI);
			float seamHalfW = (0.14f + 0.16f * env) * (1.0f - p * 0.45f);

			// Deep navy ground fracture
			WeaponVoxelRenderer.emitCube(pose, vc, cx, cy, cz,
					Math.abs(rx) * seamHalfW + 0.08f, 0.045f, Math.abs(rz) * seamHalfW + 0.08f,
					0.03f, 0.09f, 0.42f, alpha);
			// Electric blue inner blade core
			WeaponVoxelRenderer.emitCube(pose, vc, cx, cy + 0.04f, cz,
					0.055f + 0.05f * env, 0.035f, 0.055f + 0.05f * env,
					0.24f, 0.65f, 1.0f, alpha);

			// Angled dark-blue slash shards erupting along the 8-block cut
			if ((i & 1) == 0) {
				float side = ((i & 2) == 0 ? 1.0f : -1.0f) * (0.28f + 0.35f * env);
				float rise = (0.18f + (i % 5) * 0.11f) * (0.35f + p * 0.85f);
				WeaponVoxelRenderer.emitCube(pose, vc,
						cx + rx * side, cy + rise, cz + rz * side,
						0.045f, 0.14f * (1.0f - p * 0.5f), 0.045f,
						0.06f, 0.24f, 0.78f, alpha * (1.0f - p * 0.6f));
			}
		}

		// Huge crescent horizontal slash wave racing with the dash
		if (head > 0.05f && head < 1.0f) {
			float wx = Mth.lerp(head, sx, ex);
			float wy = Mth.lerp(head, sy, ey) + 0.78f;
			float wz = Mth.lerp(head, sz, ez);
			for (int k = -7; k <= 7; k++) {
				float lat = k * 0.22f;
				float back = (k * k) * 0.024f;
				float bx = wx + rx * lat - (ex - sx) * (back * 0.125f);
				float bz = wz + rz * lat - (ez - sz) * (back * 0.125f);
				float bright = 1.0f - Math.abs(k) / 8.5f;
				WeaponVoxelRenderer.emitCube(pose, vc, bx, wy, bz,
						0.08f, 0.035f, 0.08f,
						0.12f + 0.45f * bright, 0.42f + 0.45f * bright, 1.0f, alpha * 0.92f);
			}
		}
	}

	private static void emitSlashAura(PoseStack.Pose pose, VertexConsumer vc,
			float sx, float sy, float sz, float ex, float ey, float ez,
			float rx, float rz, float p, float head, float alpha) {
		for (int i = 0; i <= 16; i++) {
			float u = i / 16.0f;
			if (u > head) break;
			float cx = Mth.lerp(u, sx, ex);
			float cy = Mth.lerp(u, sy, ey) + 0.12f + p * 0.25f;
			float cz = Mth.lerp(u, sz, ez);
			float spread = (0.35f + 0.45f * Mth.sin(u * Mth.PI)) * (0.6f + p * 0.5f);
			WeaponVoxelRenderer.emitCube(pose, vc,
					cx + rx * spread * 0.5f, cy, cz + rz * spread * 0.5f,
					0.16f, 0.08f, 0.16f, 0.04f, 0.15f, 0.58f, alpha * 0.42f);
			WeaponVoxelRenderer.emitCube(pose, vc,
					cx - rx * spread * 0.5f, cy, cz - rz * spread * 0.5f,
					0.16f, 0.08f, 0.16f, 0.04f, 0.15f, 0.58f, alpha * 0.42f);
		}
	}
}
