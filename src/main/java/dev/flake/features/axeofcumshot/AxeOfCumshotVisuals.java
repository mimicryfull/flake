package dev.flake.features.axeofcumshot;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class AxeOfCumshotVisuals {
	private AxeOfCumshotVisuals() {}

	public static void launchAxe(ServerPlayer source, Vec3 start, Vec3 end, float speedPerTick, int maxTicks) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(start, source.getId(),
				CombatVfxPayload.Phase.AXE_THROW, speedPerTick, 0, end), start, maxTicks);
	}

	public static void impact(ServerPlayer source, Vec3 pos, int targetId) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(pos, source.getId(),
				CombatVfxPayload.Phase.HAMMER_STUN, 0.85f, targetId, pos), pos, 10);
	}
}
