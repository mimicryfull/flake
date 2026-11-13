package dev.flake.features.chainblade;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class ChainBladeVisuals {
	private static final int DURATION = 16;

	private ChainBladeVisuals() {}

	public static void pull(ServerPlayer source, LivingEntity target) {
		Vec3 from = source.getEyePosition().add(0, -0.35, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0);
		CombatVisuals.broadcast(source, new CombatVfxPayload(from, source.getId(),
				CombatVfxPayload.Phase.CHAIN_BLADE_PULL, 0.0f, target.getId(), to), from, DURATION);
	}
}
