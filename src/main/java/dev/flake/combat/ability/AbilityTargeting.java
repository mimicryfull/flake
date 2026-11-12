package dev.flake.combat.ability;

import dev.flake.features.duel.DuelRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Existing class target rules shared by directed skills, explosions and proximity mines. */
public final class AbilityTargeting {
	private AbilityTargeting() {}

	public static List<LivingEntity> candidates(ServerPlayer player, ServerLevel level, Vec3 centre, double radius) {
		var list = new ArrayList<LivingEntity>();
		boolean inDuel = DuelRuntime.fighting(player.getUUID());
		for (var other : level.players()) {
			if (other == player || !other.isAlive()) continue;
			if (!inDuel || DuelRuntime.opponents(player.getUUID(), other.getUUID())) list.add(other);
		}
		var box = new AABB(centre.x - radius - 1, centre.y - radius - 1, centre.z - radius - 1,
				centre.x + radius + 1, centre.y + radius + 1, centre.z + radius + 1);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.entityTags().contains("flake_test_dummy")))
			list.add(mob);
		return list;
	}

	/** Best-aligned target in the view cone (not nearest); retain the original tie/order semantics. */
	public static LivingEntity target(ServerPlayer player, double range, double minCos, boolean lineOfSight) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		LivingEntity best = null;
		double bestCos = minCos;
		for (var other : candidates(player, level, player.position(), range)) {
			Vec3 aim = other.position().add(0, other.getBbHeight() * 0.5, 0), delta = aim.subtract(eye);
			double distance = delta.length();
			if (distance > range || distance < 0.3) continue;
			double cos = delta.scale(1 / distance).dot(look);
			if (cos < bestCos) continue;
			if (lineOfSight && level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) continue;
			best = other;
			bestCos = cos;
		}
		return best;
	}
}
