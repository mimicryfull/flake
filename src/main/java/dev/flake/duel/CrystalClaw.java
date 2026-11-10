package dev.flake.duel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * «Кристалевий кіготь»: активний предмет (ПКМ). Тимчасово підсилює шкоду класових
 * здібностей на 20%. Баф не змінює атрибути гравця — множник застосовується
 * безпосередньо в точках нанесення шкоди здібностями на сервері.
 */
public final class CrystalClaw {
	public static final int BUFF_TICKS = 100;
	public static final int COOLDOWN_TICKS = 300;
	public static final float SKILL_DAMAGE_MULTIPLIER = 1.20f;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "crystal_claw");
	public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);
	public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, KEY,
			new dev.flake.shared.FlakeActiveItem(new Item.Properties().setId(KEY).stacksTo(1), ID) {
				@Override
				protected boolean activateServer(ServerPlayer player, net.minecraft.world.entity.Entity directTarget) {
					return activate(player);
				}
			});

	private static final Map<UUID, Integer> BUFF_UNTIL = new HashMap<>();
	private static final Map<UUID, Integer> READY_AT = new HashMap<>();

	private CrystalClaw() {}

	public static ItemStack stack() { return new ItemStack(ITEM); }

	public static boolean active(ServerPlayer player) {
		Integer until = BUFF_UNTIL.get(player.getUUID());
		return until != null && until > player.level().getServer().getTickCount();
	}

	/** Remaining buff ticks for HUD status; 0 when inactive. */
	public static int remaining(UUID id, int nowTick) {
		Integer until = BUFF_UNTIL.get(id);
		return until == null ? 0 : Math.max(0, until - nowTick);
	}

	/** Server-side multiplier for damage dealt by class abilities while the buff is active. */
	public static float skillDamage(ServerPlayer player, float damage) {
		return active(player) ? damage * SKILL_DAMAGE_MULTIPLIER : damage;
	}

	public static boolean activate(ServerPlayer player) {
		var id = player.getUUID();
		if (DuelRuntime.blocksAbility(id) || active(player)) return false;
		int now = player.level().getServer().getTickCount();
		if (now < READY_AT.getOrDefault(id, 0)) return false;
		BUFF_UNTIL.put(id, now + BUFF_TICKS);
		READY_AT.put(id, now + COOLDOWN_TICKS);
		player.getCooldowns().addCooldown(ID, COOLDOWN_TICKS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.9f, 1.35f);
		ShockwaveVisuals.crystal(player, true);
		return true;
	}

	public static void reset(ServerPlayer player) {
		boolean wasActive = BUFF_UNTIL.remove(player.getUUID()) != null;
		READY_AT.remove(player.getUUID());
		player.getCooldowns().removeCooldown(ID);
		if (wasActive) ShockwaveVisuals.crystal(player, false);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			for (var entry : Map.copyOf(BUFF_UNTIL).entrySet()) {
				var player = server.getPlayerList().getPlayer(entry.getKey());
				if (entry.getValue() > now && player != null && player.isAlive() && !DuelRuntime.blocksAbility(entry.getKey())) continue;
				BUFF_UNTIL.remove(entry.getKey());
				if (player != null) ShockwaveVisuals.crystal(player, false);
			}
			READY_AT.entrySet().removeIf(e -> e.getValue() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
