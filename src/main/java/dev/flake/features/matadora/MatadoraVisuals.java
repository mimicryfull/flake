package dev.flake.features.matadora;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;

public final class MatadoraVisuals {
	/** Full buff plus its fade-out, shared by the owner overlay and observer aura. */
	private static final int DURATION = (int) Math.ceil(MaskAnimation.BUFF_SECONDS * 20) + 12;

	private MatadoraVisuals() {}

	public static void mask(ServerPlayer source, boolean on) {
		var phase = on ? CombatVfxPayload.Phase.MASK_ON : CombatVfxPayload.Phase.MASK_OFF;
		CombatVisuals.broadcast(source, new CombatVfxPayload(source.position(), source.getId(), phase),
				source.position(), on ? DURATION : 0);
	}
}
