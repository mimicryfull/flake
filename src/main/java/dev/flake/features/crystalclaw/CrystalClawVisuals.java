package dev.flake.features.crystalclaw;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;

public final class CrystalClawVisuals {
	private static final int DURATION = (int) Math.ceil(CrystalClawAnimation.BUFF_SECONDS * 20) + 12;

	private CrystalClawVisuals() {}

	public static void crystal(ServerPlayer source, boolean on) {
		var phase = on ? CombatVfxPayload.Phase.CRYSTAL_ON : CombatVfxPayload.Phase.CRYSTAL_OFF;
		CombatVisuals.broadcast(source, new CombatVfxPayload(source.position(), source.getId(), phase),
				source.position(), on ? DURATION : 0);
	}
}
