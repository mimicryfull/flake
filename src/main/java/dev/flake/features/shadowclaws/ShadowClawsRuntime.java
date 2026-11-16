package dev.flake.features.shadowclaws;

import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.werewolf.WerewolfVisuals;
import dev.flake.runtime.ServerState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Tracks Shadow Claws melee Bleed DoT procs (35% chance, 1.0 HP every 15 ticks for 3s). */
public final class ShadowClawsRuntime {
	public static final float BLEED_CHANCE = 0.35f;
	public static final int BLEED_DURATION_TICKS = 60;
	public static final int BLEED_INTERVAL_TICKS = 15;
	public static final float BLEED_TICK_DAMAGE = 1.0f;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "shadow_claws");

	private static final class BleedEntry {
		final ServerLevel level;
		final UUID attackerId;
		int nextTick;
		int expiresAt;

		BleedEntry(ServerLevel level, UUID attackerId, int nextTick, int expiresAt) {
			this.level = level;
			this.attackerId = attackerId;
			this.nextTick = nextTick;
			this.expiresAt = expiresAt;
		}
	}

	private static final class State {
		final Map<UUID, BleedEntry> bleeds = new HashMap<>();
		boolean applyingBleed;
	}

	private static final ServerState<State> STATES = new ServerState<>(State::new);
	private static boolean registered;

	public static Item ITEM;

	private ShadowClawsRuntime() {}

	static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return ITEM == null ? ItemStack.EMPTY : new ItemStack(ITEM); }

	public static boolean isClaws(ItemStack stack) { return ITEM != null && !stack.isEmpty() && stack.is(ITEM); }

	static void register() {
		if (registered) return;
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(ShadowClawsRuntime::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.of(server).bleeds.remove(handler.player.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			MinecraftServer server = entity.level().getServer();
			if (server != null) STATES.of(server).bleeds.remove(entity.getUUID());
		});
	}

	public static void afterMeleeDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
		if (blocked || taken <= 0.0f || !(victim.level() instanceof ServerLevel level)) return;
		MinecraftServer server = level.getServer();
		if (server == null) return;
		State state = STATES.of(server);
		if (state.applyingBleed || !source.isDirect()) return;
		if (!(source.getEntity() instanceof ServerPlayer attacker) || !isClaws(attacker.getMainHandItem())) return;
		if (victim == attacker || !victim.isAlive()) return;
		if (ThreadLocalRandom.current().nextFloat() >= BLEED_CHANCE) return;

		int now = server.getTickCount();
		state.bleeds.put(victim.getUUID(), new BleedEntry(level, attacker.getUUID(), now + BLEED_INTERVAL_TICKS, now + BLEED_DURATION_TICKS));
		WerewolfVisuals.shadowBleed(attacker, victim, true);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
				SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.70f, 0.72f);
	}

	public static int bleedRemaining(ServerPlayer player, int now) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return 0;
		BleedEntry entry = STATES.of(server).bleeds.get(player.getUUID());
		return entry == null ? 0 : Math.max(0, entry.expiresAt - now);
	}

	private static void tick(MinecraftServer server) {
		State state = STATES.of(server);
		if (state.bleeds.isEmpty()) return;
		int now = server.getTickCount();

		for (var mapEntry : new ArrayList<>(state.bleeds.entrySet())) {
			UUID victimId = mapEntry.getKey();
			BleedEntry bleed = mapEntry.getValue();
			var entity = bleed.level.getEntity(victimId);
			if (!(entity instanceof LivingEntity victim) || !victim.isAlive() || victim.level() != bleed.level) {
				state.bleeds.remove(victimId);
				continue;
			}
			ServerPlayer attacker = server.getPlayerList().getPlayer(bleed.attackerId);
			if (victim instanceof ServerPlayer vp && DuelRuntime.protectedInventory(vp.getUUID())) {
				if (!DuelRuntime.fighting(vp.getUUID())
						|| attacker == null
						|| !DuelRuntime.opponents(attacker.getUUID(), vp.getUUID())) {
					state.bleeds.remove(victimId);
					continue;
				}
			}

			if (now >= bleed.nextTick) {
				bleed.nextTick += BLEED_INTERVAL_TICKS;
				state.applyingBleed = true;
				try {
					DamageSource dmgSrc = attacker != null
							? attacker.damageSources().indirectMagic(null, attacker)
							: bleed.level.damageSources().magic();
					victim.hurtServer(bleed.level, dmgSrc, BLEED_TICK_DAMAGE);
				} finally {
					state.applyingBleed = false;
				}
				ServerPlayer anchor = attacker != null ? attacker
						: (bleed.level.players().isEmpty() ? null : bleed.level.players().getFirst());
				WerewolfVisuals.shadowBleed(anchor, victim, false);
				bleed.level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
						SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.42f, 1.35f);
			}

			if (now >= bleed.expiresAt) {
				state.bleeds.remove(victimId);
			}
		}
	}

	public static void reset(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server != null) STATES.of(server).bleeds.remove(player.getUUID());
	}
}
