package dev.flake.features.axeofcumshot;

import dev.flake.combat.Cooldowns;
import dev.flake.combat.Stuns;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * «Сокира Кумшоту»: активний артефакт (ПКМ).
 * Кидає сокиру в напрямку погляду без автонаведення; влучання завдає шкоди та оглушує на 1.5с.
 */
public final class AxeOfCumshotRuntime {
	public static final int COOLDOWN_TICKS = 20;
	public static final int STUN_TICKS = 30;
	public static final float DAMAGE = 4.0f;
	public static final double RANGE = 18.0;
	public static final double SPEED_PER_TICK = 1.50;
	public static final int MAX_FLIGHT_TICKS = 12;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "axe_of_cumshot");
	public static final Identifier SOUND_ID = Identifier.fromNamespaceAndPath("flake", "axe_of_cumshot_activate");

	private static SoundEvent sound;

	private static final class ThrownAxe {
		final UUID ownerId;
		final ServerLevel level;
		Vec3 pos;
		final Vec3 dir;
		int ticksLeft;

		ThrownAxe(UUID ownerId, ServerLevel level, Vec3 pos, Vec3 dir, int ticksLeft) {
			this.ownerId = ownerId;
			this.level = level;
			this.pos = pos;
			this.dir = dir;
			this.ticksLeft = ticksLeft;
		}
	}

	private static final class State {
		final List<ThrownAxe> axes = new ArrayList<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);

	private AxeOfCumshotRuntime() {}

	public static Item ITEM;

	static void bind(Item item) { ITEM = item; }
	static void bindSound(SoundEvent event) { sound = event; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isAxe(ItemStack stack) { return ITEM != null && stack.is(ITEM); }

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AxeOfCumshotRuntime::tick);
	}

	public static boolean throwAxe(ServerPlayer player) {
		if (DuelRuntime.blocksAbility(player.getUUID()) || !(player.level() instanceof ServerLevel level)) return false;

		int cooldown = Cooldowns.ticks(player, COOLDOWN_TICKS);
		player.getCooldowns().addCooldown(ID, cooldown);

		Vec3 dir = player.getLookAngle().normalize();
		Vec3 start = player.getEyePosition().add(dir.scale(0.55)).add(0, -0.18, 0);
		Vec3 end = start.add(dir.scale(RANGE));

		STATES.of(level.getServer()).axes.add(new ThrownAxe(player.getUUID(), level, start, dir, MAX_FLIGHT_TICKS));
		AxeOfCumshotVisuals.launchAxe(player, start, end, (float) SPEED_PER_TICK, MAX_FLIGHT_TICKS);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
		return true;
	}

	private static void tick(MinecraftServer server) {
		var axes = STATES.of(server).axes;
		if (axes.isEmpty()) return;

		var it = axes.iterator();
		while (it.hasNext()) {
			ThrownAxe axe = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(axe.ownerId);
			if (owner == null || owner.level() != axe.level || !owner.isAlive()) {
				it.remove();
				continue;
			}

			Vec3 from = axe.pos;
			Vec3 to = from.add(axe.dir.scale(SPEED_PER_TICK));
			var blockHit = axe.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
			if (hitBlock) {
				to = blockHit.getLocation();
			}

			LivingEntity bestTarget = null;
			Vec3 bestHitPos = null;
			double bestDistSq = Double.MAX_VALUE;
			AABB sweep = new AABB(from, to).inflate(0.45);
			for (var entity : axe.level.getEntities(owner, sweep, e -> e instanceof LivingEntity l && validTarget(owner, l))) {
				LivingEntity living = (LivingEntity) entity;
				var hit = living.getBoundingBox().inflate(0.32).clip(from, to);
				if (hit.isPresent()) {
					double dSq = from.distanceToSqr(hit.get());
					if (dSq < bestDistSq) {
						bestDistSq = dSq;
						bestTarget = living;
						bestHitPos = hit.get();
					}
				}
			}

			if (bestTarget != null) {
				Stuns.apply(bestTarget, STUN_TICKS);
				bestTarget.hurtServer(axe.level, owner.damageSources().playerAttack(owner), DAMAGE);
				AxeOfCumshotVisuals.impact(owner, bestHitPos, bestTarget.getId());
				it.remove();
				continue;
			}

			if (hitBlock || --axe.ticksLeft <= 0) {
				if (hitBlock) {
					AxeOfCumshotVisuals.impact(owner, to, 0);
				}
				it.remove();
				continue;
			}

			axe.pos = to;
		}
	}

	private static boolean validTarget(ServerPlayer player, LivingEntity living) {
		if (!living.isAlive() || living == player) return false;
		if (DuelRuntime.fighting(player.getUUID())) {
			return living instanceof ServerPlayer other && DuelRuntime.opponents(player.getUUID(), other.getUUID());
		}
		return living instanceof Player || (living instanceof Mob mob && mob.entityTags().contains("flake_test_dummy"));
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
		if (player.level() instanceof ServerLevel level) {
			STATES.of(level.getServer()).axes.removeIf(axe -> axe.ownerId.equals(player.getUUID()));
		}
	}
}
