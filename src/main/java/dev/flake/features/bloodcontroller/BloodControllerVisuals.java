package dev.flake.features.bloodcontroller;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Server-side VFX broadcasts for Blood Controller's Voodoo Doll and Blood Drain skills. */
public final class BloodControllerVisuals {
	private BloodControllerVisuals() {}

	public static void voodooOn(ServerPlayer owner, LivingEntity target, Vec3 dollPos, float yawRad) {
		Vec3 targetChest = chestPos(target);
		var payload = new CombatVfxPayload(dollPos, owner.getId(), CombatVfxPayload.Phase.VOODOO_DOLL_ON,
				yawRad, target.getId(), targetChest);
		CombatVisuals.broadcast(owner, payload, dollPos, BloodControllerRuntime.VOODOO_TICKS + 12);
	}

	public static void voodooHit(ServerPlayer attacker, ServerLevel level, int ownerId, Vec3 dollPos,
			LivingEntity target, float transferredDamage) {
		ServerPlayer anchor = attacker != null ? attacker : (level.players().isEmpty() ? null : level.players().getFirst());
		if (anchor == null) return;
		Vec3 targetChest = chestPos(target);
		var payload = new CombatVfxPayload(dollPos, ownerId, CombatVfxPayload.Phase.VOODOO_DOLL_HIT,
				transferredDamage, target.getId(), targetChest);
		CombatVisuals.sendToLevel(anchor, payload);
	}

	public static void voodooOff(ServerLevel level, ServerPlayer owner, int ownerId, Vec3 dollPos) {
		ServerPlayer anchor = owner != null ? owner : (level.players().isEmpty() ? null : level.players().getFirst());
		if (anchor == null) return;
		var payload = new CombatVfxPayload(dollPos, ownerId, CombatVfxPayload.Phase.VOODOO_DOLL_OFF);
		CombatVisuals.sendToLevel(anchor, payload);
	}

	public static void bloodDrain(ServerPlayer caster, LivingEntity victim, float amount) {
		Vec3 from = chestPos(victim);
		Vec3 to = chestPos(caster);
		var payload = new CombatVfxPayload(from, caster.getId(), CombatVfxPayload.Phase.BLOOD_DRAIN,
				amount, victim.getId(), to);
		CombatVisuals.broadcast(caster, payload, from, 22);
	}

	private static Vec3 chestPos(LivingEntity entity) {
		return entity.position().add(0.0, entity.getBbHeight() * 0.58, 0.0);
	}
}
