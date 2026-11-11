package dev.flake.features.bastion.client;

import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedActor;
import dev.flake.features.bastion.BastionAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import java.util.Arrays;
import net.minecraft.world.phys.Vec3;

/** Caster motion is live; contact and confirmation are exclusively server-authored. */
public final class ShieldRamActor extends TimedActor {
	private final int targetId;
	private final Vec3 aim;
	private final float yaw;
	private final boolean shieldRam;
	private Vec3 lastBody, lagBody;
	private final Vec3[] history = new Vec3[5];
	private float poseAge, historyAge;

	public ShieldRamActor(CombatVfxPayload payload) {
		super(payload);
		targetId = payload.targetId(); aim = payload.aim(); yaw = payload.yaw();
		shieldRam = payload.phase() == CombatVfxPayload.Phase.SHIELD_RAM_SHIELD;
		lastBody = lagBody = origin;
		Arrays.fill(history, origin);
	}

	/** Fixed-time world-space breadcrumbs, interpolated across frames. */
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

	@Override public boolean write(RenderFrame frame, SlotWriter slot) {
		float age = age(frame);
		if (BastionAnimation.Ram.finished(age)) return true;
		var client = frame.client;
		var cameraPos = frame.camera;
		float partial = frame.partial;
		float[] values = slot.values;
		var source = client.level.getEntity(sourceId);
		var target = targetId >= 0 ? client.level.getEntity(targetId) : null;
		Vec3 body = source != null ? source.getPosition(partial) : lastBody;
		double dirX = Math.cos(yaw), dirZ = Math.sin(yaw);
		sampleMotion(body, age);
		float bodyHeight = source != null ? source.getBbHeight() : 1.8f;
		float targetHeight = target != null ? target.getBbHeight() : 1.8f;
		Vec3 impact = aim;
		double midX = body.x + (impact.x - body.x) * 0.55;
		double midY = (body.y + bodyHeight * 0.5) + (impact.y - (body.y + bodyHeight * 0.5)) * 0.55;
		double midZ = body.z + (impact.z - body.z) * 0.55;
		double dx = midX - cameraPos.x, dy = midY - cameraPos.y, dz = midZ - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 64 * 64) return false;
		float span = (float) body.distanceTo(impact);
		int position = slot.position;
		values[position] = (float) dx; values[position + 1] = (float) dy; values[position + 2] = (float) dz;
		values[position + 3] = span * 0.5f + 2.2f;
		int style = slot.style;
		values[style] = 1.0f;
		values[style + 1] = age;
		values[style + 2] = 9.0f;
		values[style + 3] = shieldRam ? 1.0f : 0.0f;
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
		values[material + 6] = targetId >= 0 ? BastionAnimation.Ram.impact(age) : 0;
		values[material + 7] = targetId >= 0 ? BastionAnimation.Ram.suppression(age) : 0;
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
}
