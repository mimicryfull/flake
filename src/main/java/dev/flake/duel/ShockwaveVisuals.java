package dev.flake.duel;

import dev.flake.ExampleMod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class ShockwaveVisuals {
	private static final int DURATION = 80;
	/** Keeps the post pass alive for the whole Matadora buff plus its fade-out. */
	private static final int MASK_DURATION = Matadora.BUFF_TICKS + 12;
	/** Same window for the Crystal Claw frost buff. */
	private static final int CRYSTAL_DURATION = CrystalClaw.BUFF_TICKS + 12;
	/** Bulwark fortress state plus its soft fade-out. */
	private static final int BULWARK_DURATION = (int) Math.ceil(BastionAnimation.Bulwark.BUFF_SECONDS * 20) + 14;
	/** Shield Ram wind-up, trail, impact and suppression feedback. */
	private static final int RAM_DURATION = (int) Math.ceil(BastionAnimation.Ram.DURATION * 20) + 4;
	/** Screen slash window for the Void Paladin crit. */
	private static final int CRIT_DURATION = (int) Math.ceil(CritAnimation.DURATION * 20) + 2;
	private static final Identifier EFFECT = Identifier.fromNamespaceAndPath(ExampleMod.MOD_ID, "shockwave");
	private static final HashMap<UUID, Integer> active = new HashMap<>();
	private static final HashSet<UUID> disabled = new HashSet<>();

	private ShockwaveVisuals() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ShockwavePayload.TYPE, ShockwavePayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ShockwaveVisuals::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			removeEffects(handler.player);
			active.remove(handler.player.getUUID());
			disabled.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (var player : server.getPlayerList().getPlayers()) removeEffects(player);
			active.clear();
			disabled.clear();
		});
	}

	public static void charge(ServerPlayer source) {
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.45f, 0.7f);
		send(source, ShockwavePayload.Phase.CHARGE);
	}

	public static void cancel(ServerPlayer source) {
		cancel(source, source.getId());
	}

	public static void cancel(ServerPlayer source, int sourceId) {
		var payload = new ShockwavePayload(source.position(), sourceId, ShockwavePayload.Phase.CANCEL);
		for (var player : source.level().getServer().getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, ShockwavePayload.TYPE)) ServerPlayNetworking.send(player, payload);
		}
	}

	public static void play(ServerPlayer source) {
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.75f, 0.7f);
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.22f, 1.65f);
		send(source, ShockwavePayload.Phase.BURST, 0.0f);
	}

	public static void playQuake(ServerPlayer source, double dirX, double dirZ) {
		float yaw = (float) Math.atan2(dirZ, dirX);
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 0.32f, 0.58f);
		source.level().playSound(null, source.getX(), source.getY(), source.getZ(),
				SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.20f, 1.75f);
		send(source, ShockwavePayload.Phase.QUAKE, yaw);
	}

	/** Matadora state, mirrored to the owner (overlay + 3rd-person aura) and observers (3D cube flame aura). */
	public static void mask(ServerPlayer source, boolean on) {
		buff(source, on, ShockwavePayload.Phase.MASK_ON, ShockwavePayload.Phase.MASK_OFF, MASK_DURATION);
	}

	/** Crystal Claw state, mirrored to the owner (frost overlay + 3rd-person shards) and observers (orbiting ice shards). */
	public static void crystal(ServerPlayer source, boolean on) {
		buff(source, on, ShockwavePayload.Phase.CRYSTAL_ON, ShockwavePayload.Phase.CRYSTAL_OFF, CRYSTAL_DURATION);
	}

	/** Bulwark fortress state: armour shell, absorption layer, rooted ground lock and hit-reactive ripple. */
	public static void bulwark(ServerPlayer source, boolean on) {
		buff(source, on, ShockwavePayload.Phase.BULWARK_ON, ShockwavePayload.Phase.BULWARK_OFF, BULWARK_DURATION);
	}

	/** Aim stores a fixed, server-confirmed contact point (not the victim's moving position). */
	public static void shieldRam(ServerPlayer source, net.minecraft.world.entity.LivingEntity target, Vec3 direction, boolean shield) {
		var phase = shield ? ShockwavePayload.Phase.SHIELD_RAM_SHIELD : ShockwavePayload.Phase.SHIELD_RAM;
		float yaw = (float) Math.atan2(direction.z, direction.x);
		int targetId = target == null ? -1 : target.getId();
		var contact = target == null ? source.position().add(direction.scale(0.9)).add(0, 0.95, 0)
				: target.position().add(0, target.getBbHeight() * 0.52, 0).subtract(direction.scale(target.getBbWidth() * 0.35));
		var payload = new ShockwavePayload(source.position(), source.getId(), phase, yaw, targetId, contact);
		broadcast(source, payload, source.position(), RAM_DURATION);
	}

	/** A Bulwark owner just took a real hit; nearby clients ripple the defensive shell. */
	public static void bulwarkHit(ServerPlayer victim, Vec3 sourcePosition) {
		// Unknown/environmental damage has no invented incoming direction.
		var direction = sourcePosition == null ? Vec3.ZERO : sourcePosition.subtract(victim.position()).normalize();
		var payload = new ShockwavePayload(victim.position(), victim.getId(), ShockwavePayload.Phase.BULWARK_HIT,
				0, -1, direction);
		broadcast(victim, payload, victim.position(), BULWARK_DURATION);
	}

	public static void bloodlust(ServerPlayer source, boolean on) {
		if (on) {
			buff(source, true, ShockwavePayload.Phase.BLOODLUST_ON, ShockwavePayload.Phase.BLOODLUST_OFF,
					(int) Math.ceil((GhoulAnimation.BUFF_SECONDS + GhoulAnimation.EXIT) * 20) + 4);
		} else {
			// Also retire cached rigs on clients that left the activation radius before an early cancel.
			var payload = new ShockwavePayload(source.position(), source.getId(), ShockwavePayload.Phase.BLOODLUST_OFF);
			for (var player : source.level().getServer().getPlayerList().getPlayers()) {
				if (player.level() == source.level() && !disabled.contains(player.getUUID()) && ServerPlayNetworking.canSend(player, ShockwavePayload.TYPE))
					ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** A valid lunge has already launched; rejected damage must not produce a contact flash. */
	public static void bloodLunge(ServerPlayer source, net.minecraft.world.entity.LivingEntity target, Vec3 direction, boolean hit) {
		var contact = target.position().add(0, target.getBbHeight() * 0.58, 0)
				.subtract(direction.scale(target.getBbWidth() * 0.35));
		var payload = new ShockwavePayload(source.position(), source.getId(), ShockwavePayload.Phase.BLOOD_LUNGE,
				(float) Math.atan2(direction.z, direction.x), hit ? target.getId() : -1, contact);
		broadcast(source, payload, source.position(), (int) Math.ceil(GhoulAnimation.LUNGE_SECONDS * 20) + 4);
	}

	/** Only called after eligible Bloodlust healing increased actual health, never for Leech alone. */
	public static void bloodSiphon(ServerPlayer receiver, net.minecraft.world.entity.LivingEntity victim, float healed) {
		var extraction = victim.position().add(0, victim.getBbHeight() * 0.58, 0);
		var receiving = receiver.position().add(0, receiver.getBbHeight() * 0.58, 0);
		var outward = receiving.subtract(extraction);
		if (outward.lengthSqr() > 0.0001) extraction = extraction.add(outward.normalize().scale(victim.getBbWidth() * 0.5 + 0.035));
		var payload = new ShockwavePayload(extraction, receiver.getId(), ShockwavePayload.Phase.BLOOD_SIPHON,
				healed, victim.getId(), receiving);
		broadcast(receiver, payload, extraction, 18);
	}

	private static void broadcast(ServerPlayer anchor, ShockwavePayload payload, Vec3 origin, int duration) {
		var server = anchor.level().getServer();
		if (server == null) return;
		int now = server.getTickCount();
		for (var player : server.getPlayerList().getPlayers()) {
			if (player.level() != anchor.level() || player.distanceToSqr(origin) > 48 * 48 || disabled.contains(player.getUUID())) continue;
			if (!ServerPlayNetworking.canSend(player, ShockwavePayload.TYPE)) continue;
			ServerPlayNetworking.send(player, payload);
			extend(player, now + duration);
		}
	}

	private static void buff(ServerPlayer source, boolean on, ShockwavePayload.Phase onPhase,
			ShockwavePayload.Phase offPhase, int duration) {
		var server = source.level().getServer();
		int now = server.getTickCount();
		var phase = on ? onPhase : offPhase;
		for (var player : server.getPlayerList().getPlayers()) {
			if (player.level() != source.level() || player.distanceToSqr(source) > 48 * 48 || disabled.contains(player.getUUID())) continue;
			if (!ServerPlayNetworking.canSend(player, ShockwavePayload.TYPE)) continue;
			ServerPlayNetworking.send(player, new ShockwavePayload(source.position(), source.getId(), phase));
			if (on) extend(player, now + duration);
		}
	}

	/** Void Paladin crit: a short world-space crimson rupture at the hit point, visible to everyone nearby. */
	public static void crit(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target) {
		var server = attacker.level().getServer();
		if (server == null) return;
		int now = server.getTickCount();
		var origin = target.position().add(0, target.getBbHeight() * 0.62, 0);
		var direction = origin.subtract(attacker.getEyePosition());
		var aim = direction.lengthSqr() > 0.0001 ? direction.normalize() : attacker.getLookAngle();
		var payload = new ShockwavePayload(origin, attacker.getId(), ShockwavePayload.Phase.CRIT, 0.0f, target.getId(), aim);
		for (var player : server.getPlayerList().getPlayers()) {
			if (player.level() != attacker.level() || player.distanceToSqr(origin) > 48 * 48 || disabled.contains(player.getUUID())) continue;
			if (!ServerPlayNetworking.canSend(player, ShockwavePayload.TYPE)) continue;
			ServerPlayNetworking.send(player, payload);
			extend(player, now + CRIT_DURATION);
		}
	}

	/** Cube of Darkness: purely visual; the server already resolved the target, damage and cooldown. */
	public static void bolt(ServerPlayer source, net.minecraft.world.entity.LivingEntity target) {
		var server = source.level().getServer();
		int now = server.getTickCount();
		var look = source.getLookAngle();
		var right = look.cross(new net.minecraft.world.phys.Vec3(0, 1, 0)).normalize();
		var muzzle = source.getEyePosition().add(look.scale(0.55)).add(right.scale(0.28)).add(0, -0.28, 0);
		var aim = target.position().add(0, target.getBbHeight() * 0.6, 0);
		for (var player : server.getPlayerList().getPlayers()) {
			if (player.level() != source.level() || player.distanceToSqr(muzzle) > 48 * 48 || disabled.contains(player.getUUID())) continue;
			if (!ServerPlayNetworking.canSend(player, ShockwavePayload.TYPE)) continue;
			ServerPlayNetworking.send(player, new ShockwavePayload(muzzle, source.getId(), ShockwavePayload.Phase.BOLT, 0.0f, target.getId(), aim));
			extend(player, now + DURATION);
		}
	}

	/** Called only after the server actually applied a shockwave impulse to this victim. */
	public static void impact(ServerPlayer victim, ServerPlayer source) {
		if (victim == source || disabled.contains(victim.getUUID())
				|| !ServerPlayNetworking.canSend(victim, ShockwavePayload.TYPE)) return;
		ServerPlayNetworking.send(victim, new ShockwavePayload(source.position().add(0, 1, 0),
				source.getId(), ShockwavePayload.Phase.IMPACT));
		extend(victim, victim.level().getServer().getTickCount() + DURATION);
	}

	private static void send(ServerPlayer source, ShockwavePayload.Phase phase) {
		send(source, phase, 0.0f);
	}

	private static void send(ServerPlayer source, ShockwavePayload.Phase phase, float yaw) {
		var server = source.level().getServer();
		int now = server.getTickCount();
		var origin = source.position();
		var waveOrigin = phase == ShockwavePayload.Phase.QUAKE ? origin : origin.add(0, 1, 0);
		for (var player : server.getPlayerList().getPlayers()) {
			if (player.level() != source.level() || player.distanceToSqr(origin) > 48 * 48 || disabled.contains(player.getUUID())) continue;
			if (!ServerPlayNetworking.canSend(player, ShockwavePayload.TYPE)) continue;
			ServerPlayNetworking.send(player, new ShockwavePayload(waveOrigin, source.getId(), phase, yaw));
			extend(player, now + DURATION);
		}
	}

	private static void extend(ServerPlayer player, int untilTick) {
		player.addPostEffect(EFFECT);
		active.merge(player.getUUID(), untilTick, Math::max);
	}

	public static boolean isEnabled(UUID playerId) {
		return !disabled.contains(playerId);
	}

	public static boolean toggle(ServerPlayer player) {
		if (disabled.remove(player.getUUID())) return true;
		disabled.add(player.getUUID());
		if (ServerPlayNetworking.canSend(player, ShockwavePayload.TYPE)) {
			ServerPlayNetworking.send(player, new ShockwavePayload(player.position(), player.getId(), ShockwavePayload.Phase.CLEAR));
		}
		if (active.remove(player.getUUID()) != null) removeEffects(player);
		return false;
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		active.entrySet().removeIf(entry -> {
			var player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) return true;
			if (now >= entry.getValue()) {
				removeEffects(player);
				return true;
			}
			return false;
		});
	}

	private static void removeEffects(ServerPlayer player) {
		player.removePostEffect(EFFECT);
	}
}
