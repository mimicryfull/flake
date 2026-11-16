package dev.flake.features.werewolf.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.werewolf.WerewolfRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * World-space 3D voxel VFX for Werewolf's Claws and Paws, Nature's Call (1.5x primal aura),
 * and Shadow Claws crimson bleed procs/ticks.
 */
public final class WerewolfActor implements EffectActor {
	private static final float FADE_OUT_SEC = 0.25f;

	private final int sourceId;
	private final CombatVfxPayload.Phase mode;
	private Vec3 pos;
	private final VfxTimeline timeline = new VfxTimeline();
	private final float maxLifeSec;
	private float ageSec;
	private float offAt = Float.NaN;

	public WerewolfActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.mode = payload.phase();
		this.pos = payload.origin();
		this.maxLifeSec = switch (mode) {
			case WEREWOLF_CLAWS_ON -> WerewolfRuntime.CLAWS_TICKS / 20.0f;
			case WEREWOLF_CALL_ON -> WerewolfRuntime.NATURES_CALL_TICKS / 20.0f;
			default -> 0.55f;
		};
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId) return false;
		if (mode == CombatVfxPayload.Phase.WEREWOLF_CLAWS_ON
				&& payload.phase() == CombatVfxPayload.Phase.WEREWOLF_CLAWS_OFF) {
			if (Float.isNaN(offAt)) offAt = ageSec;
			return true;
		}
		if (mode == CombatVfxPayload.Phase.WEREWOLF_CALL_ON
				&& payload.phase() == CombatVfxPayload.Phase.WEREWOLF_CALL_OFF) {
			if (Float.isNaN(offAt)) offAt = ageSec;
			return true;
		}
		return false;
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == mode;
	}

	@Override
	public boolean expired() {
		return ageSec >= maxLifeSec + FADE_OUT_SEC || (!Float.isNaN(offAt) && ageSec >= offAt + FADE_OUT_SEC);
	}

	private float opacity() {
		float in = Mth.clamp(ageSec / 0.12f, 0.0f, 1.0f);
		float offFade = Float.isNaN(offAt) ? 1.0f : Mth.clamp(1.0f - (ageSec - offAt) / FADE_OUT_SEC, 0.0f, 1.0f);
		float tailFade = Mth.clamp((maxLifeSec + 0.15f - ageSec) / FADE_OUT_SEC, 0.0f, 1.0f);
		return in * offFade * tailFade;
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

		ClientLevel level = mc.level;
		float entityHeight = 1.8f;
		if (level != null) {
			Entity target = level.getEntity(sourceId);
			if (target != null) {
				pos = target.position();
				entityHeight = target.getBbHeight();
			}
		}

		float alpha = opacity();
		if (alpha <= 0.01f) return;

		float dx = (float) (pos.x - cameraPos.x);
		float dy = (float) (pos.y - cameraPos.y);
		float dz = (float) (pos.z - cameraPos.z);

		if (mode == CombatVfxPayload.Phase.SHADOW_BLEED) {
			float cy = dy + entityHeight * 0.52f;
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
					(pose, vc) -> emitBleed(pose, vc, dx, cy, dz, ageSec, alpha));
			return;
		}

		final float h = entityHeight;
		if (mode == CombatVfxPayload.Phase.WEREWOLF_CLAWS_ON) {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
					(pose, vc) -> emitClawsAura(pose, vc, dx, dy, dz, h, ageSec, alpha));
		} else {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
					(pose, vc) -> emitNaturesCall(pose, vc, dx, dy, dz, h, ageSec, alpha));
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
					(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc, dx, dy + h * 0.45f, dz,
							0.95f, 0.35f, 0.92f, 0.48f, alpha * 0.25f));
		}
	}

	private static void emitClawsAura(PoseStack.Pose pose, VertexConsumer vc,
			float dx, float dy, float dz, float h, float age, float alpha) {
		int blades = 10;
		for (int i = 0; i < blades; i++) {
			float a = age * 3.8f + i * (Mth.TWO_PI / blades);
			float r = 0.62f + 0.10f * Mth.sin(age * 6.0f + i);
			float y = dy + 0.25f + ((i % 5) / 5.0f) * (h * 0.72f);
			boolean crimson = (i & 1) == 0;
			WeaponVoxelRenderer.emitCube(pose, vc,
					dx + Mth.cos(a) * r, y, dz + Mth.sin(a) * r,
					0.042f, 0.085f, 0.042f,
					crimson ? 0.92f : 0.82f, crimson ? 0.14f : 0.88f, crimson ? 0.18f : 0.95f, alpha * 0.72f);
		}
	}

	private static void emitNaturesCall(PoseStack.Pose pose, VertexConsumer vc,
			float dx, float dy, float dz, float h, float age, float alpha) {
		int nodes = 16;
		float ringR = 0.92f + 0.12f * Mth.sin(age * 4.5f);
		for (int i = 0; i < nodes; i++) {
			float a = i * (Mth.TWO_PI / nodes) + age * 1.4f;
			WeaponVoxelRenderer.emitCube(pose, vc,
					dx + Mth.cos(a) * ringR, dy + 0.06f, dz + Mth.sin(a) * ringR,
					0.075f, 0.025f, 0.075f, 0.22f, 0.88f, 0.48f, alpha * 0.68f);

			if ((i & 1) == 0) {
				float shieldA = a - age * 2.2f;
				float sy = dy + h * (0.28f + 0.36f * (0.5f + 0.5f * Mth.sin(age * 3.0f + i)));
				WeaponVoxelRenderer.emitCube(pose, vc,
						dx + Mth.cos(shieldA) * (ringR * 0.90f), sy, dz + Mth.sin(shieldA) * (ringR * 0.90f),
						0.06f, 0.08f, 0.06f, 0.30f, 0.94f, 0.62f, alpha * 0.55f);
			}
		}
	}

	private static void emitBleed(PoseStack.Pose pose, VertexConsumer vc,
			float dx, float cy, float dz, float age, float alpha) {
		float p = Mth.clamp(age / 0.55f, 0.0f, 1.0f);
		for (int i = 0; i < 10; i++) {
			float theta = i * 2.39996f;
			float speed = 0.55f + (i % 3) * 0.30f;
			float px = dx + Mth.cos(theta) * speed * p;
			float pz = dz + Mth.sin(theta) * speed * p;
			float py = cy + (0.35f - p * 1.05f) * p * speed;
			float r = (i & 1) == 0 ? 0.92f : 0.48f;
			WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, 0.032f, 0.042f, 0.032f,
					r, 0.03f, 0.07f, alpha * (1.0f - p));
		}
	}
}
