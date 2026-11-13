package dev.flake.features.passives.client;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.render.vfx.TimedActor;
import net.minecraft.world.phys.Vec3;

/**
 * Client actor for Hunter Compass: tracks a low-HP target entity each frame and feeds
 * camera-relative capsule data to the {@code hunterHighlight} post-shader pass (style 17).
 */
public final class HunterCompassActor extends TimedActor {
	public static final float DURATION = 0.65f;
	private static final float STYLE_ID = 17.0f;

	private final int targetId;
	private float ageSec;

	public HunterCompassActor(CombatVfxPayload payload) {
		super(payload);
		this.targetId = payload.targetId();
	}

	@Override
	public boolean replaces(CombatVfxPayload payload) {
		return payload.phase() == CombatVfxPayload.Phase.HUNTER_HIGHLIGHT
				&& payload.sourceId() == sourceId
				&& payload.targetId() == targetId;
	}

	@Override
	public boolean expired() {
		return ageSec >= DURATION;
	}

	@Override
	public boolean write(RenderFrame frame, SlotWriter slot) {
		ageSec = age(frame);
		if (ageSec >= DURATION) return true;
		var client = frame.client;
		if (client.player != null && client.player.getId() != sourceId) {
			return true;
		}
		if (client.player == null || client.level == null) {
			return false;
		}
		var target = targetId >= 0 ? client.level.getEntity(targetId) : null;
		if (target != null && !target.isAlive()) return true;

		Vec3 pos = target != null ? target.getPosition(frame.partial) : origin;
		float height = target != null ? target.getBbHeight() : 1.8f;

		float[] values = slot.values;
		values[slot.position] = (float) (pos.x - frame.camera.x);
		values[slot.position + 1] = (float) (pos.y + height * 0.5 - frame.camera.y);
		values[slot.position + 2] = (float) (pos.z - frame.camera.z);
		values[slot.position + 3] = height;

		values[slot.style] = 1.0f;
		values[slot.style + 1] = ageSec;
		values[slot.style + 2] = STYLE_ID;
		values[slot.style + 3] = 0.0f;
		return false;
	}
}
