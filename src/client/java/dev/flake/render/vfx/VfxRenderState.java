package dev.flake.render.vfx;


import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;

/** Shared slot budget, world lifetime, and frame upload. Effect behavior belongs to feature actors. */
public final class VfxRenderState {
	public enum Admission { COALESCE, UPDATE_ALL, UPDATE_FIRST, REPLACE }
	private static java.util.function.Function<CombatVfxPayload.Phase, Admission> admissionPolicy;
	private static java.util.function.BiFunction<CombatVfxPayload, ClientLevel, EffectActor> actorFactory;

	/** Wired explicitly by client bootstrap; infrastructure never imports concrete feature actors. */
	public static void configure(java.util.function.Function<CombatVfxPayload.Phase, Admission> policy,
			java.util.function.BiFunction<CombatVfxPayload, ClientLevel, EffectActor> factory) {
		admissionPolicy = java.util.Objects.requireNonNull(policy);
		actorFactory = java.util.Objects.requireNonNull(factory);
	}
	private static final float[] values = new float[VfxBuffer.UNIFORM_BYTES / Float.BYTES];
	private static final SlotWriter[] slots = new SlotWriter[VfxBuffer.MAX_WAVES];
	private static final ArrayList<EffectActor> actors = new ArrayList<>(VfxBuffer.MAX_WAVES);
	private static final RenderFrame frame = new RenderFrame();
	private static final Matrix4f viewProjection = new Matrix4f(), inverseViewProjection = new Matrix4f();
	private static final VfxTimeline impactTime = new VfxTimeline();
	private static boolean impacting;
	private static ClientLevel level;

	static {
		for (int i = 0; i < slots.length; i++) slots[i] = new SlotWriter(values, i);
	}

	private VfxRenderState() {}

	public static void checkWorld(ClientLevel current) {
		if (level != current) clear();
	}

	public static void receive(CombatVfxPayload payload) {
		if (payload.phase() == CombatVfxPayload.Phase.CLEAR) { clear(); return; }
		var current = Minecraft.getInstance().level;
		if (current == null) return;
		checkWorld(current);
		level = current;
		if (payload.phase() == CombatVfxPayload.Phase.IMPACT) {
			impactTime.reset(); impacting = true;
			return;
		}
		var admission = admissionPolicy.apply(payload.phase());
		if (admission == Admission.COALESCE) {
			// One rig per source: aura/lunge/two siphons coexist in a single slot.
			for (var actor : actors) if (actor.receive(payload)) return;
			if (payload.phase() == CombatVfxPayload.Phase.BLOODLUST_OFF) return;
			admit(payload);
			return;
		}
		if (admission == Admission.UPDATE_ALL) {
			for (var actor : actors) actor.receive(payload);
			return;
		}
		if (admission == Admission.UPDATE_FIRST) {
			for (var actor : actors) if (actor.receive(payload)) return;
			return;
		}
		actors.removeIf(actor -> actor.replaces(payload));
		actors.removeIf(actor -> actor.charging() && actor.sourceId() == payload.sourceId());
		if (payload.phase() == CombatVfxPayload.Phase.CANCEL) return;
		admit(payload);
	}

	private static void admit(CombatVfxPayload payload) {
		if (actors.size() == VfxBuffer.MAX_WAVES) actors.removeFirst();
		actors.add(actorFactory.apply(payload, level));
	}

	public static void clear() {
		actors.clear(); level = null; impacting = false;
		impactTime.reset();
		// The reusable frame must not retain an old world through its Minecraft/camera references.
		frame.client = null; frame.camera = null;
		Arrays.fill(values, 0);
	}

	/** Submits any world-space VFX meshes owned by active actors. */
	public static void submitGeometry(com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.SubmitNodeCollector collector,
			net.minecraft.world.phys.Vec3 cameraPos) {
		if (actors.isEmpty()) return;
		for (int i = 0; i < actors.size(); i++) {
			actors.get(i).submitGeometry(poseStack, collector, cameraPos);
		}
	}

	/** One reusable CPU array, one PostPass-owned UBO, and no per-frame slot objects. */
	public static float[] uniforms() {
		Arrays.fill(values, 0);
		var client = Minecraft.getInstance();
		if (client.level == null || client.level != level) { clear(); return values; }
		if (actors.isEmpty() && !impacting) return values;
		long now = System.nanoTime();
		var camera = client.gameRenderer.mainCamera();
		camera.getViewRotationProjectionMatrix(viewProjection).get(values, 0);
		inverseViewProjection.set(viewProjection).invert().get(values, 16);
		boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
		values[32] = zeroToOne ? 1 : 2;
		values[33] = zeroToOne ? 0 : -1;
		int quality = switch (client.options.particles().get()) {
			case MINIMAL -> 0;
			case DECREASED -> 1;
			default -> 2;
		};
		values[34] = quality;
		values[35] = 4 + quality * 4;
		if (impacting) {
			float age = impactTime.sample(now, client.isPaused());
			float strength = client.options.screenEffectScale().get().floatValue();
			values[VfxBuffer.IMPACT_OFFSET] = ImpactAnimation.displacement(age) * strength;
			values[VfxBuffer.IMPACT_OFFSET + 1] = ImpactAnimation.envelope(age) * strength;
			values[VfxBuffer.IMPACT_OFFSET + 2] = 0.5f;
			values[VfxBuffer.IMPACT_OFFSET + 3] = 0.5f;
			if (age >= ImpactAnimation.DURATION) impacting = false;
		}
		frame.begin(client, camera.position(), now, quality);
		for (int index = 0; index < actors.size();) {
			if (actors.get(index).write(frame, slots[index])) { actors.remove(index); continue; }
			index++;
		}
		return values;
	}
}
