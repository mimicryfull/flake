package dev.flake.features.stormstaff;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.Stuns;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.features.duel.DuelRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * «Грозовий жезл» (Storm Staff): слабка ближня атака (5.0),
 * ПКМ — блискавичний промінь уздовж погляду до 8 блоків (зупиняється блоками),
 * завдає 4.0 шкоди цілям у капсулі радіусом ~0.9 м і оглушує на 0.3 с (6 тіків).
 */
public final class StormStaffRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 120;
	public static final int COOLDOWN_TICKS = 20;
	public static final double RANGE = 8.0;
	public static final double BEAM_RADIUS = 0.9;
	public static final float BEAM_DAMAGE = 4.0f;
	public static final int STUN_TICKS = 6;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "storm_staff");

	public static Item ITEM;

	private StormStaffRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isStaff(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	public static boolean castRay(ServerPlayer player) {
		if (DuelRuntime.blocksAbility(player.getUUID()) || !(player.level() instanceof ServerLevel level)) return false;
		if (!isStaff(player.getMainHandItem()) && !isStaff(player.getOffhandItem())) return false;

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		Vec3 maxEnd = eye.add(look.scale(RANGE));
		var clip = level.clip(new ClipContext(eye, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = clip.getType() != HitResult.Type.MISS ? clip.getLocation() : maxEnd;

		Vec3 rawRight = look.cross(new Vec3(0, 1, 0));
		Vec3 right = rawRight.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : rawRight.normalize();
		Vec3 muzzle = eye.add(look.scale(0.62)).add(right.scale(0.30)).add(0, -0.22, 0);

		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.getCooldowns().addCooldown(ID, Cooldowns.ticks(player, COOLDOWN_TICKS));

		Vec3 mid = eye.add(end).scale(0.5);
		double searchRadius = eye.distanceTo(end) * 0.5 + BEAM_RADIUS + 2.0;
		double maxDistSq = BEAM_RADIUS * BEAM_RADIUS;

		for (LivingEntity target : AbilityTargeting.candidates(player, level, mid, searchRadius)) {
			Vec3 bodyLow = target.position().add(0.0, Math.min(0.2, target.getBbHeight() * 0.15), 0.0);
			Vec3 bodyHigh = target.position().add(0.0, Math.max(0.4, target.getBbHeight() * 0.95), 0.0);
			if (distanceSqSegmentToSegment(eye, end, bodyLow, bodyHigh) > maxDistSq) continue;

			float damage = CombatModifiers.skillDamage(player, target, BEAM_DAMAGE);
			target.hurtServer(level, player.damageSources().playerAttack(player), damage);
			Stuns.apply(target, STUN_TICKS);
		}

		StormStaffVisuals.ray(player, muzzle, end);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z,
				SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.45f, 1.55f);
		level.playSound(null, end.x, end.y, end.z,
				SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.32f, 1.70f);
		return true;
	}

	/** Squared minimum distance between two 3D line segments [a0, a1] and [b0, b1]. */
	public static double distanceSqSegmentToSegment(Vec3 a0, Vec3 a1, Vec3 b0, Vec3 b1) {
		Vec3 u = a1.subtract(a0);
		Vec3 v = b1.subtract(b0);
		Vec3 w = a0.subtract(b0);
		double a = u.dot(u);
		double b = u.dot(v);
		double c = v.dot(v);
		double d = u.dot(w);
		double e = v.dot(w);
		double denom = a * c - b * b;

		double sN, sD = denom;
		double tN, tD = denom;
		final double eps = 1e-8;

		if (denom < eps) {
			sN = 0.0;
			sD = 1.0;
			tN = e;
			tD = c;
		} else {
			sN = b * e - c * d;
			tN = a * e - b * d;
			if (sN < 0.0) {
				sN = 0.0;
				tN = e;
				tD = c;
			} else if (sN > sD) {
				sN = sD;
				tN = e + b;
				tD = c;
			}
		}

		if (tN < 0.0) {
			tN = 0.0;
			if (-d < 0.0) {
				sN = 0.0;
			} else if (-d > a) {
				sN = sD;
			} else {
				sN = -d;
				sD = a;
			}
		} else if (tN > tD) {
			tN = tD;
			if (-d + b < 0.0) {
				sN = 0.0;
			} else if (-d + b > a) {
				sN = sD;
			} else {
				sN = -d + b;
				sD = a;
			}
		}

		double sc = Math.abs(sN) < eps ? 0.0 : sN / sD;
		double tc = Math.abs(tN) < eps ? 0.0 : tN / tD;
		Vec3 closestDiff = w.add(u.scale(sc)).subtract(v.scale(tc));
		return closestDiff.lengthSqr();
	}

	public static void register() {}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
	}
}
