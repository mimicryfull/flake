package dev.flake.features.dispatcher;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-side VFX broadcasts for Dispatcher's 8-block Dispatch slash and 4-soldier Desert Squad. */
public final class DispatcherVisuals {
	private DispatcherVisuals() {}

	public static void dispatchSlash(ServerPlayer owner, Vec3 startPos, Vec3 endPos, float yawRad) {
		var payload = new CombatVfxPayload(startPos, owner.getId(), CombatVfxPayload.Phase.DISPATCH_SLASH,
				yawRad, -1, endPos);
		CombatVisuals.broadcast(owner, payload, startPos, DispatcherRuntime.DISPATCH_TICKS + 26);
	}

	public static void squadOn(ServerPlayer owner, float yawRad) {
		var payload = new CombatVfxPayload(owner.position(), owner.getId(), CombatVfxPayload.Phase.DISPATCHER_SQUAD_ON,
				yawRad, -1, owner.position());
		CombatVisuals.broadcast(owner, payload, owner.position(), DispatcherRuntime.SQUAD_TICKS + 12);
	}

	public static void squadSync(ServerPlayer owner, int soldierIndex, Vec3 pos, float yawRad) {
		var payload = new CombatVfxPayload(pos, owner.getId(), CombatVfxPayload.Phase.DISPATCHER_SQUAD_SYNC,
				yawRad, soldierIndex, pos);
		CombatVisuals.broadcast(owner, payload, pos, DispatcherRuntime.SQUAD_TICKS + 12);
	}

	public static void squadShot(ServerPlayer owner, Vec3 soldierPos, float yawRad, int targetId, Vec3 targetChest) {
		var payload = new CombatVfxPayload(soldierPos, owner.getId(), CombatVfxPayload.Phase.DISPATCHER_SQUAD_SHOT,
				yawRad, targetId, targetChest);
		CombatVisuals.broadcast(owner, payload, soldierPos, 18);
	}

	public static void squadOff(ServerLevel level, ServerPlayer owner, int ownerId, Vec3 pos) {
		ServerPlayer anchor = owner != null ? owner : (level.players().isEmpty() ? null : level.players().getFirst());
		if (anchor == null) return;
		var payload = new CombatVfxPayload(pos, ownerId, CombatVfxPayload.Phase.DISPATCHER_SQUAD_OFF);
		CombatVisuals.sendToLevel(anchor, payload);
	}
}
