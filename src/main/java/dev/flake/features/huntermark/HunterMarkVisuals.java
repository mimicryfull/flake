package dev.flake.features.huntermark;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class HunterMarkVisuals {
	private HunterMarkVisuals() {}

	public static void markBurst(ServerPlayer owner, LivingEntity target) {
		Vec3 pos = target.position().add(0.0, target.getBbHeight() + 0.35, 0.0);
		CombatVisuals.broadcast(owner, new CombatVfxPayload(pos, owner.getId(),
				CombatVfxPayload.Phase.HUNTER_MARK, 0.0f, target.getId(), pos), pos, 16);
	}

	public static void highlight(ServerPlayer owner, LivingEntity target) {
		Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		CombatVisuals.send(owner, new CombatVfxPayload(pos, owner.getId(),
				CombatVfxPayload.Phase.HUNTER_HIGHLIGHT, 0.0f, target.getId(), pos), 10);
	}
}
