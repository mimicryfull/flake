package dev.flake.combat.ability;

import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.features.loadout.Loadout.FighterClass;
import dev.flake.combat.Stuns;
import dev.flake.combat.CombatFeedback;
import dev.flake.combat.Cooldowns;
import dev.flake.features.duel.DuelRuntime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Explicit runtime composition and shared slot cooldowns. Features register factories at bootstrap;
 * this combat code never imports concrete class features. All player state belongs to a server instance.
 */
public final class ClassAbilityRuntimes {
	private static final Map<FighterClass, Function<MinecraftServer, ? extends ClassAbilityRuntime>> FACTORIES = new EnumMap<>(FighterClass.class);
	private static final Map<MinecraftServer, ClassAbilityRuntimes> SERVERS = new LinkedHashMap<>();
	private static boolean hooksRegistered;
	private static final List<java.util.function.Consumer<ServerPlayer>> CLASS_CHANGES = new ArrayList<>();
	public static void onClassChange(java.util.function.Consumer<ServerPlayer> listener) { CLASS_CHANGES.add(listener); }
	public static void classChanged(ServerPlayer player) { for (var listener : CLASS_CHANGES) listener.accept(player); }

	private final MinecraftServer server;
	private final List<ClassAbilityRuntime> runtimes = new ArrayList<>();
	private final Map<Ability, ClassAbilityRuntime> byAbility = new EnumMap<>(Ability.class);
	private final Map<UUID, SlotCooldowns> cooldowns = new HashMap<>();
	private final ConfirmedDamageHooks damageHooks = new ConfirmedDamageHooks(this);

	private record SlotCooldowns(int[] readyAt, int[] totals) {
		private SlotCooldowns() { this(new int[AbilitySlots.COUNT], new int[AbilitySlots.COUNT]); }
	}

	private ClassAbilityRuntimes(MinecraftServer server) {
		this.server = server;
		for (var factory : FACTORIES.values()) {
			var runtime = factory.apply(server);
			for (var ability : runtime.abilities()) {
				if (byAbility.putIfAbsent(ability, runtime) != null)
					throw new IllegalStateException("Duplicate class ability runtime: " + ability);
			}
			runtimes.add(runtime);
		}
	}

	/** Call from the composition root before any server runtime is created. No reflective discovery. */
	public static void registerRuntime(FighterClass fighterClass, Function<MinecraftServer, ? extends ClassAbilityRuntime> factory) {
		if (!SERVERS.isEmpty()) throw new IllegalStateException("Register class runtimes before server startup");
		if (FACTORIES.putIfAbsent(fighterClass, factory) != null)
			throw new IllegalStateException("Duplicate fighter class runtime: " + fighterClass);
	}

	public static ClassAbilityRuntimes of(MinecraftServer server) {
		return SERVERS.computeIfAbsent(server, ClassAbilityRuntimes::new);
	}

	/** Lookup without allocating state, suitable for reset and read-only HUD queries. */
	public static ClassAbilityRuntimes find(MinecraftServer server) { return SERVERS.get(server); }

	/**
	 * Compatibility bridge for old UUID-only HUD APIs. New callers should use of(server) instead:
	 * a UUID alone cannot disambiguate the same account on two servers in the same JVM.
	 */
	public static ClassAbilityRuntimes find(UUID player) {
		for (var state : SERVERS.values())
			if (state.cooldowns.containsKey(player) || state.server.getPlayerList().getPlayer(player) != null) return state;
		return null;
	}

	public static void registerHooks() {
		if (hooksRegistered) return;
		hooksRegistered = true;
		Stuns.onInterrupt(ClassAbilityRuntimes::cancelPlayerBuffs);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			var state = find(server);
			if (state != null) for (var runtime : state.runtimes) runtime.tick(server.getTickCount());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) ->
				of(victim.level().getServer()).damageHooks.afterDamage(victim, source, taken, blocked));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> resetPlayer(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			var state = SERVERS.remove(server);
			if (state != null) state.clear();
		});
	}

	public static void resetPlayer(ServerPlayer player) {
		var state = find(player.level().getServer());
		if (state != null) state.reset(player);
	}

	public static void cancelPlayerBuffs(ServerPlayer player) {
		var state = find(player.level().getServer());
		if (state != null) state.cancelBuffs(player);
	}

	/** A failed target lookup does not spend a cooldown; cooldown scaling is snapshotted at cast. */
	public int cast(ServerPlayer player, Ability ability, int slot) {
		if (!AbilitySlots.valid(slot)) return 0;
		int now = server.getTickCount();
		int remaining = remaining(player.getUUID(), slot, now);
		if (DuelRuntime.blocksAbility(player.getUUID())) {
			CombatFeedback.blocked(player);
			return 0;
		}
		if (remaining > 0) {
			CombatFeedback.onCooldown(player, remaining);
			return 0;
		}
		if (!(player.level() instanceof ServerLevel)) {
			CombatFeedback.unavailable(player);
			return 0;
		}
		var runtime = byAbility.get(ability);
		if (runtime == null || !runtime.cast(player, ability, now)) {
			CombatFeedback.noTarget(player);
			return 0;
		}
		int cooldown = Cooldowns.ticks(player, ability.cooldownTicks());
		var slots = cooldowns.computeIfAbsent(player.getUUID(), k -> new SlotCooldowns());
		slots.readyAt[slot - 1] = now + cooldown;
		slots.totals[slot - 1] = cooldown;
		return 1;
	}

	public int remaining(UUID player, int slot, int now) {
		var slots = cooldowns.get(player);
		return slots == null || !AbilitySlots.valid(slot) ? 0 : Math.max(0, slots.readyAt[slot - 1] - now);
	}

	public int cooldownTotal(UUID player, int slot) {
		var slots = cooldowns.get(player);
		return slots == null || !AbilitySlots.valid(slot) ? 0 : Math.max(0, slots.totals[slot - 1]);
	}

	public int activeElapsed(UUID player, int slot, int now) {
		for (var runtime : runtimes) {
			int elapsed = runtime.activeElapsed(player, slot, now);
			if (elapsed >= 0) return elapsed;
		}
		return -1;
	}

	public int buffRemaining(UUID player, Ability ability, int now) {
		var runtime = byAbility.get(ability);
		return runtime == null ? 0 : runtime.buffRemaining(player, ability, now);
	}

	public void cancelBuffs(ServerPlayer player) {
		for (var runtime : runtimes) runtime.cancelBuffs(player, server.getTickCount());
	}

	public void reset(ServerPlayer player) {
		cooldowns.remove(player.getUUID());
		for (var runtime : runtimes) runtime.reset(player);
	}

	private void clear() {
		for (var runtime : runtimes) runtime.clear();
		cooldowns.clear();
		damageHooks.clear();
	}

	List<ClassAbilityRuntime> runtimes() { return runtimes; }
}
