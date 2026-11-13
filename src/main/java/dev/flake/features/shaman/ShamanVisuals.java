package dev.flake.features.shaman;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-side VFX broadcasts for Shaman's Totem of Pain and Spirit Ward. */
public final class ShamanVisuals {
	private ShamanVisuals() {}

	public static void totem(ServerPlayer owner, Vec3 pos) {
		var payload = new CombatVfxPayload(pos, owner.getId(), CombatVfxPayload.Phase.SHAMAN_TOTEM,
				(float) Math.toRadians(owner.getYRot()), -1, pos);
		CombatVisuals.broadcast(owner, payload, pos, 0);
	}

	public static void totemOff(ServerLevel level, ServerPlayer owner, int ownerId, Vec3 pos) {
		ServerPlayer anchor = owner != null ? owner : (level.players().isEmpty() ? null : level.players().getFirst());
		if (anchor == null) return;
		var payload = new CombatVfxPayload(pos, ownerId, CombatVfxPayload.Phase.SHAMAN_TOTEM_OFF);
		CombatVisuals.sendToLevel(anchor, payload);
	}

	public static void spiritWard(ServerPlayer owner) {
		Vec3 origin = owner.position();
		var payload = new CombatVfxPayload(origin, owner.getId(), CombatVfxPayload.Phase.SHAMAN_WARD,
				(float) Math.toRadians(owner.getYRot()), -1, origin);
		CombatVisuals.broadcast(owner, payload, origin, 0);
	}
}
