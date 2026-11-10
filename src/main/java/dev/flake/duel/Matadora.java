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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * «Матадора»: активний предмет (ПКМ). Тимчасово дає +10% шкоди та +20% швидкості,
 * шейдерний оверлей для власника і об'ємну фіолетову ауру з кубічними частинками.
 */
public final class Matadora {
	public static final int BUFF_TICKS = 100;
	public static final int COOLDOWN_TICKS = 300;
	public static final double DAMAGE_BONUS = 0.10;
	public static final double SPEED_BONUS = 0.20;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "matadora");
	public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);
	public static final SoundEvent SOUND = Registry.register(BuiltInRegistries.SOUND_EVENT,
			Identifier.fromNamespaceAndPath("flake", "matadora_activate"),
			SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("flake", "matadora_activate")));
	public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, KEY,
			new dev.flake.shared.FlakeActiveItem(new Item.Properties().setId(KEY).stacksTo(1), ID) {
				@Override
				protected boolean activateServer(ServerPlayer player, net.minecraft.world.entity.Entity directTarget) {
					return activate(player);
				}
			});

	private static final Identifier DAMAGE_MOD = Identifier.fromNamespaceAndPath("flake", "matadora_damage");
	private static final Identifier SPEED_MOD = Identifier.fromNamespaceAndPath("flake", "matadora_speed");
	private static final Map<UUID, Integer> BUFF_UNTIL = new HashMap<>();
	private static final Map<UUID, Integer> READY_AT = new HashMap<>();

	private Matadora() {}

	public static ItemStack stack() { return new ItemStack(ITEM); }

	public static boolean active(UUID id) { return BUFF_UNTIL.containsKey(id); }

	/** Remaining buff ticks for HUD status; 0 when inactive. */
	public static int remaining(UUID id, int nowTick) {
		Integer until = BUFF_UNTIL.get(id);
		return until == null ? 0 : Math.max(0, until - nowTick);
	}

	public static boolean activate(ServerPlayer player) {
		var id = player.getUUID();
		if (DuelRuntime.blocksAbility(id)) return false;
		int now = player.level().getServer().getTickCount();
		if (active(id) || now < READY_AT.getOrDefault(id, 0)) return false;
		BUFF_UNTIL.put(id, now + BUFF_TICKS);
		READY_AT.put(id, now + COOLDOWN_TICKS);
		player.getCooldowns().addCooldown(ID, COOLDOWN_TICKS);
		apply(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
		return true;
	}

	private static void apply(ServerPlayer player) {
		remove(player, false);
		var damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (damage != null) damage.addTransientModifier(new AttributeModifier(DAMAGE_MOD, DAMAGE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		if (speed != null) speed.addTransientModifier(new AttributeModifier(SPEED_MOD, SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		ShockwaveVisuals.mask(player, true);
	}

	private static void remove(ServerPlayer player, boolean notify) {
		var damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (damage != null) damage.removeModifier(DAMAGE_MOD);
		if (speed != null) speed.removeModifier(SPEED_MOD);
		if (notify) ShockwaveVisuals.mask(player, false);
	}

	public static void reset(ServerPlayer player) {
		BUFF_UNTIL.remove(player.getUUID());
		READY_AT.remove(player.getUUID());
		remove(player, true);
		player.getCooldowns().removeCooldown(ID);
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			for (var entry : Map.copyOf(BUFF_UNTIL).entrySet()) {
				var player = server.getPlayerList().getPlayer(entry.getKey());
				boolean over = entry.getValue() <= now || player == null || !player.isAlive() || DuelRuntime.blocksAbility(entry.getKey());
				if (!over) continue;
				BUFF_UNTIL.remove(entry.getKey());
				if (player != null) remove(player, true);
			}
			READY_AT.entrySet().removeIf(e -> e.getValue() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.player));
	}
}
