package dev.flake.features.junglespear;

import dev.flake.combat.Cooldowns;
import dev.flake.combat.ability.AbilityMovement;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.features.arena.Arena;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
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
 * «Джунглеве спис»: зброя з подовженою дистанцією удару та плавним ривком уперед (ПКМ).
 */
public final class JungleSpearRuntime {
	public static final int COOLDOWN_TICKS = 20;
	public static final double DASH_DISTANCE = 4.5;
	public static final double REACH_BONUS = 0.5;
	public static final double HIT_RADIUS = 1.35;
	private static final int DASH_TICKS = 12;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "jungle_spear");
	public static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath("flake", "jungle_spear_dash");
	private static final Identifier REACH_MODIFIER = Identifier.fromNamespaceAndPath("flake", "jungle_spear_reach");

	private static SoundEvent sound;

	private static final class ActiveDash {
		int ticksLeft;
		final Vec3 direction;
		final float damage;
		final Set<UUID> hitIds = new HashSet<>();

		ActiveDash(int ticksLeft, Vec3 direction, float damage) {
			this.ticksLeft = ticksLeft;
			this.direction = direction;
			this.damage = damage;
		}
	}

	private static final class State {
		final Map<UUID, ActiveDash> dashes = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);

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
		AbilityMovement.launch(player, direction.scale(0.92).add(0, 0.14, 0));
		STATES.of(level.getServer()).dashes.put(player.getUUID(), new ActiveDash(DASH_TICKS, direction, damage));

		player.getCooldowns().addCooldown(ID, Cooldowns.ticks(player, COOLDOWN_TICKS));
		JungleSpearVisuals.dash(player, start, end);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.1f);
		return true;
	}

	private static void tickDashes(MinecraftServer server) {
		var dashes = STATES.of(server).dashes;
		if (dashes.isEmpty()) return;

		var it = dashes.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			ActiveDash dash = entry.getValue();
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level) || dash.ticksLeft <= 0) {
				it.remove();
				continue;
			}

			// Keep smooth dash inside duel arena bounds
			var match = DuelRuntime.of(player.getUUID());
			if (match != null && match.arena() != null) {
				var bounds = match.arena().bounds();
				Vec3 next = player.position().add(player.getDeltaMovement());
				if (!bounds.contains(new Arena.Position(next.x, next.y, next.z, 0f, 0f))) {
					AbilityMovement.launch(player, Vec3.ZERO);
					it.remove();
					continue;
				}
			}

			// Sustain smooth forward momentum during the first part of the dash so it feels longer
			if (dash.ticksLeft > 4) {
				double sustain = 0.52 + 0.28 * ((dash.ticksLeft - 4) / (double) (DASH_TICKS - 4));
				AbilityMovement.launch(player, dash.direction.scale(sustain).add(0, player.getDeltaMovement().y * 0.5, 0));
			}

			Vec3 centre = player.position().add(0, player.getBbHeight() * 0.5, 0);
			for (LivingEntity target : AbilityTargeting.candidates(player, level, centre, HIT_RADIUS + 0.6)) {
				if (!dash.hitIds.add(target.getUUID())) continue;
				target.hurtServer(level, player.damageSources().playerAttack(player), dash.damage);
			}
			dash.ticksLeft--;
			if (dash.ticksLeft <= 0) it.remove();
		}
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
		if (player.level() instanceof ServerLevel level) {
			STATES.of(level.getServer()).dashes.remove(player.getUUID());
		}
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
			tickDashes(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			removeReach(handler.player);
			STATES.of(server).dashes.remove(handler.player.getUUID());
		});
	}
}
