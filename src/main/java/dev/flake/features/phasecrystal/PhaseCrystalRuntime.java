package dev.flake.features.phasecrystal;

import dev.flake.combat.Cooldowns;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** «Фазовий кристал»: 2 с у фазі без отримання/завдання шкоди, потім безпечний телепорт на 3 м вперед. */
public final class PhaseCrystalRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 400;
	public static final int COOLDOWN_TICKS = 20;
	public static final int BUFF_TICKS = 40;
	public static final double TELEPORT_DISTANCE = 3.0;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "phase_crystal");

	private static final class State {
		final Map<UUID, Integer> buffs = new HashMap<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);

	public static Item ITEM;

	private PhaseCrystalRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isItem(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	public static boolean active(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return false;
		Integer until = STATES.of(server).buffs.get(player.getUUID());
		return until != null && until > server.getTickCount();
	}

	public static int remaining(ServerPlayer player, int nowTick) {
		Integer until = STATES.of(player).buffs.get(player.getUUID());
		return until == null ? 0 : Math.max(0, until - nowTick);
	}

	public static boolean activate(ServerPlayer player) {
		UUID id = player.getUUID();
		if (DuelRuntime.blocksAbility(id)) return false;
		if (active(player)) {
			dev.flake.combat.CombatFeedback.alreadyActive(player, stack());
			return false;
		}

		MinecraftServer server = player.level().getServer();
		if (server == null) return false;
		int now = server.getTickCount();
		State state = STATES.of(server);
		int readyAt = state.ready.getOrDefault(id, 0);
		if (now < readyAt) {
			dev.flake.combat.CombatFeedback.onCooldown(player, stack(), readyAt - now);
			return false;
		}

		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		state.buffs.put(id, now + BUFF_TICKS);
		state.ready.put(id, now + cooldown);
		player.getCooldowns().addCooldown(ID, cooldown);

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.35f);
		PhaseCrystalVisuals.phaseOn(player);
		return true;
	}

	private static Vec3 resolveSafeBlinkPos(ServerPlayer player, ServerLevel level) {
		Vec3 start = player.position();
		Vec3 look = player.getLookAngle().normalize();
		double allowed = TELEPORT_DISTANCE;

		Vec3 footStart = start.add(0.0, 0.15, 0.0);
		var footHit = level.clip(new ClipContext(footStart, footStart.add(look.scale(TELEPORT_DISTANCE)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (footHit.getType() != HitResult.Type.MISS) {
			allowed = Math.min(allowed, Math.max(0.0, footStart.distanceTo(footHit.getLocation()) - 0.45));
		}

		Vec3 eyeStart = player.getEyePosition();
		var eyeHit = level.clip(new ClipContext(eyeStart, eyeStart.add(look.scale(TELEPORT_DISTANCE)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (eyeHit.getType() != HitResult.Type.MISS) {
			allowed = Math.min(allowed, Math.max(0.0, eyeStart.distanceTo(eyeHit.getLocation()) - 0.45));
		}

		EntityDimensions dims = player.getDimensions(player.getPose());
		for (double d = allowed; d >= 0.0; d -= 0.25) {
			Vec3 candidate = start.add(look.scale(d));
			AABB box = dims.makeBoundingBox(candidate);
			if (level.noCollision(player, box)) {
				return candidate;
			}
			Vec3 steppedUp = candidate.add(0.0, 0.55, 0.0);
			if (level.noCollision(player, dims.makeBoundingBox(steppedUp))) {
				return steppedUp;
			}
		}
		return start;
	}

	private static boolean canBlinkInState(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!player.isAlive()) return false;
		if (DuelRuntime.protectedInventory(id) && !DuelRuntime.fighting(id)) return false;
		var match = DuelRuntime.of(id);
		return match == null || !match.inCountdown(id);
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
		var server = player.level().getServer();
		if (server == null) return;
		State state = STATES.existing(server);
		if (state == null) return;
		boolean wasActive = state.buffs.remove(player.getUUID()) != null;
		state.ready.remove(player.getUUID());
		if (wasActive) PhaseCrystalVisuals.phaseOff(player, player.position());
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			State state = STATES.of(server);
			state.ready.entrySet().removeIf(e -> e.getValue() <= now);
			if (state.buffs.isEmpty()) return;

			for (var entry : Map.copyOf(state.buffs).entrySet()) {
				UUID id = entry.getKey();
				int until = entry.getValue();
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				boolean canStay = player != null && canBlinkInState(player);
				if (until > now && canStay) continue;

				state.buffs.remove(id);
				if (player == null) continue;

				if (until <= now && canStay && player.level() instanceof ServerLevel level) {
					Vec3 dest = resolveSafeBlinkPos(player, level);
					player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), player.getYRot(), player.getXRot(), false);
					player.fallDistance = 0.0f;
					level.playSound(null, dest.x, dest.y, dest.z,
							SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.35f);
					PhaseCrystalVisuals.phaseOff(player, dest);
				} else {
					PhaseCrystalVisuals.phaseOff(player, player.position());
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
