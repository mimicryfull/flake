package dev.flake.features.voidanchor;

import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Cooldowns;
import dev.flake.combat.ability.AbilityMovement;
import dev.flake.combat.ability.AbilityTargeting;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** «Якір пустоти»: ставить якір у точці прицілу; через 1.5с притягує ворогів у 5 м та завдає 1.5 HP шкоди. */
public final class VoidAnchorRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 240;
	public static final int COOLDOWN_TICKS = 20;
	public static final int DELAY_TICKS = 30;
	public static final double CAST_RANGE = 9.0;
	public static final double PULL_RADIUS = 5.0;
	public static final float DAMAGE = 1.5f;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "void_anchor");

	private record Anchor(UUID ownerId, ServerLevel level, Vec3 pos, int triggerAt) {}

	private static final class State {
		final Map<UUID, Anchor> anchors = new HashMap<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);

	public static Item ITEM;

	private VoidAnchorRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isItem(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VoidAnchorRuntime::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}

	public static boolean activate(ServerPlayer player) {
		UUID id = player.getUUID();
		if (DuelRuntime.blocksAbility(id) || !(player.level() instanceof ServerLevel level)) return false;

		MinecraftServer server = level.getServer();
		int now = server.getTickCount();
		State state = STATES.of(server);
		int readyAt = state.ready.getOrDefault(id, 0);
		if (now < readyAt) {
			dev.flake.combat.CombatFeedback.onCooldown(player, stack(), readyAt - now);
			return false;
		}

		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		state.ready.put(id, now + cooldown);
		player.getCooldowns().addCooldown(ID, cooldown);

		Vec3 anchorPos = resolveAnchorPos(player, level);
		state.anchors.put(id, new Anchor(id, level, anchorPos, now + DELAY_TICKS));

		VoidAnchorVisuals.cast(player, anchorPos);
		level.playSound(null, anchorPos.x, anchorPos.y, anchorPos.z,
				SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.75f, 0.85f);
		return true;
	}

	private static Vec3 resolveAnchorPos(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		Vec3 maxEnd = eye.add(look.scale(CAST_RANGE));
		BlockHitResult hit = level.clip(new ClipContext(eye, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			return player.position();
		}
		if (hit.getDirection() == Direction.UP) {
			return hit.getLocation();
		}
		Vec3 steppedOut = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(0.35));
		BlockHitResult ground = level.clip(new ClipContext(
				steppedOut.add(0.0, 0.25, 0.0),
				steppedOut.add(0.0, -5.0, 0.0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return ground.getType() != HitResult.Type.MISS ? ground.getLocation() : hit.getLocation();
	}

	private static void tick(MinecraftServer server) {
		State state = STATES.of(server);
		int now = server.getTickCount();
		state.ready.entrySet().removeIf(e -> e.getValue() <= now);
		if (state.anchors.isEmpty()) return;

		var it = state.anchors.entrySet().iterator();
		while (it.hasNext()) {
			Anchor anchor = it.next().getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(anchor.ownerId());
			if (owner == null || owner.level() != anchor.level() || !owner.isAlive() || invalidMatchState(anchor.ownerId())) {
				it.remove();
				continue;
			}
			if (now < anchor.triggerAt()) continue;

			it.remove();
			triggerAnchor(owner, anchor.level(), anchor.pos());
		}
	}

	private static boolean invalidMatchState(UUID id) {
		if (DuelRuntime.protectedInventory(id) && !DuelRuntime.fighting(id)) return true;
		var match = DuelRuntime.of(id);
		return match != null && match.inCountdown(id);
	}

	private static void triggerAnchor(ServerPlayer owner, ServerLevel level, Vec3 center) {
		double rSq = PULL_RADIUS * PULL_RADIUS;
		for (LivingEntity victim : AbilityTargeting.candidates(owner, level, center, PULL_RADIUS)) {
			if (victim.position().distanceToSqr(center) > rSq) continue;
			Vec3 delta = center.subtract(victim.position());
			Vec3 horiz = new Vec3(delta.x, 0.0, delta.z);
			double hDist = horiz.length();
			if (hDist > 0.08) {
				double strength = Math.min(1.45, 0.55 + hDist * 0.20);
				Vec3 pull = horiz.scale(strength / hDist).add(0.0, Math.max(0.22, delta.y * 0.22 + 0.16), 0.0);
				AbilityMovement.launch(victim, pull);
			}
			float dmg = CombatModifiers.skillDamage(owner, victim, DAMAGE);
			victim.hurtServer(level, owner.damageSources().indirectMagic(owner, owner), dmg);
		}
		VoidAnchorVisuals.pull(owner, center);
		level.playSound(null, center.x, center.y, center.z,
				SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.9f, 0.72f);
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
		var server = player.level().getServer();
		if (server == null) return;
		State state = STATES.existing(server);
		if (state != null) {
			state.anchors.remove(player.getUUID());
			state.ready.remove(player.getUUID());
		}
	}
}
