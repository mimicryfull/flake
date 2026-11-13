package dev.flake.features.sparksyringe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedActor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Rising golden-cyan cleansing voxel helix around the caster. */
public final class SparkSyringeActor extends TimedActor {
	private static final float DURATION_SEC = 0.85f;
	private static final int HELIX_POINTS = 18;

	private float ageSec;

	public SparkSyringeActor(CombatVfxPayload payload) {
		super(payload);
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.SPARK_SYRINGE;
	}

	@Override
	public boolean expired() {
		return ageSec >= DURATION_SEC;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = age(frame);
		return expired();
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = time.sample(System.nanoTime(), mc.isPaused());
		if (expired() || mc.level == null) return;

		var entity = mc.level.getEntity(sourceId);
		Vec3 base = entity != null ? entity.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) : origin;
		double dx = base.x - cameraPos.x;
		double dy = base.y - cameraPos.y;
		double dz = base.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		float progress = Mth.clamp(ageSec / DURATION_SEC, 0.0f, 1.0f);
		float fade = 1.0f - smoothstep(0.55f, 1.0f, progress);
		if (fade <= 0.01f) return;

		float height = entity != null ? entity.getBbHeight() : 1.8f;
		float cx = (float) dx;
		float cy = (float) dy;
		float cz = (float) dz;

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitHelix(pose, vc, cx, cy, cz, height, progress, fade));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> emitGlows(pose, vc, cx, cy, cz, height, progress, fade));
	}

	private void emitHelix(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float height, float progress, float fade) {
		float riseHead = progress * (height + 0.55f);
		float radiusBase = 0.72f * (1.0f - 0.18f * progress);

		for (int strand = 0; strand < 2; strand++) {
			boolean gold = (strand == 0);
			float phaseOffset = strand * (float) Math.PI;
			for (int i = 0; i < HELIX_POINTS; i++) {
				float tailFrac = i / (float) (HELIX_POINTS - 1);
				float yOffset = riseHead - tailFrac * 1.15f;
				if (yOffset < 0.02f || yOffset > height + 0.45f) continue;

				float angle = ageSec * 9.5f + yOffset * 4.2f + phaseOffset;
				float r = radiusBase * (0.85f + 0.20f * (1.0f - tailFrac));
				float x = cx + (float) Math.cos(angle) * r;
				float z = cz + (float) Math.sin(angle) * r;
				float s = (0.056f - 0.030f * tailFrac) * (0.65f + 0.35f * fade);
				float alpha = (1.0f - tailFrac * 0.72f) * 0.92f * fade;

				if (gold) {
					WeaponVoxelRenderer.emitCube(pose, vc, x, cy + yOffset, z, s, s, s, 1.00f, 0.84f, 0.24f, alpha);
				} else {
					WeaponVoxelRenderer.emitCube(pose, vc, x, cy + yOffset, z, s, s, s, 0.32f, 0.96f, 1.00f, alpha);
				}
			}
		}

		// Ground cleanse ring expanding quickly at cast
		float ringT = Mth.clamp(progress / 0.45f, 0.0f, 1.0f);
		if (ringT < 1.0f) {
			float rr = 0.35f + ringT * 1.15f;
			float ra = (1.0f - ringT) * 0.75f;
			for (int i = 0; i < 16; i++) {
				float a = (i / 16.0f) * (float) (Math.PI * 2.0);
				WeaponVoxelRenderer.emitCube(pose, vc,
						cx + (float) Math.cos(a) * rr, cy + 0.05f, cz + (float) Math.sin(a) * rr,
						0.042f, 0.020f, 0.042f, 0.92f, 0.95f, 0.55f, ra);
			}
		}
	}

	private void emitGlows(PoseStack.Pose pose, VertexConsumer vc,
			float cx, float cy, float cz, float height, float progress, float fade) {
		float riseY = cy + Math.min(height + 0.25f, progress * (height + 0.45f));
		WeaponVoxelRenderer.emitGlowSprite(pose, vc, cx, riseY, cz, 0.65f * (0.6f + 0.4f * fade),
				0.85f, 0.96f, 0.68f, 0.55f * fade);
	}

	private static float smoothstep(float low, float high, float x) {
		float t = Mth.clamp((x - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
