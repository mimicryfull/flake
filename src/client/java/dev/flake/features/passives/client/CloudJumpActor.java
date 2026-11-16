package dev.flake.features.passives.client;

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

/** Fluffy white & sky-blue voxel cloud burst beneath the player's feet on double jump. */
public final class CloudJumpActor implements EffectActor {
	private static final float DURATION_SEC = 0.46f;
	private static final int PUFFS = 14;

	private final int sourceId;
	private final Vec3 origin;
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;

	public CloudJumpActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.origin = payload.origin();
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.CLOUD_JUMP;
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
		float alpha = (1.0f - p * p) * 0.85f;
		if (alpha <= 0.01f) return;

		float dx = (float) (origin.x - cameraPos.x);
		float dy = (float) (origin.y - cameraPos.y) + 0.08f;
		float dz = (float) (origin.z - cameraPos.z);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
				(pose, vc) -> emitCloudRing(pose, vc, dx, dy, dz, p, alpha));
		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc, dx, dy + 0.06f, dz,
						0.85f + 0.45f * p, 0.86f, 0.96f, 1.0f, alpha * 0.35f));
	}

	private static void emitCloudRing(PoseStack.Pose pose, VertexConsumer vc,
			float dx, float dy, float dz, float p, float alpha) {
		float expand = 0.25f + p * 0.92f;
		for (int i = 0; i < PUFFS; i++) {
			float angle = i * (Mth.TWO_PI / PUFFS) + p * 1.1f;
			float rad = expand * ((i & 1) == 0 ? 1.0f : 0.70f);
			float py = dy - p * 0.14f + Mth.sin(i * 1.7f) * 0.07f;
			float size = (0.095f + (i % 3) * 0.025f) * (1.0f - p * 0.55f);
			boolean skyTint = (i % 3) == 0;
			WeaponVoxelRenderer.emitCube(pose, vc,
					dx + Mth.cos(angle) * rad, py, dz + Mth.sin(angle) * rad,
					size, size * 0.65f, size,
					skyTint ? 0.78f : 0.98f, skyTint ? 0.92f : 0.99f, 1.0f, alpha);
		}
	}
}
