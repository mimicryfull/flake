package dev.flake.features.phasecrystal;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class PhaseCrystalVisuals {
	private PhaseCrystalVisuals() {}

	public static void phaseOn(ServerPlayer source) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(source.position(), source.getId(),
				CombatVfxPayload.Phase.PHASE_CRYSTAL_ON), source.position(), 44);
	}

	public static void phaseOff(ServerPlayer source, Vec3 destPos) {
		CombatVisuals.sendToLevel(source, new CombatVfxPayload(destPos, source.getId(),
				CombatVfxPayload.Phase.PHASE_CRYSTAL_OFF, 0.0f, 0, destPos));
	}
}
