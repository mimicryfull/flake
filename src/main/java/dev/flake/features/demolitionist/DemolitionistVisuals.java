package dev.flake.features.demolitionist;

import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-side VFX broadcasts for Demolitionist's Proximity Mine and Blast Jump. */
public final class DemolitionistVisuals {
	private static final int EXPLOSION_SHADER_TICKS = 14;

	private DemolitionistVisuals() {}

	public static void placeMine(ServerPlayer owner, Vec3 pos) {
		var payload = new CombatVfxPayload(pos, owner.getId(), CombatVfxPayload.Phase.MINE_PLACE,
				(float) Math.toRadians(owner.getYRot()), -1, pos);
		CombatVisuals.broadcast(owner, payload, pos, 0);
	}

	public static void disarmMine(ServerLevel level, ServerPlayer owner, int ownerId, Vec3 pos) {
		ServerPlayer anchor = owner != null ? owner : (level.players().isEmpty() ? null : level.players().getFirst());
		if (anchor == null) return;
		var payload = new CombatVfxPayload(pos, ownerId, CombatVfxPayload.Phase.MINE_DISARM);
		CombatVisuals.sendToLevel(anchor, payload);
	}

	public static void detonateMine(ServerPlayer owner, Vec3 pos, int victimId) {
		var payload = new CombatVfxPayload(pos, owner.getId(), CombatVfxPayload.Phase.MINE_DETONATE,
				0.0f, victimId, pos);
		CombatVisuals.broadcast(owner, payload, pos, EXPLOSION_SHADER_TICKS);
	}

	public static void blastJump(ServerPlayer source, Vec3 origin, Vec3 launchVelocity) {
		var payload = new CombatVfxPayload(origin, source.getId(), CombatVfxPayload.Phase.BLAST_JUMP,
				(float) Math.toRadians(source.getYRot()), -1, launchVelocity);
		CombatVisuals.broadcast(source, payload, origin, EXPLOSION_SHADER_TICKS);
	}
}
