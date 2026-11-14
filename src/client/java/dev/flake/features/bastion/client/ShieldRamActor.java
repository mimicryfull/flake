package dev.flake.features.bastion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.bastion.BastionAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Hybrid Shield Ram VFX: low-overhead 3D golden voxel wedge shield, flying shards & suppression
 * clamps combined with fast post-shader streaks, nose lens & impact shockwave (`style = 9`).
 */
public final class ShieldRamActor implements EffectActor {
	private final int sourceId;
	private final int targetId;
	private final Vec3 origin;
	private final Vec3 aim;
	private final float yaw;
	private final boolean shieldRam;
	private final VfxTimeline timeline = new VfxTimeline();
	private Vec3 lastBody, lagBody;
	private final Vec3[] history = new Vec3[5];
	private float ageSec, poseAge, historyAge;

	public ShieldRamActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		this.targetId = payload.targetId();
		this.origin = payload.origin();
		this.aim = payload.aim();
		this.yaw = payload.yaw();
		this.shieldRam = payload.phase() == CombatVfxPayload.Phase.SHIELD_RAM_SHIELD;
		this.lastBody = this.lagBody = origin;
		Arrays.fill(history, origin);
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId
				&& (payload.phase() == CombatVfxPayload.Phase.SHIELD_RAM || payload.phase() == CombatVfxPayload.Phase.SHIELD_RAM_SHIELD);
	}

	@Override public boolean expired() { return BastionAnimation.Ram.finished(ageSec); }

	private void sampleMotion(Vec3 body, float age) {
		float dt = age - poseAge;
		if (dt > 0.25f || lastBody.distanceToSqr(body) > 36) {
			Arrays.fill(history, body);
			lagBody = body;
			historyAge = age;
		} else if (dt > 0) {
			lagBody = lagBody.lerp(body, -Math.expm1(-dt / 0.035));
			while (historyAge + 0.035f <= age) {
				historyAge += 0.035f;
				System.arraycopy(history, 0, history, 1, history.length - 1);
				history[0] = lastBody.lerp(body, Math.clamp((historyAge - poseAge) / dt, 0, 1));
			}
		}
		lastBody = body;
		poseAge = age;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = timeline.sample(frame.nanos, frame.client.isPaused());
		if (expired()) return true;

		var client = frame.client;
		var cameraPos = frame.camera;
		float partial = frame.partial;
		float[] values = slot.values;
		var source = client.level.getEntity(sourceId);
		var target = targetId >= 0 ? client.level.getEntity(targetId) : null;
		Vec3 body = source != null ? source.getPosition(partial) : lastBody;
		double dirX = Math.cos(yaw), dirZ = Math.sin(yaw);
		sampleMotion(body, ageSec);

		float bodyHeight = source != null ? source.getBbHeight() : 1.8f;
		float targetHeight = target != null ? target.getBbHeight() : 1.8f;
		Vec3 impact = aim;
		double midX = body.x + (impact.x - body.x) * 0.55;
		double midY = (body.y + bodyHeight * 0.5) + (impact.y - (body.y + bodyHeight * 0.5)) * 0.55;
		double midZ = body.z + (impact.z - body.z) * 0.55;
		double dx = midX - cameraPos.x, dy = midY - cameraPos.y, dz = midZ - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64 * 64) return false;

		float span = (float) body.distanceTo(impact);
		values[slot.position] = (float) dx;
		values[slot.position + 1] = (float) dy;
		values[slot.position + 2] = (float) dz;
		values[slot.position + 3] = span * 0.5f + 2.2f;

		values[slot.style] = 1.0f;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = 9.0f;
		values[slot.style + 3] = shieldRam ? 1.0f : 0.0f;

		int bounds = slot.bounds;
		values[bounds] = (float) (body.x - cameraPos.x);
		values[bounds + 1] = (float) (body.y - cameraPos.y);
		values[bounds + 2] = (float) (body.z - cameraPos.z);
		values[bounds + 3] = bodyHeight;
		values[bounds + 4] = (float) (impact.x - cameraPos.x);
		values[bounds + 5] = (float) (impact.y - cameraPos.y);
		values[bounds + 6] = (float) (impact.z - cameraPos.z);
		values[bounds + 7] = 1.0f + (shieldRam ? 0.42f : 0.0f);

		double vx = target != null ? target.getX(partial) : impact.x;
		double vy = target != null ? target.getY(partial) : impact.y - targetHeight * 0.52;
		double vz = target != null ? target.getZ(partial) : impact.z;
		values[bounds + 8] = (float) (vx - cameraPos.x);
		values[bounds + 9] = (float) (vy - cameraPos.y);
		values[bounds + 10] = (float) (vz - cameraPos.z);
		values[bounds + 11] = targetHeight;

		for (int i = 0; i < history.length; i++) {
			int entry = bounds + 12 + i * 4;
			values[entry] = (float) (history[i].x - cameraPos.x);
			values[entry + 1] = (float) (history[i].y - cameraPos.y);
			values[entry + 2] = (float) (history[i].z - cameraPos.z);
			values[entry + 3] = bodyHeight;
		}

		int material = slot.material;
		values[material] = (float) dirX; values[material + 1] = 0f; values[material + 2] = (float) dirZ;
		values[material + 3] = shieldRam ? 1.0f : 0.0f;
		values[material + 4] = targetHeight;
		values[material + 5] = targetId >= 0 ? 1 : 0;
		values[material + 6] = targetId >= 0 ? BastionAnimation.Ram.impact(ageSec) : 0;
		values[material + 7] = targetId >= 0 ? BastionAnimation.Ram.suppression(ageSec) : 0;

		double lagX = lagBody.x - body.x, lagY = lagBody.y - body.y, lagZ = lagBody.z - body.z;
		double lagLenSq = lagX * lagX + lagY * lagY + lagZ * lagZ;
		if (lagLenSq > 0.16) {
			double s = 0.4 / Math.sqrt(lagLenSq);
			lagX *= s; lagY *= s; lagZ *= s;
			lagLenSq = 0.16;
		}
		values[material + 8] = (float) lagX;
		values[material + 9] = (float) lagY;
		values[material + 10] = (float) lagZ;
		values[material + 11] = (float) Math.min(1, Math.sqrt(lagLenSq) * 3);
		return false;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		ageSec = timeline.sample(System.nanoTime(), mc.isPaused());
		if (expired() || mc.level == null) return;

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		var source = mc.level.getEntity(sourceId);
		Vec3 body = source != null ? source.getPosition(partial) : lastBody;
		float bodyHeight = source != null ? source.getBbHeight() : 1.8f;

		double dx = body.x - cameraPos.x;
		double dy = body.y - cameraPos.y;
		double dz = body.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64.0 * 64.0) return;

		boolean confirmed = targetId >= 0;
		float shieldScale = shieldRam ? 1.24f : 1.0f;
		float ramAlpha = smoothstep(0.0f, 0.04f, ageSec) * (1.0f - smoothstep(0.28f, 0.48f, ageSec));
		float spring = BastionAnimation.assembly(ageSec / 0.11f);
		Vec3 dir = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw)).normalize();
		Vec3 side = new Vec3(-dir.z, 0.0, dir.x);

		// 1. 3D Golden Voxel Wedge Shield in front of the charging Bastion (hidden slightly on 1st person owner so view stays clear).
		boolean hideCoreProwForFirstPerson = mc.player != null && mc.player.getId() == sourceId
				&& mc.options.getCameraType().isFirstPerson();
		if (ramAlpha > 0.02f) {
			Vec3 nose = body.add(0.0, bodyHeight * 0.52, 0.0).add(dir.scale(0.88 + 0.15 * shieldScale));
			if (confirmed) {
				float lockT = smoothstep(0.055f, 0.10f, ageSec);
				nose = nose.lerp(aim, lockT);
			}
			Vec3 finalNose = nose;
			poseStack.pushPose();
			poseStack.translate((float) (finalNose.x - cameraPos.x), (float) (finalNose.y - cameraPos.y), (float) (finalNose.z - cameraPos.z));
			poseStack.mulPose(new Matrix4f().rotationY((float) (Math.PI * 0.5 - yaw)));
			if (!hideCoreProwForFirstPerson) {
				collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
						(pose, vc) -> emitVoxelRamShield(pose, vc, shieldScale, spring, confirmed, ramAlpha));
			}
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
					(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, 0f, 0.18f,
							0.78f * shieldScale, 1.00f, 0.82f, 0.24f, 0.46f * ramAlpha));
			poseStack.popPose();
		}

		// 2. Trail crumb embers behind dash path
		float trailAlpha = smoothstep(0.02f, 0.08f, ageSec) * (1.0f - smoothstep(0.32f, 0.56f, ageSec));
		if (trailAlpha > 0.02f) {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
					(pose, vc) -> emitDashCrumbs(pose, vc, cameraPos, side, trailAlpha));
		}

		// 3. Confirmed impact golden voxel shards & suppression clamps on victim
		if (confirmed && ageSec >= BastionAnimation.Ram.IMPACT_START) {
			float t = ageSec - BastionAnimation.Ram.IMPACT_START;
			if (t < 0.45f) {
				float shardAlpha = 1.0f - smoothstep(0.18f, 0.45f, t);
				collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
						(pose, vc) -> emitImpactShards(pose, vc, cameraPos, dir, side, t, shardAlpha));
				collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
						(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc,
								(float) (aim.x - cameraPos.x), (float) (aim.y - cameraPos.y), (float) (aim.z - cameraPos.z),
								1.25f * shieldScale, 1.00f, 0.90f, 0.35f, (1.0f - smoothstep(0.05f, 0.32f, t)) * 0.72f));
			}

			float sup = BastionAnimation.Ram.suppression(ageSec);
			if (sup > 0.02f) {
				var target = mc.level.getEntity(targetId);
				Vec3 vPos = target != null ? target.getPosition(partial) : aim.add(0.0, -0.9, 0.0);
				float vh = target != null ? target.getBbHeight() : 1.8f;
				collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
						(pose, vc) -> emitSuppressionClamps(pose, vc, cameraPos, vPos, vh, sup));
			}
		}
	}

	private void emitVoxelRamShield(PoseStack.Pose pose, VertexConsumer vc,
			float scale, float spring, boolean confirmed, float alpha) {
		float spread = confirmed ? Math.max(0.0f, (ageSec - 0.10f) * 3.8f) : 0.0f;
		float open = (1.0f - spring) * 0.45f;
		float flash = confirmed ? BastionAnimation.Ram.impact(ageSec) : 0.0f;

		// Central golden prow spine
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0f, 0.14f * scale,
				0.075f * scale, 0.44f * scale, 0.12f * scale,
				1.00f, 0.88f + 0.12f * flash, 0.28f + 0.55f * flash, alpha);
		WeaponVoxelRenderer.emitCube(pose, vc, 0f, 0.46f * scale, 0.08f * scale,
				0.055f * scale, 0.11f * scale, 0.08f * scale, 1.00f, 0.96f, 0.65f, alpha);

		// Left & right angled golden shield wings
		for (int side = -1; side <= 1; side += 2) {
			float sx = side * (0.26f + open + spread * 0.55f) * scale;
			float sz = -(0.06f + spread * 0.20f) * scale;
			// Main amber-gold plate
			WeaponVoxelRenderer.emitCube(pose, vc, sx, 0.02f, sz,
					0.16f * scale, 0.36f * scale, 0.055f * scale,
					0.98f, 0.68f + 0.20f * flash, 0.10f + 0.35f * flash, alpha * 0.95f);
			// Dark bronze outer frame rib
			WeaponVoxelRenderer.emitCube(pose, vc, side * (0.44f + open + spread * 0.62f) * scale, 0f, sz - 0.05f,
					0.045f * scale, 0.30f * scale, 0.065f * scale,
					0.46f, 0.26f, 0.04f, alpha);
			// Bright yellow inner chevron trim
			WeaponVoxelRenderer.emitCube(pose, vc, sx * 0.72f, 0.20f * scale, sz + 0.05f,
					0.10f * scale, 0.05f * scale, 0.035f * scale,
					1.00f, 0.92f, 0.38f, alpha);
		}
	}

	private void emitDashCrumbs(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, Vec3 side, float alpha) {
		for (int i = 0; i < history.length; i++) {
			float tail = 1.0f - i / (float) history.length;
			Vec3 h = history[i];
			for (int s = -1; s <= 1; s += 2) {
				Vec3 p = h.add(side.scale(s * 0.52)).add(0.0, 0.45 + (i % 2) * 0.35, 0.0);
				float sz = 0.032f * tail;
				WeaponVoxelRenderer.emitCube(pose, vc,
						(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
						sz, sz, sz, 1.00f, 0.74f + 0.18f * tail, 0.14f, alpha * tail * 0.78f);
			}
		}
	}

	private void emitImpactShards(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 dir, Vec3 side, float t, float alpha) {
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		for (int k = 0; k < 10; k++) {
			float seed = (k * 0.618034f) % 1.0f;
			float ang = k * 2.39996f;
			Vec3 vel = side.scale(Math.cos(ang) * (2.0 + seed * 2.1))
					.add(up.scale(Math.sin(ang) * (1.8 + seed * 1.8)))
					.add(dir.scale(0.8 + seed * 1.4));
			float drag = (float) (1.0 - Math.exp(-t * 4.5)) / 4.5f;
			Vec3 p = aim.add(vel.scale(drag)).add(0.0, -t * t * 2.6, 0.0);
			float sz = (0.042f + 0.024f * seed) * (1.0f - 0.35f * t);
			boolean bright = (k % 2 == 0);
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z),
					sz, sz * 1.4f, sz * 0.65f,
					1.00f, bright ? 0.88f : 0.64f, bright ? 0.34f : 0.08f, alpha * 0.92f);
		}
	}

	private void emitSuppressionClamps(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam,
			Vec3 victimPos, float vh, float sup) {
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI * 0.5 + 0.45 + ageSec * 0.8;
			float r = 0.56f;
			double px = victimPos.x + Math.cos(a) * r;
			double pz = victimPos.z + Math.sin(a) * r;
			double py = victimPos.y + vh * (0.42 + (k % 2) * 0.22);
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (px - cam.x), (float) (py - cam.y), (float) (pz - cam.z),
					0.065f, vh * 0.16f, 0.065f, 0.98f, 0.72f, 0.12f, sup * 0.86f);
			WeaponVoxelRenderer.emitCube(pose, vc,
					(float) (px - cam.x), (float) (py - cam.y), (float) (pz - cam.z),
					0.045f, vh * 0.18f, 0.045f, 1.00f, 0.92f, 0.42f, sup * 0.92f);
		}
	}

	private static float smoothstep(float low, float high, float x) {
		float t = Math.clamp((x - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
