package dev.flake.features.voidanchor;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class VoidAnchorVisuals {
	private VoidAnchorVisuals() {}

	public static void cast(ServerPlayer source, Vec3 anchorPos) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(anchorPos, source.getId(),
				CombatVfxPayload.Phase.VOID_ANCHOR_CAST, 0.0f, 0, anchorPos), anchorPos, 35);
	}

	public static void pull(ServerPlayer source, Vec3 anchorPos) {
		CombatVisuals.broadcast(source, new CombatVfxPayload(anchorPos, source.getId(),
				CombatVfxPayload.Phase.VOID_ANCHOR_PULL, 0.0f, 0, anchorPos), anchorPos, 16);
	}
}
