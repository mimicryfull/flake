package dev.flake.features.mirrortrap;

import dev.flake.combat.Cooldowns;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.runtime.ServerState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** «Дзеркальна пастка»: активний щит на 4 с, що відбиває перший ворожий снаряд назад у стрільця. */
public final class MirrorTrapRuntime {
	public static final int TARGET_COOLDOWN_TICKS = 300;
	public static final int COOLDOWN_TICKS = 20;
	public static final int BUFF_TICKS = 80;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "mirror_trap");

	private static final class State {
		final Map<UUID, Integer> buffs = new HashMap<>();
		final Map<UUID, Integer> ready = new HashMap<>();
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);

	public static Item ITEM;

	private MirrorTrapRuntime() {}

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
				SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 0.85f, 1.45f);
		MirrorTrapVisuals.trap(player, true);
		return true;
	}

	private static boolean consume(ServerPlayer victim) {
		MinecraftServer server = victim.level().getServer();
		if (server == null) return false;
		Integer until = STATES.of(server).buffs.get(victim.getUUID());
		if (until == null || until <= server.getTickCount()) return false;
		STATES.of(server).buffs.remove(victim.getUUID());
		return true;
	}

	static boolean interceptProjectile(Projectile projectile, LivingEntity victim) {
		if (projectile.level().isClientSide() || !(victim instanceof ServerPlayer sp)) return false;
		Entity shooter = projectile.getOwner();
		if (shooter == sp || !consume(sp)) return false;

		Vec3 currentVel = projectile.getDeltaMovement();
		double speed = Math.max(0.85, currentVel.length());
		Vec3 dir;
		if (shooter != null && shooter.isAlive() && shooter.level() == projectile.level()) {
			Vec3 targetPos = shooter.position().add(0.0, shooter.getBbHeight() * 0.6, 0.0);
			Vec3 delta = targetPos.subtract(projectile.position());
			dir = delta.lengthSqr() > 1e-4 ? delta.normalize() : sp.getLookAngle().normalize();
		} else {
			dir = currentVel.lengthSqr() > 1e-4 ? currentVel.normalize().scale(-1.0) : sp.getLookAngle().normalize();
		}

		projectile.setDeltaMovement(dir.scale(speed));
		projectile.setPos(projectile.position().add(dir.scale(0.55)));
		projectile.setOwner(sp);
		projectile.syncVelocity = true;

		notifyReflect(sp, projectile.position(), dir);
		return true;
	}

	static boolean interceptCustomProjectile(ServerPlayer victim, ServerPlayer attacker, Vec3 hitPos) {
		if (victim == attacker || !consume(victim)) return false;
		Vec3 pos = hitPos != null ? hitPos : victim.position().add(0.0, 1.0, 0.0);
		Vec3 dir = attacker.getEyePosition().subtract(pos);
		Vec3 norm = dir.lengthSqr() > 1e-4 ? dir.normalize() : victim.getLookAngle().normalize();
		notifyReflect(victim, pos, norm);
		return true;
	}

	private static void notifyReflect(ServerPlayer victim, Vec3 pos, Vec3 dir) {
		victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.65f);
		MirrorTrapVisuals.reflect(victim, pos, dir);
		MirrorTrapVisuals.trap(victim, false);
	}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(ID);
		var server = player.level().getServer();
		if (server == null) return;
		State state = STATES.existing(server);
		if (state == null) return;
		boolean wasActive = state.buffs.remove(player.getUUID()) != null;
		state.ready.remove(player.getUUID());
		if (wasActive) MirrorTrapVisuals.trap(player, false);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			State state = STATES.of(server);
			for (var entry : Map.copyOf(state.buffs).entrySet()) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (entry.getValue() > now && player != null && player.isAlive() && !DuelRuntime.blocksAbility(entry.getKey())) continue;
				state.buffs.remove(entry.getKey());
				if (player != null) MirrorTrapVisuals.trap(player, false);
			}
			state.ready.entrySet().removeIf(e -> e.getValue() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
