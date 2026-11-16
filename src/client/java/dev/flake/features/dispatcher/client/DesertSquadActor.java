package dev.flake.features.dispatcher.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.dispatcher.DispatcherRuntime;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.EffectActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.VfxTimeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders Dispatcher's 4 summoned soldiers in brown patterned wraps and wrapped head-scarves (keffiyeh)
 * leaving only their eyes visible, firing voxel AK-47s with summon rings, muzzle flashes, casings, and tracers.
 */
public final class DesertSquadActor implements EffectActor {
	private static final float MAX_LIFE_SEC = DispatcherRuntime.SQUAD_TICKS / 20.0f;
	private static final float FADE_OUT_SEC = 0.25f;
	private static final float SUMMON_ANIM_SEC = 0.42f;

	private static final class ClientSoldier {
		Vec3 pos;
		float yaw;
		float lastShotAt = -10.0f;
		boolean synced;
	}

	private record Tracer(Vec3 from, Vec3 to, float firedAt, float flightSec) {}
	private record Casing(Vec3 pos, Vec3 vel, float firedAt) {}

	private final int sourceId;
	private final ClientSoldier[] soldiers = new ClientSoldier[DispatcherRuntime.SQUAD_COUNT];
	private final List<Tracer> tracers = new ArrayList<>();
	private final List<Casing> casings = new ArrayList<>();
	private final VfxTimeline timeline = new VfxTimeline();
	private float ageSec;
	private float offAt = Float.NaN;

	public DesertSquadActor(CombatVfxPayload payload) {
		this.sourceId = payload.sourceId();
		Vec3 origin = payload.origin();
		float baseYaw = payload.yaw();
		double[] angles = { Math.toRadians(-68.0), Math.toRadians(-24.0), Math.toRadians(24.0), Math.toRadians(68.0) };
		double[] radii = { 2.55, 2.25, 2.25, 2.55 };
		for (int i = 0; i < DispatcherRuntime.SQUAD_COUNT; i++) {
			ClientSoldier s = new ClientSoldier();
			double a = baseYaw + angles[i];
			s.pos = origin.add(-Math.sin(a) * radii[i], 0.0, Math.cos(a) * radii[i]);
			s.yaw = baseYaw;
			soldiers[i] = s;
		}
	}

	@Override public int sourceId() { return sourceId; }

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() != sourceId) return false;
		if (payload.phase() == CombatVfxPayload.Phase.DISPATCHER_SQUAD_SYNC) {
			int idx = payload.targetId();
			if (idx >= 0 && idx < DispatcherRuntime.SQUAD_COUNT) {
				soldiers[idx].pos = payload.origin();
				soldiers[idx].yaw = payload.yaw();
				soldiers[idx].synced = true;
			}
			return true;
		}
		if (payload.phase() == CombatVfxPayload.Phase.DISPATCHER_SQUAD_SHOT) {
			ClientSoldier shooter = nearestSoldier(payload.origin());
			if (shooter != null) {
				shooter.pos = payload.origin();
				shooter.yaw = payload.yaw();
				shooter.lastShotAt = ageSec;
				Vec3 muzzle = akMuzzleWorldPos(shooter);
				Vec3 target = payload.aim();
				float flight = (float) Mth.clamp(muzzle.distanceTo(target) / 36.0, 0.07, 0.24);
				tracers.add(new Tracer(muzzle, target, ageSec, flight));
				if (tracers.size() > 36) tracers.removeFirst();

				Vector3f velLocal = new Vector3f(1.8f, 1.9f, -0.3f).rotateY(-shooter.yaw);
				casings.add(new Casing(muzzle.add(0.0, 0.04, 0.0), new Vec3(velLocal.x, velLocal.y, velLocal.z), ageSec));
				if (casings.size() > 24) casings.removeFirst();
			}
			return true;
		}
		if (payload.phase() == CombatVfxPayload.Phase.DISPATCHER_SQUAD_OFF) {
			if (Float.isNaN(offAt)) offAt = ageSec;
			return true;
		}
		return false;
	}

	private ClientSoldier nearestSoldier(Vec3 pos) {
		ClientSoldier best = soldiers[0];
		double bestDist = Double.POSITIVE_INFINITY;
		for (ClientSoldier s : soldiers) {
			double d = s.pos.distanceToSqr(pos);
			if (d < bestDist) {
				bestDist = d;
				best = s;
			}
		}
		return best;
	}

	private static Vec3 akMuzzleWorldPos(ClientSoldier s) {
		Vector3f muzzle = new Vector3f(-0.24f, 1.06f, 0.72f).rotateY(-s.yaw);
		return s.pos.add(muzzle.x, muzzle.y, muzzle.z);
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.DISPATCHER_SQUAD_ON;
	}

	@Override
	public boolean expired() {
		return ageSec >= MAX_LIFE_SEC + FADE_OUT_SEC || (!Float.isNaN(offAt) && ageSec >= offAt + FADE_OUT_SEC);
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

		float offFade = Float.isNaN(offAt) ? 1.0f : Mth.clamp(1.0f - (ageSec - offAt) / FADE_OUT_SEC, 0.0f, 1.0f);
		float tailFade = Mth.clamp((MAX_LIFE_SEC + 0.15f - ageSec) / FADE_OUT_SEC, 0.0f, 1.0f);
		float summonFrac = Mth.clamp(ageSec / SUMMON_ANIM_SEC, 0.0f, 1.0f);
		float globalAlpha = summonFrac * offFade * tailFade;
		if (globalAlpha <= 0.01f) return;

		// 1. Render the 4 wrapped soldiers & summon rings
		for (int i = 0; i < DispatcherRuntime.SQUAD_COUNT; i++) {
			ClientSoldier s = soldiers[i];
			float dx = (float) (s.pos.x - cameraPos.x);
			float dy = (float) (s.pos.y - cameraPos.y);
			float dz = (float) (s.pos.z - cameraPos.z);
			if (dx * dx + dy * dy + dz * dz > 72.0 * 72.0) continue;

			float shotElapsed = ageSec - s.lastShotAt;
			float recoil = (shotElapsed >= 0.0f && shotElapsed < 0.16f) ? (1.0f - shotElapsed / 0.16f) : 0.0f;
			float idleBob = Mth.sin(ageSec * 4.5f + i * 1.4f) * 0.018f;
			float riseScale = 0.25f + 0.75f * summonFrac;

			poseStack.pushPose();
			poseStack.translate(dx, dy + idleBob, dz);
			poseStack.mulPose(new Matrix4f().rotationY(-s.yaw));

			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
					(pose, vc) -> emitSoldierAndAk(pose, vc, riseScale, recoil, globalAlpha));

			if (recoil > 0.05f) {
				collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
						(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc,
								-0.24f, 1.06f * riseScale, 0.74f - recoil * 0.06f,
								0.38f * recoil, 1.0f, 0.76f, 0.28f, recoil * 0.90f));
			}
			poseStack.popPose();

			// Summon portal ring (first 0.55s)
			if (ageSec < 0.58f) {
				float sp = ageSec / 0.58f;
				collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.SOFT_RENDER_TYPE,
						(pose, vc) -> emitSummonRing(pose, vc, dx, dy + 0.04f, dz, sp, globalAlpha));
			}
		}

		// 2. Tracers & brass casings in world space
		if (!tracers.isEmpty() || !casings.isEmpty()) {
			collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE, (pose, vc) -> {
				for (Tracer tr : tracers) {
					float u = (ageSec - tr.firedAt) / tr.flightSec;
					if (u < 0.0f || u > 1.15f) continue;
					float clamped = Mth.clamp(u, 0.0f, 1.0f);
					Vec3 p = tr.from.lerp(tr.to, clamped);
					float px = (float) (p.x - cameraPos.x);
					float py = (float) (p.y - cameraPos.y);
					float pz = (float) (p.z - cameraPos.z);
					WeaponVoxelRenderer.emitCube(pose, vc, px, py, pz, 0.036f, 0.036f, 0.036f,
							1.0f, 0.84f, 0.35f, globalAlpha);
					Vec3 prev = tr.from.lerp(tr.to, Math.max(0.0f, clamped - 0.14f));
					Vec3 mid = p.lerp(prev, 0.5);
					WeaponVoxelRenderer.emitCube(pose, vc,
							(float) (mid.x - cameraPos.x), (float) (mid.y - cameraPos.y), (float) (mid.z - cameraPos.z),
							0.025f, 0.025f, 0.025f, 1.0f, 0.52f, 0.15f, globalAlpha * 0.75f);
				}
				for (Casing c : casings) {
					float t = ageSec - c.firedAt;
					if (t < 0.0f || t > 0.50f) continue;
					double cx = c.pos.x + c.vel.x * t;
					double cy = c.pos.y + c.vel.y * t - 4.9 * t * t;
					double cz = c.pos.z + c.vel.z * t;
					WeaponVoxelRenderer.emitCube(pose, vc,
							(float) (cx - cameraPos.x), (float) (cy - cameraPos.y), (float) (cz - cameraPos.z),
							0.016f, 0.012f, 0.024f, 0.92f, 0.72f, 0.22f, (1.0f - t / 0.50f) * globalAlpha);
				}
			});
		}
	}

	private static void emitSoldierAndAk(PoseStack.Pose pose, VertexConsumer vc,
			float s, float recoil, float a) {
		// Colors: brown cloth wraps, desert sand-gold ornaments, beige keffiyeh headwrap, visible eyes only
		float brR = 0.42f, brG = 0.25f, brB = 0.13f;
		float dkR = 0.25f, dkG = 0.14f, dkB = 0.07f;
		float wrR = 0.56f, wrG = 0.37f, wrB = 0.20f;
		float ornR = 0.85f, ornG = 0.68f, ornB = 0.32f;
		float twR = 0.78f, twG = 0.66f, twB = 0.48f;
		float twShR = 0.58f, twShG = 0.46f, twShB = 0.30f;

		// Boots & wrapped legs (0.0 -> 0.68)
		WeaponVoxelRenderer.emitCube(pose, vc, -0.10f, 0.34f * s, 0.0f, 0.085f, 0.34f * s, 0.085f, dkR, dkG, dkB, a);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.10f, 0.34f * s, 0.0f, 0.085f, 0.34f * s, 0.085f, dkR, dkG, dkB, a);
		// Leg spiral wrap bands + gold trim
		for (int b = 0; b < 3; b++) {
			float by = (0.14f + b * 0.18f) * s;
			WeaponVoxelRenderer.emitCube(pose, vc, -0.10f, by, 0.0f, 0.092f, 0.032f * s, 0.092f, wrR, wrG, wrB, a);
			WeaponVoxelRenderer.emitCube(pose, vc, 0.10f, by, 0.0f, 0.092f, 0.032f * s, 0.092f, wrR, wrG, wrB, a);
		}

		// Torso in brown tunic + patterned sash & gold desert ornaments (0.68 -> 1.30)
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.99f * s, 0.0f, 0.20f, 0.31f * s, 0.115f, brR, brG, brB, a);
		// Ornate waist sash
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.74f * s, 0.0f, 0.215f, 0.055f * s, 0.125f, dkR, dkG, dkB, a);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 0.74f * s, 0.128f, 0.12f, 0.025f * s, 0.010f, ornR, ornG, ornB, a);
		// Diagonal chest strap + diamond ornaments
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 1.02f * s, 0.120f, 0.045f, 0.21f * s, 0.012f, wrR, wrG, wrB, a);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 1.05f * s, 0.126f, 0.032f, 0.032f * s, 0.010f, ornR, ornG, ornB, a);
		WeaponVoxelRenderer.emitCube(pose, vc, -0.13f, 1.18f * s, 0.0f, 0.075f, 0.035f * s, 0.125f, ornR, ornG, ornB, a);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.13f, 1.18f * s, 0.0f, 0.075f, 0.035f * s, 0.125f, ornR, ornG, ornB, a);

		// Wrapped arms aiming forward toward weapon
		WeaponVoxelRenderer.emitCube(pose, vc, -0.25f, 1.08f * s, 0.12f, 0.068f, 0.068f * s, 0.17f, brR, brG, brB, a);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.22f, 1.05f * s, 0.16f, 0.068f, 0.068f * s, 0.18f, brR, brG, brB, a);
		// Arm ornament cuffs
		WeaponVoxelRenderer.emitCube(pose, vc, -0.25f, 1.08f * s, 0.22f, 0.074f, 0.074f * s, 0.025f, ornR, ornG, ornB, a);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.22f, 1.05f * s, 0.26f, 0.074f, 0.074f * s, 0.025f, ornR, ornG, ornB, a);

		// Head completely wrapped in a towel/keffiyeh with ONLY eyes visible (1.30 -> 1.72)
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 1.51f * s, 0.0f, 0.175f, 0.185f * s, 0.175f, twR, twG, twB, a);
		// Top turban folds & brown ornamental headband (agal)
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 1.66f * s, 0.0f, 0.195f, 0.055f * s, 0.195f, twShR, twShG, twShB, a);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 1.62f * s, 0.0f, 0.188f, 0.022f * s, 0.188f, dkR, dkG, dkB, a);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 1.62f * s, 0.190f, 0.08f, 0.018f * s, 0.010f, ornR, ornG, ornB, a);
		// Lower face towel wrap covering nose & mouth
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 1.40f * s, 0.01f, 0.182f, 0.082f * s, 0.182f, twShR, twShG, twShB, a);
		// Trailing cloth tail at back of head
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 1.38f * s, -0.185f, 0.11f, 0.16f * s, 0.03f, twShR, twShG, twShB, a);
		// Narrow dark eye slit + ONLY TWO EYES visible
		WeaponVoxelRenderer.emitCube(pose, vc, 0.0f, 1.52f * s, 0.176f, 0.135f, 0.038f * s, 0.010f, 0.07f, 0.04f, 0.03f, a);
		WeaponVoxelRenderer.emitCube(pose, vc, -0.058f, 1.52f * s, 0.184f, 0.026f, 0.018f * s, 0.008f, 0.96f, 0.92f, 0.80f, a);
		WeaponVoxelRenderer.emitCube(pose, vc, 0.058f, 1.52f * s, 0.184f, 0.026f, 0.018f * s, 0.008f, 0.96f, 0.92f, 0.80f, a);

		// Voxel AK-47 (Kalash) held ready at chest/shoulder height, with shot recoil kick
		float gz = 0.30f - recoil * 0.07f;
		float gx = -0.16f;
		float gy = 1.06f * s;
		// Wooden stock
		WeaponVoxelRenderer.emitCube(pose, vc, gx - 0.02f, gy - 0.02f, gz - 0.22f, 0.024f, 0.045f, 0.11f, 0.50f, 0.25f, 0.10f, a);
		// Steel receiver
		WeaponVoxelRenderer.emitCube(pose, vc, gx, gy, gz, 0.028f, 0.042f, 0.14f, 0.17f, 0.18f, 0.21f, a);
		// Wooden handguard
		WeaponVoxelRenderer.emitCube(pose, vc, gx, gy - 0.005f, gz + 0.19f, 0.024f, 0.034f, 0.08f, 0.52f, 0.27f, 0.11f, a);
		// Long steel barrel + gas tube
		WeaponVoxelRenderer.emitCube(pose, vc, gx, gy + 0.012f, gz + 0.34f, 0.014f, 0.016f, 0.11f, 0.24f, 0.25f, 0.28f, a);
		// Front sight post
		WeaponVoxelRenderer.emitCube(pose, vc, gx, gy + 0.042f, gz + 0.42f, 0.008f, 0.022f, 0.008f, 0.20f, 0.20f, 0.22f, a);
		// Iconic curved AK-47 banana magazine
		WeaponVoxelRenderer.emitCube(pose, vc, gx, gy - 0.09f, gz + 0.08f, 0.018f, 0.065f, 0.032f, 0.13f, 0.13f, 0.15f, a);
		WeaponVoxelRenderer.emitCube(pose, vc, gx, gy - 0.16f, gz + 0.11f, 0.018f, 0.045f, 0.030f, 0.13f, 0.13f, 0.15f, a);
	}

	private static void emitSummonRing(PoseStack.Pose pose, VertexConsumer vc,
			float dx, float dy, float dz, float p, float alpha) {
		float rad = 0.45f + p * 0.45f;
		float rAlpha = alpha * (1.0f - p);
		int nodes = 12;
		for (int i = 0; i < nodes; i++) {
			float ang = i * (Mth.TWO_PI / nodes) + p * 4.5f;
			boolean blue = (i & 1) == 0;
			WeaponVoxelRenderer.emitCube(pose, vc,
					dx + Mth.cos(ang) * rad, dy + p * 0.65f, dz + Mth.sin(ang) * rad,
					0.055f, 0.035f, 0.055f,
					blue ? 0.16f : 0.85f, blue ? 0.56f : 0.68f, blue ? 1.0f : 0.32f, rAlpha);
		}
	}
}
