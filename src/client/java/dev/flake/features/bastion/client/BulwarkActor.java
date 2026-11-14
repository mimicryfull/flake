package dev.flake.features.bastion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flake.client.vfx.WeaponVoxelRenderer;
import dev.flake.features.bastion.BastionAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedBuffActor;
import dev.flake.render.vfx.VfxTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hybrid Bulwark VFX: 6 crisp golden 3D voxel shield plates + ground anchor keys around the Bastion,
 * paired with lightweight post-shader golden barrier dome, rib streaks & hit shockwave (`style = 10/11`).
 */
public final class BulwarkActor extends TimedBuffActor {
	private final VfxTimeline hit = new VfxTimeline();
	private boolean hitFired;
	private Vec3 hitDirection = Vec3.ZERO, lastBody;
	private float lastAge;
	private float lastHitAge = -1f;

	public BulwarkActor(CombatVfxPayload payload) {
		super(payload, CombatVfxPayload.Phase.BULWARK_ON, CombatVfxPayload.Phase.BULWARK_OFF, BastionAnimation.Bulwark.BUFF_SECONDS);
		this.lastBody = origin;
	}

	@Override
	public boolean receive(CombatVfxPayload payload) {
		if (payload.sourceId() == sourceId && payload.phase() == CombatVfxPayload.Phase.BULWARK_HIT) {
			hit.reset();
			hitFired = true;
			lastHitAge = 0.0f;
			hitDirection = payload.aim();
			return true;
		}
		return super.receive(payload);
	}

	@Override
	public boolean expired() {
		return BastionAnimation.Bulwark.finished(lastAge, off());
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		float age = age(frame), off = off();
		lastAge = age;
		if (BastionAnimation.Bulwark.finished(age, off)) return true;

		var client = frame.client;
		float hitAge = hitFired ? hit.sample(frame.nanos, client.isPaused()) : -1f;
		lastHitAge = hitAge;
		boolean owner = client.player != null && client.player.getId() == sourceId;
		float intensity = BastionAnimation.Bulwark.intensity(age, off);
		var entity = client.level.getEntity(sourceId);
		if ((entity == null || !entity.isAlive()) && Float.isNaN(offAt)) offAt = Math.min(age, off);
		var feet = entity != null ? entity.getPosition(frame.partial) : lastBody;
		lastBody = feet;

		float fx = (float) (feet.x - frame.camera.x);
		float fy = (float) (feet.y - frame.camera.y);
		float fz = (float) (feet.z - frame.camera.z);
		float[] values = slot.values;
		if (fx * fx + fy * fy + fz * fz <= 48 * 48) {
			values[slot.bounds] = fx; values[slot.bounds + 1] = fy; values[slot.bounds + 2] = fz;
			values[slot.bounds + 3] = entity != null ? entity.getBbHeight() : 1.8f;
		} else if (!owner) return false;

		values[slot.style] = intensity;
		values[slot.style + 1] = age;
		values[slot.style + 2] = owner && client.options.getCameraType().isFirstPerson() ? 11 : 10;
		values[slot.style + 3] = client.options.screenEffectScale().get().floatValue();

		int material = slot.material;
		values[material] = entity instanceof LivingEntity living ? Math.clamp(living.getAbsorptionAmount() / 4, 0, 1) : 0;
		values[material + 1] = BastionAnimation.Bulwark.hit(hitAge);
		values[material + 2] = intensity;
		values[material + 3] = BastionAnimation.Bulwark.burst(age);
		values[material + 4] = (float) hitDirection.x;
		values[material + 5] = (float) hitDirection.y;
		values[material + 6] = (float) hitDirection.z;
		values[material + 7] = hitAge;
		values[material + 8] = Float.isNaN(offAt) ? off : offAt;
		return false;
	}

	@Override
	public void submitGeometry(PoseStack poseStack, SubmitNodeCollector collector, Vec3 cameraPos) {
		Minecraft mc = Minecraft.getInstance();
		lastAge = time.sample(System.nanoTime(), mc.isPaused());
		if (expired() || mc.level == null) return;

		float age = lastAge;
		float off = off();
		float intensity = BastionAnimation.Bulwark.intensity(age, off);
		if (intensity <= 0.02f) return;

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		var entity = mc.level.getEntity(sourceId);
		Vec3 feet = entity != null ? entity.getPosition(partial) : lastBody;
		lastBody = feet;
		float height = entity != null ? entity.getBbHeight() : 1.8f;

		double dx = feet.x - cameraPos.x;
		double dy = feet.y - cameraPos.y;
		double dz = feet.z - cameraPos.z;
		if (dx * dx + dy * dy + dz * dz > 48.0 * 48.0) return;

		boolean firstPersonOwner = mc.player != null && mc.player.getId() == sourceId
				&& mc.options.getCameraType().isFirstPerson();
		float hitAge = hitFired ? lastHitAge : -1f;
		float exitAge = Math.max(0.0f, age - off);
		float release = smoothstep(0.0f, 0.62f, exitAge);

		poseStack.pushPose();
		poseStack.translate((float) dx, (float) dy, (float) dz);

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.CORE_RENDER_TYPE,
				(pose, vc) -> emitBastionShieldsAndKeys(pose, vc, age, height, intensity, release, hitAge, firstPersonOwner));

		collector.submitCustomGeometry(poseStack, WeaponVoxelRenderer.GLOW_RENDER_TYPE,
				(pose, vc) -> WeaponVoxelRenderer.emitGlowSprite(pose, vc, 0f, height * 0.48f, 0f,
						1.05f + 0.15f * BastionAnimation.Bulwark.hit(hitAge),
						1.00f, 0.78f, 0.20f, 0.22f * intensity * (firstPersonOwner ? 0.35f : 1.0f)));

		poseStack.popPose();
	}

	private void emitBastionShieldsAndKeys(PoseStack.Pose pose, VertexConsumer vc,
			float age, float height, float intensity, float release, float hitAge, boolean firstPersonOwner) {
		float hitStrength = BastionAnimation.Bulwark.hit(hitAge);
		boolean directed = hitDirection.lengthSqr() > 0.01;
		Vec3 normHit = directed ? hitDirection.normalize() : new Vec3(0, 0, 1);

		// Six hexagonal golden bastion plates orbiting smoothly around the player
		PoseStack tmp = new PoseStack();
		for (int k = 0; k < 6; k++) {
			float delay = k * 0.020f;
			float localAge = age - delay;
			if (localAge <= 0.0f) continue;

			float spring = BastionAnimation.assembly(localAge / 0.28f);
			float angle = k * (float) (Math.PI / 3.0) + (float) Math.sin(age * 0.65f + k * 0.5f) * 0.03f;
			float radX = (float) Math.cos(angle);
			float radZ = (float) Math.sin(angle);

			float angularDot = directed ? (float) Math.max(0.0, radX * normHit.x + radZ * normHit.z) : 0.25f;
			float recoil = hitStrength * angularDot * 0.14f;
			float radius = 0.86f + (1.0f - spring) * 0.72f + release * 0.62f - recoil;

			float py = height * (firstPersonOwner ? 0.22f : (0.48f + (k % 2 == 0 ? 0.08f : -0.06f)))
					+ (1.0f - spring) * 0.32f + release * 0.22f;
			float alpha = intensity * smoothstep(0.0f, 0.05f, localAge) * (firstPersonOwner ? 0.42f : 0.92f);

			tmp.pushPose();
			tmp.last().pose().set(pose.pose())
					.translate(radX * radius, py, radZ * radius)
					.rotateY((float) (Math.PI * 0.5) - angle);
			PoseStack.Pose localPose = tmp.last();

			float pScale = firstPersonOwner ? 0.55f : 1.0f;
			float excite = angularDot * hitStrength;

			// Amber-bronze outer armor body
			WeaponVoxelRenderer.emitCube(localPose, vc, 0f, 0f, 0f,
					0.24f * pScale, 0.36f * pScale, 0.042f,
					0.88f + 0.12f * excite, 0.58f + 0.26f * excite, 0.08f + 0.30f * excite, alpha * 0.90f);
			// Dark bronze side pillars
			for (int s = -1; s <= 1; s += 2) {
				WeaponVoxelRenderer.emitCube(localPose, vc, s * 0.25f * pScale, 0f, 0.01f,
						0.042f * pScale, 0.32f * pScale, 0.056f,
						0.42f, 0.22f, 0.03f, alpha);
			}
			// Glowing yellow center rib & top crown
			WeaponVoxelRenderer.emitCube(localPose, vc, 0f, 0.02f, 0.038f,
					0.045f * pScale, 0.28f * pScale, 0.022f,
					1.00f, 0.90f + 0.10f * excite, 0.34f + 0.45f * excite, alpha);
			WeaponVoxelRenderer.emitCube(localPose, vc, 0f, 0.36f * pScale, 0.01f,
					0.16f * pScale, 0.045f * pScale, 0.045f,
					1.00f, 0.84f, 0.24f, alpha);
			tmp.popPose();
		}

		// Six ground anchor keys locking the fortress into the earth
		float keyAlpha = intensity * (1.0f - release * 0.85f) * 0.82f;
		if (keyAlpha > 0.02f) {
			for (int k = 0; k < 6; k++) {
				float spring = BastionAnimation.assembly((age - k * 0.014f) / 0.22f);
				float a = k * (float) (Math.PI / 3.0) + 0.5236f;
				float r = 0.78f + (1.0f - spring) * 0.38f + release * 0.20f;
				float kx = (float) Math.cos(a) * r;
				float kz = (float) Math.sin(a) * r;
				float ky = 0.10f + Math.abs(1.0f - spring) * 0.18f;
				WeaponVoxelRenderer.emitCube(pose, vc, kx, ky, kz, 0.065f, 0.12f, 0.065f,
						0.96f, 0.72f, 0.14f, keyAlpha);
				WeaponVoxelRenderer.emitCube(pose, vc, kx, ky + 0.12f, kz, 0.040f, 0.05f, 0.040f,
						1.00f, 0.94f, 0.46f, keyAlpha);
			}
		}
	}

	private static float smoothstep(float low, float high, float x) {
		float t = Math.clamp((x - low) / (high - low), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
