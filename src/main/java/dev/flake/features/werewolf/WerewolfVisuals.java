package dev.flake.features.werewolf;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Server-side VFX broadcasts for Werewolf abilities and Shadow Claws bleed. */
public final class WerewolfVisuals {
	private WerewolfVisuals() {}

	public static void clawsOn(ServerPlayer owner) {
		var payload = new CombatVfxPayload(owner.position(), owner.getId(), CombatVfxPayload.Phase.WEREWOLF_CLAWS_ON,
				(float) Math.toRadians(owner.getYRot()), -1, owner.position());
		CombatVisuals.broadcast(owner, payload, owner.position(), WerewolfRuntime.CLAWS_TICKS + 8);
	}

	public static void clawsOff(ServerPlayer owner) {
		var payload = new CombatVfxPayload(owner.position(), owner.getId(), CombatVfxPayload.Phase.WEREWOLF_CLAWS_OFF);
		CombatVisuals.sendToLevel(owner, payload);
	}

	public static void naturesCallOn(ServerPlayer owner) {
		var payload = new CombatVfxPayload(owner.position(), owner.getId(), CombatVfxPayload.Phase.WEREWOLF_CALL_ON,
				(float) Math.toRadians(owner.getYRot()), -1, owner.position());
		CombatVisuals.broadcast(owner, payload, owner.position(), WerewolfRuntime.NATURES_CALL_TICKS + 8);
	}

	public static void naturesCallOff(ServerPlayer owner) {
		var payload = new CombatVfxPayload(owner.position(), owner.getId(), CombatVfxPayload.Phase.WEREWOLF_CALL_OFF);
		CombatVisuals.sendToLevel(owner, payload);
	}

	public static void shadowBleed(ServerPlayer anchor, LivingEntity victim, boolean procFlash) {
		if (anchor == null || victim == null) return;
		Vec3 pos = victim.position().add(0.0, victim.getBbHeight() * 0.52, 0.0);
		var payload = new CombatVfxPayload(pos, victim.getId(), CombatVfxPayload.Phase.SHADOW_BLEED,
				procFlash ? 1.0f : 0.0f, victim.getId(), pos);
		CombatVisuals.broadcast(anchor, payload, pos, 16);
	}
}
