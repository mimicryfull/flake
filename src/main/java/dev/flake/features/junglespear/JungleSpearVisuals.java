package dev.flake.features.junglespear;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class JungleSpearVisuals {
	private static final int DURATION = 8;

	private JungleSpearVisuals() {}

	public static void dash(ServerPlayer source, Vec3 start, Vec3 end) {
		var from = start.add(0, source.getBbHeight() * 0.55, 0);
		var to = end.add(0, source.getBbHeight() * 0.55, 0);
		CombatVisuals.broadcast(source, new CombatVfxPayload(from, -1,
				CombatVfxPayload.Phase.SPEAR_DASH, 0.0f, -1, to), from, DURATION);
	}
}
