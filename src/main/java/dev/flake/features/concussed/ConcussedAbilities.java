package dev.flake.features.concussed;

import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.features.loadout.Loadout;
import dev.flake.combat.CombatModifiers;
import dev.flake.combat.Cooldowns;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.LoadoutService;
import dev.flake.features.training.TrainingDummies;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Owns Concussed casts, their delayed hits, cooldowns and cancellation lifecycle. */
public final class ConcussedAbilities {
	private static final Map<MinecraftServer, ConcussedAbilities> INSTANCES = new HashMap<>();
	private final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
	private final Map<UUID, PendingShockwave> CHARGES = new HashMap<>();
	private final Map<UUID, Integer> QUAKE_COOLDOWNS = new HashMap<>();
	private final Map<UUID, Integer> QUAKE_TOTALS = new HashMap<>();
	private final Map<UUID, Integer> SHOCKWAVE_TOTALS = new HashMap<>();
	private final Map<UUID, ActiveQuake> QUAKES = new HashMap<>();
	private static boolean installed;
	private ConcussedAbilities() {}

	private static ConcussedAbilities of(MinecraftServer server) {
		return INSTANCES.computeIfAbsent(server, key -> new ConcussedAbilities());
	}

	public static int cooldownRemainingTicks(ServerPlayer player, int nowTick) {
		var playerId = player.getUUID();
		var state = of(player.level().getServer());
		Integer start = state == null ? null : state.COOLDOWNS.get(playerId);
		return start == null ? 0 : Math.max(0, Shockwave.COOLDOWN_TICKS - (nowTick - start));
	}

	public static int chargeElapsedTicks(ServerPlayer player, int nowTick) {
		var playerId = player.getUUID();
		var state = of(player.level().getServer());
		var pending = state == null ? null : state.CHARGES.get(playerId);
		return pending == null ? -1 : Math.clamp(nowTick - (pending.releaseTick() - Shockwave.CHARGE_TICKS), 0, Shockwave.CHARGE_TICKS);
	}

	/** Cancel at the class-change event, even if the player switches back within one tick. */
	public static void onClassChanged(ServerPlayer player) {
		var state = of(player.level().getServer());
		var pending = state.CHARGES.remove(player.getUUID());
		if (pending != null) dev.flake.render.vfx.CombatVisuals.cancel(player, pending.sourceId());
		state.QUAKES.remove(player.getUUID());
		// Switching classes must not reset either ability's recharge.
	}

	public static int quakeCooldownRemainingTicks(ServerPlayer player, int nowTick) {
		var playerId = player.getUUID();
		var state = of(player.level().getServer());
		Integer start = state == null ? null : state.QUAKE_COOLDOWNS.get(playerId);
		return start == null ? 0 : Math.max(0, ElectricQuake.COOLDOWN_TICKS - (nowTick - start));
	}

	public static int quakeActiveElapsedTicks(ServerPlayer player, int nowTick) {
		var playerId = player.getUUID();
		var state = of(player.level().getServer());
		var active = state == null ? null : state.QUAKES.get(playerId);
		return active == null ? -1 : Math.clamp(nowTick - active.startTick(), 0, ElectricQuake.PROPAGATION_TICKS);
	}

	public static int shockwaveCooldownTotalTicks(ServerPlayer player) {
		var playerId = player.getUUID();
		var state = of(player.level().getServer());
		return state == null ? Shockwave.COOLDOWN_TICKS : state.SHOCKWAVE_TOTALS.getOrDefault(playerId, Shockwave.COOLDOWN_TICKS);
	}

	public static int quakeCooldownTotalTicks(ServerPlayer player) {
		var playerId = player.getUUID();
		var state = of(player.level().getServer());
		return state == null ? ElectricQuake.COOLDOWN_TICKS : state.QUAKE_TOTALS.getOrDefault(playerId, ElectricQuake.COOLDOWN_TICKS);
	}

	public static void install() {
		if (installed) return;
		installed = true;
		dev.flake.combat.ability.ClassAbilityRuntimes.onClassChange(ConcussedAbilities::onClassChanged);
		ServerTickEvents.END_SERVER_TICK.register(server -> of(server).tick(server));
		ServerLifecycleEvents.SERVER_STOPPED.register(INSTANCES::remove);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			var state = INSTANCES.get(server);
			if (state == null) return;
			var id = handler.player.getUUID();
			if (state.CHARGES.remove(id) != null) dev.flake.render.vfx.CombatVisuals.cancel(handler.player);
			state.COOLDOWNS.remove(id);
			state.QUAKE_COOLDOWNS.remove(id);
			state.QUAKES.remove(id);
			state.SHOCKWAVE_TOTALS.remove(id);
			state.QUAKE_TOTALS.remove(id);
		});
	}

	/** Round boundaries clear both delayed effects and their cooldowns, unlike a class change. */
	public static void reset(ServerPlayer player) {
		onClassChanged(player);
		var state = of(player.level().getServer());
		var id = player.getUUID();
		state.COOLDOWNS.remove(id);
		state.QUAKE_COOLDOWNS.remove(id);
		state.SHOCKWAVE_TOTALS.remove(id);
		state.QUAKE_TOTALS.remove(id);
	}

	private void tick(MinecraftServer server) {
		var builds = LoadoutService.of(server);
		int now = server.getTickCount();
		tickActiveCasts(QUAKES, (id, active) -> {
			var player = server.getPlayerList().getPlayer(id);
			if (player == null || !player.isAlive() || player.isSpectator()
					|| DuelRuntime.blocksAbility(id)
					|| active.inDuel() && !DuelRuntime.fighting(id)
					|| player.level() != active.level()
					|| builds.get(id).fighterClass() != Loadout.FighterClass.CONCUSSED) return true;
			int step = now - active.startTick();
			if (step < 0) return false;
			if (step > ElectricQuake.PROPAGATION_TICKS) return true;
			stepElectricQuake(player, active, step);
			return step >= ElectricQuake.PROPAGATION_TICKS;
		});
		QUAKE_COOLDOWNS.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null
				|| ElectricQuake.COOLDOWN_TICKS - (now - entry.getValue()) <= 0);
		tickActiveCasts(CHARGES, (id, pending) -> {
			var player = server.getPlayerList().getPlayer(id);
			if (player == null) return true;
			if (!player.isAlive() || player.isSpectator() || player.getId() != pending.sourceId()
					|| DuelRuntime.blocksAbility(id)
					|| pending.inDuel() && !DuelRuntime.fighting(id)
					|| player.level() != pending.level()
					|| builds.get(id).fighterClass() != Loadout.FighterClass.CONCUSSED) {
				dev.flake.render.vfx.CombatVisuals.cancel(player, pending.sourceId());
				return true;
			}
			if (now < pending.releaseTick()) return false;
			detonate(player);
			return true;
		});
		COOLDOWNS.entrySet().removeIf(entry -> {
			var player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) return true;
			boolean hasHud = ServerPlayNetworking.canSend(player, dev.flake.features.hud.HudStatePayload.TYPE);
			int remaining = Shockwave.COOLDOWN_TICKS - (now - entry.getValue());
			if (remaining <= 0) {
				if (!hasHud) player.sendSystemMessage(Component.literal("Гравітаційний дисонанс готовий").withStyle(net.minecraft.ChatFormatting.AQUA), true);
				return true;
			}
			if (!hasHud && now % 4 == 0) {
				if (CHARGES.containsKey(entry.getKey())) {
					int elapsed = now - entry.getValue();
					int filled = Math.clamp(elapsed * 10 / Shockwave.CHARGE_TICKS, 0, 10);
					player.sendSystemMessage(Component.literal("Накопичення  " + "▰".repeat(filled) + "▱".repeat(10 - filled))
							.withStyle(net.minecraft.ChatFormatting.AQUA), true);
					return false;
				}
				int filled = Math.clamp((Shockwave.COOLDOWN_TICKS - remaining) * 10 / Shockwave.COOLDOWN_TICKS, 0, 10);
				String bar = "▰".repeat(filled) + "▱".repeat(10 - filled);
				String seconds = String.format(Locale.ROOT, "%.1f", remaining / 20.0);
				player.sendSystemMessage(Component.literal("Ударна хвиля  " + bar + "  " + seconds + " с").withStyle(net.minecraft.ChatFormatting.AQUA), true);
			}
			return false;
		});
	}

	public static int cast(ServerPlayer player, Ability ability) {
		return of(player.level().getServer()).castAbility(player, ability);
	}

	private int castAbility(ServerPlayer player, Ability ability) {
		return switch (ability) {
			case GRAVITATIONAL_DISSONANCE -> shockwave(player);
			case ELECTRIC_QUAKE -> quake(player);
			default -> 0;
		};
	}

	private int shockwave(ServerPlayer player) {
		var server = player.level().getServer();
		var id = player.getUUID();
		if (DuelRuntime.blocksAbility(id)) {
			failure(player, "Дочекайся початку бою або завершення повернення речей.");
			return 0;
		}
		if (LoadoutService.of(server).get(id).fighterClass() != Loadout.FighterClass.CONCUSSED) {
			failure(player, "Здібність доступна класу Контужений.");
			return 0;
		}
		if (!player.isAlive() || player.isSpectator()) {
			failure(player, "Здібність доступна лише живому гравцю.");
			return 0;
		}
		int now = server.getTickCount();
		if (CHARGES.containsKey(id)) return 0;
		Integer lastUse = COOLDOWNS.get(id);
		if (lastUse != null && now - lastUse < Shockwave.COOLDOWN_TICKS) return 0;
		int cooldown = Cooldowns.ticks(player, Shockwave.COOLDOWN_TICKS);
		COOLDOWNS.put(id, now - (Shockwave.COOLDOWN_TICKS - cooldown));
		SHOCKWAVE_TOTALS.put(id, cooldown);
		CHARGES.put(id, new PendingShockwave(now + Shockwave.CHARGE_TICKS, player.level(), player.getId(), DuelRuntime.fighting(id)));
		if (!ServerPlayNetworking.canSend(player, dev.flake.features.hud.HudStatePayload.TYPE)) {
			player.sendSystemMessage(Component.literal("Накопичення  ▱▱▱▱▱▱▱▱▱▱").withStyle(net.minecraft.ChatFormatting.AQUA), true);
		}
		dev.flake.features.concussed.ConcussedVisuals.charge(player);
		dev.flake.bootstrap.FlakeNetworking.sync(server);
		return 1;
	}

	private void detonate(ServerPlayer player) {
		var id = player.getUUID();
		var origin = player.position();
		for (UUID targetId : DuelRuntime.participants(id)) {
			if (id.equals(targetId)) continue;
			var target = player.level().getServer().getPlayerList().getPlayer(targetId);
			if (target == null || !target.isAlive() || target.level() != player.level()) continue;
			var offset = target.position().subtract(origin);
			Shockwave.impulse(offset.x, offset.y, offset.z).ifPresent(impulse -> {
				target.push(impulse.x(), impulse.y(), impulse.z());
				target.connection.send(new ClientboundSetEntityMotionPacket(target));
				dev.flake.features.concussed.ConcussedVisuals.impact(target, player);
			});
		}
		var box = new AABB(origin.x - Shockwave.RADIUS, origin.y - Shockwave.RADIUS, origin.z - Shockwave.RADIUS,
				origin.x + Shockwave.RADIUS, origin.y + Shockwave.RADIUS, origin.z + Shockwave.RADIUS);
		for (Mob mob : DuelRuntime.fighting(id) ? java.util.List.<Mob>of() : player.level().getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
			var offset = mob.position().subtract(origin);
			Shockwave.impulse(offset.x, offset.y, offset.z).ifPresent(impulse -> {
				if (mob.entityTags().contains(TrainingDummies.TAG) && mob.isNoAi()) TrainingDummies.prepare(mob);
				mob.push(impulse.x(), impulse.y(), impulse.z());
				mob.syncVelocity = true;
			});
		}
		dev.flake.features.concussed.ConcussedVisuals.play(player);
	}

	private record PendingShockwave(int releaseTick, ServerLevel level, int sourceId, boolean inDuel) {}
	private record ActiveQuake(int startTick, ServerLevel level, Vec3 origin, double dirX, double dirZ,
			boolean inDuel, Set<UUID> hitTargets) {}

	private int quake(ServerPlayer player) {
		var server = player.level().getServer();
		var id = player.getUUID();
		if (DuelRuntime.blocksAbility(id)) {
			failure(player, "Дочекайся початку бою або завершення повернення речей.");
			return 0;
		}
		if (LoadoutService.of(server).get(id).fighterClass() != Loadout.FighterClass.CONCUSSED) {
			failure(player, "Електро-землетрус доступний класу Контужений.");
			return 0;
		}
		if (!player.isAlive() || player.isSpectator()) {
			failure(player, "Здібність доступна лише живому гравцю.");
			return 0;
		}
		int now = server.getTickCount();
		if (QUAKES.containsKey(id)) return 0;
		Integer lastUse = QUAKE_COOLDOWNS.get(id);
		if (lastUse != null && now - lastUse < ElectricQuake.COOLDOWN_TICKS) return 0;
		var look = player.getLookAngle();
		double len = Math.hypot(look.x, look.z);
		double fx = len < 1.0e-5 ? -Math.sin(Math.toRadians(player.getYRot())) : look.x / len;
		double fz = len < 1.0e-5 ? Math.cos(Math.toRadians(player.getYRot())) : look.z / len;
		int cooldown = Cooldowns.ticks(player, ElectricQuake.COOLDOWN_TICKS);
		QUAKE_COOLDOWNS.put(id, now - (ElectricQuake.COOLDOWN_TICKS - cooldown));
		QUAKE_TOTALS.put(id, cooldown);
		var active = new ActiveQuake(now, player.level(), player.position(), fx, fz, DuelRuntime.fighting(id), new HashSet<>());
		QUAKES.put(id, active);
		dev.flake.features.concussed.ConcussedVisuals.playQuake(player, fx, fz);
		stepElectricQuake(player, active, 0);
		dev.flake.bootstrap.FlakeNetworking.sync(server);
		return 1;
	}

	private static void failure(ServerPlayer player, String message) {
		player.sendSystemMessage(Component.empty().append(Component.literal(message)).withStyle(net.minecraft.ChatFormatting.RED));
	}

	private void stepElectricQuake(ServerPlayer caster, ActiveQuake active, int step) {
		ServerLevel level = active.level();
		Vec3 origin = active.origin();
		double fx = active.dirX();
		double fz = active.dirZ();
		// The same nonlinear front is used by server hits and the existing VFX shader.
		double toDist = ElectricQuake.wavefrontDistance(step / 20.0f);
		double frontX = origin.x + fx * toDist;
		double frontZ = origin.z + fz * toDist;
		double frontY = snapToGround(level, frontX, origin.y, frontZ);
		if (step == 3 || step == 6 || step == 9) {
			level.playSound(null, frontX, frontY, frontZ,
					SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, step == 9 ? 0.34f : 0.17f, 1.8f - step * 0.035f);
			level.playSound(null, frontX, frontY, frontZ,
					SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, step == 9 ? 0.30f : 0.12f, 0.85f);
		}
		var casterId = caster.getUUID();
		double reachLimit = toDist + 0.65;
		for (UUID targetId : DuelRuntime.participants(casterId)) {
			if (QUAKES.get(casterId) != active) return;
			if (casterId.equals(targetId) || active.hitTargets().contains(targetId)) continue;
			var target = level.getServer().getPlayerList().getPlayer(targetId);
			if (target == null || !target.isAlive() || target.level() != level) continue;
			var offset = target.position().subtract(origin);
			ElectricQuake.evaluate(fx, fz, offset.x, offset.y, offset.z).ifPresent(hit -> {
				if (hit.forwardDistance() <= reachLimit) {
					active.hitTargets().add(targetId);
					applyQuakeHit(caster, target, hit, active);
				}
			});
		}
		if (QUAKES.get(casterId) != active) return;
		if (!active.inDuel() && !DuelRuntime.fighting(casterId)) {
			var box = new AABB(origin.x - ElectricQuake.RANGE - 2, origin.y - 3, origin.z - ElectricQuake.RANGE - 2,
					origin.x + ElectricQuake.RANGE + 2, origin.y + 3, origin.z + ElectricQuake.RANGE + 2);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
				if (QUAKES.get(casterId) != active) return;
				if (active.hitTargets().contains(mob.getUUID())) continue;
				var offset = mob.position().subtract(origin);
				ElectricQuake.evaluate(fx, fz, offset.x, offset.y, offset.z).ifPresent(hit -> {
					if (hit.forwardDistance() <= reachLimit) {
						active.hitTargets().add(mob.getUUID());
						if (mob.entityTags().contains(TrainingDummies.TAG) && mob.isNoAi()) TrainingDummies.prepare(mob);
						applyQuakeHit(caster, mob, hit, active);
					}
				});
			}
		}
	}

	private void applyQuakeHit(ServerPlayer caster, LivingEntity target, ElectricQuake.Hit hit, ActiveQuake active) {
		ServerLevel level = caster.level();
		target.hurtServer(level, caster.damageSources().playerAttack(caster), CombatModifiers.skillDamage(caster, target, hit.damage()));
		// A lethal hit may synchronously restore both players and cancel this cast.
		if (QUAKES.get(caster.getUUID()) != active) return;
		target.push(hit.impulseX(), hit.impulseY(), hit.impulseZ());
		if (target instanceof ServerPlayer targetPlayer) targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
		else target.syncVelocity = true;
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 18, 1, false, true, true));
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.42f, 1.65f);
	}

	private static double snapToGround(ServerLevel level, double x, double refY, double z) {
		BlockPos base = BlockPos.containing(x, refY, z);
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos pos = base.offset(0, dy, 0);
			if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
					&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return pos.getY();
		}
		return refY;
	}

	/** Damage callbacks may reset a match or replace a cast while this tick is executing. */
	static <T> void tickActiveCasts(Map<UUID, T> casts, java.util.function.BiPredicate<UUID, T> update) {
		for (var entry : Map.copyOf(casts).entrySet()) {
			var id = entry.getKey();
			var cast = entry.getValue();
			if (casts.get(id) != cast) continue;
			if (update.test(id, cast) && casts.get(id) == cast) casts.remove(id);
		}
	}
}
