package dev.flake.features.armorsets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.armorsets.BarrelArmor;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

/** Dark-brown expanding shock wave + oak voxels triggered by Barrel Armor every -30% HP lost (style = 28). */
public final class BarrelPulseActor implements EffectActor {
	private static final float DURATION_SEC = 0.70f;
	private static final float MAX_RADIUS = (float) BarrelArmor.PULSE_RADIUS;
	private static final int SHARD_COUNT = 22;

	private final int sourceId;
	private final Vec3 origin;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;

	public BarrelPulseActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.origin();
	}

	@Override public int sourceId() { return sourceId; }
	@Override public boolean expired() { return ageSec >= DURATION_SEC; }

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		float t = ageSec / DURATION_SEC;
		float opacity = 1.0f - t * t;

		float[] values = slot.values;
		values[slot.position] = (float) (origin.x - frame.camera.x);
		values[slot.position + 1] = (float) (origin.y - frame.camera.y);
		values[slot.position + 2] = (float) (origin.z - frame.camera.z);
		values[slot.position + 3] = MAX_RADIUS;

		values[slot.style] = opacity;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 28.0f;
		values[slot.style + 3] = 1.0f;
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
		if (dx * dx + dy * dy + dz * dz > 56.0 * 56.0) return;

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				this::emitOakShards);
		poseStack.popPose();
	}

	private void emitOakShards(PoseStack.Pose pose, VertexConsumer vc) {
		float t = Math.clamp(ageSec / DURATION_SEC, 0f, 1f);
		float spread = (float) (1.0 - Math.pow(1.0 - t, 2.2));
		float alpha = (1.0f - t) * 0.88f;
		for (int i = 0; i < SHARD_COUNT; i++) {
			float ang = (float) (i * (Math.PI * 2.0 / SHARD_COUNT)) + t * 0.65f;
			float ring = (0.45f + (i % 3) * 0.18f) * MAX_RADIUS * spread;
			float x = (float) Math.cos(ang) * ring;
			float z = (float) Math.sin(ang) * ring;
			float y = 0.14f + (float) Math.sin(t * Math.PI) * (0.45f + (i % 4) * 0.16f);
			float size = (0.045f + (i % 3) * 0.014f) * (1.0f - t * 0.45f);
			boolean dark = (i % 2 == 0);
			WeaponVoxelRenderer.emitCube(pose, vc, x, y, z, size, size * 0.85f, size,
					dark ? 0.24f : 0.48f,
					dark ? 0.12f : 0.25f,
					dark ? 0.05f : 0.09f,
					alpha);
		}
	}
}
