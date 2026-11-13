package dev.flake.features.mirrortrap;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class MirrorTrapVisuals {
	private MirrorTrapVisuals() {}

	public static void trap(ServerPlayer source, boolean on) {
		var phase = on ? CombatVfxPayload.Phase.MIRROR_TRAP_ON : CombatVfxPayload.Phase.MIRROR_TRAP_OFF;
		var payload = new CombatVfxPayload(source.position(), source.getId(), phase);
		if (on) {
			CombatVisuals.broadcast(source, payload, source.position(), 0);
		} else {
			CombatVisuals.sendToLevel(source, payload);
		}
	}

	public static void reflect(ServerPlayer source, Vec3 hitPos, Vec3 dir) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(hitPos, source.getId(),
				CombatVfxPayload.Phase.MIRROR_TRAP_REFLECT, 0.0f, 0, dir), hitPos, 8);
	}
}
