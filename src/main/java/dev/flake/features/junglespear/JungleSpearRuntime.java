package dev.flake.features.junglespear;

import dev.flake.combat.Cooldowns;
import dev.flake.combat.ability.AbilityMovement;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.features.arena.Arena;
import dev.flake.features.duel.DuelRuntime;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * «Джунглеве спис»: 6 базової шкоди з пасивно збільшеною дальністю удару.
 * ПКМ — ривок на 3 блоки, що завдає шкоди звичайної атаки всім на шляху.
 */
public final class JungleSpearRuntime {
	public static final int COOLDOWN_TICKS = 200;
	public static final double DASH_DISTANCE = 3.0;
	public static final double REACH_BONUS = 1.5;
	public static final double HIT_RADIUS = 1.25;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "jungle_spear");
	public static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath("flake", "jungle_spear_dash");
	private static final Identifier REACH_MODIFIER = Identifier.fromNamespaceAndPath("flake", "jungle_spear_reach");

	private static SoundEvent sound;

	private JungleSpearRuntime() {}

	public static Item ITEM;

	static void bind(Item item) { ITEM = item; }
	static void bindSound(SoundEvent event) { sound = event; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isSpear(ItemStack stack) { return ITEM != null && stack.is(ITEM); }

	public static void applyReach(ServerPlayer player) {
		var attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (attribute == null) return;
		attribute.removeModifier(REACH_MODIFIER);
		attribute.addTransientModifier(new AttributeModifier(REACH_MODIFIER, REACH_BONUS, AttributeModifier.Operation.ADD_VALUE));
	}

	public static void removeReach(ServerPlayer player) {
		var attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (attribute != null) attribute.removeModifier(REACH_MODIFIER);
	}

	public static boolean dash(ServerPlayer player) {
		if (DuelRuntime.blocksAbility(player.getUUID()) || !(player.level() instanceof ServerLevel level)) return false;
		Vec3 direction = AbilityMovement.flat(player);
		Vec3 start = player.position();
		Vec3 eye = player.getEyePosition();
		Vec3 end = start.add(direction.scale(DASH_DISTANCE));
		var clip = level.clip(new ClipContext(eye, eye.add(direction.scale(DASH_DISTANCE)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (clip.getType() != HitResult.Type.MISS) {
			double travelled = Math.max(0.0, start.distanceTo(clip.getLocation()) - 0.45);
			end = start.add(direction.scale(Math.min(DASH_DISTANCE, travelled)));
		}
		end = clampToArena(player, start, end, direction);

		float damage = Math.max(1.0f, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
		Vec3 centre = start.add(end).scale(0.5);
		for (LivingEntity target : targets(player, level, centre)) {
			if (distanceToSegment(target.position().add(0, target.getBbHeight() * 0.5, 0), start, end) > HIT_RADIUS) continue;
			target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		}

		player.connection.teleport(end.x, end.y, end.z, player.getYRot(), player.getXRot());
		player.getCooldowns().addCooldown(ID, Cooldowns.ticks(player, COOLDOWN_TICKS));
		JungleSpearVisuals.dash(player, start, end);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.1f);
		return true;
	}

	private static List<LivingEntity> targets(ServerPlayer player, ServerLevel level, Vec3 centre) {
		double radius = DASH_DISTANCE * 0.5 + HIT_RADIUS + 0.5;
		return AbilityTargeting.candidates(player, level, centre, radius).stream()
				.filter(entity -> entity.distanceToSqr(player) <= (DASH_DISTANCE + HIT_RADIUS) * (DASH_DISTANCE + HIT_RADIUS))
				.toList();
	}

	private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double lengthSqr = segment.lengthSqr();
		if (lengthSqr < 1e-6) return point.distanceTo(start);
		double t = Math.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0, 1.0);
		return point.distanceTo(start.add(segment.scale(t)));
	}

	/** A dash must never step outside a live arena: MatchRuntime cancels a duel the moment a fighter leaves. */
	private static Vec3 clampToArena(ServerPlayer player, Vec3 start, Vec3 end, Vec3 direction) {
		var match = DuelRuntime.of(player.getUUID());
		if (match == null || match.arena() == null) return end;
		var bounds = match.arena().bounds();
		Vec3 candidate = end;
		for (int step = 0; step < 24; step++) {
			if (bounds.contains(new Arena.Position(candidate.x, candidate.y, candidate.z, 0f, 0f))) return candidate;
			candidate = candidate.subtract(direction.scale(0.25));
			if (candidate.distanceToSqr(start) < 0.0004) break;
		}
		return start;
	}

	public static void reset(ServerPlayer player) {
		removeReach(player);
		player.getCooldowns().removeCooldown(ID);
	}

	/** The extra reach belongs to the held spear only; switching slots drops the modifier. */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				var attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
				if (attribute == null) continue;
				boolean held = ITEM != null && player.getMainHandItem().is(ITEM);
				boolean has = attribute.hasModifier(REACH_MODIFIER);
				if (held && !has) applyReach(player);
				else if (!held && has) removeReach(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeReach(handler.player));
	}
}
