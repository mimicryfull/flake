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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** «Куб темряви»: один самонавідний промінь по ворогу в полі зору. Усе визначає сервер. */
public final class DarkCube {
	public static final float DAMAGE = 2.0f;
	public static final int SLOW_AMPLIFIER = 1;
	public static final int SLOW_TICKS = 20;
	public static final int COOLDOWN_TICKS = 140;
	public static final double RANGE = 16.0;
	/** Ширший конус автонаведення (~32°), щоб промінь надійно знаходив ціль у динамічному PvP. */
	public static final double AIM_COS = 0.85;

	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "cube_of_darkness");
	public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);
	public static final SoundEvent SOUND = Registry.register(BuiltInRegistries.SOUND_EVENT, ID, SoundEvent.createVariableRangeEvent(ID));
	public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, KEY,
			new dev.flake.shared.FlakeActiveItem(new Item.Properties().setId(KEY).stacksTo(1), ID) {
				@Override
				protected boolean canConsumeOnClient(Player player) {
					return hasClientTarget(player);
				}

				@Override
				protected boolean activateServer(ServerPlayer player, net.minecraft.world.entity.Entity directTarget) {
					return fire(player);
				}
			});

	private static final Map<UUID, Integer> READY_AT = new HashMap<>();

	private DarkCube() {}

	public static ItemStack stack() { return new ItemStack(ITEM); }

	public static boolean fire(ServerPlayer player) {
		var id = player.getUUID();
		if (DuelRuntime.blocksAbility(id) || !(player.level() instanceof ServerLevel level)) return false;
		int now = level.getServer().getTickCount();
		if (now < READY_AT.getOrDefault(id, 0)) return false;
		var target = findTarget(player, level);
		if (target == null) return false;
		READY_AT.put(id, now + COOLDOWN_TICKS);
		player.getCooldowns().addCooldown(ID, COOLDOWN_TICKS);
		target.hurtServer(level, player.damageSources().indirectMagic(player, player), DAMAGE);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER, false, true, true));
		ShockwaveVisuals.bolt(player, target);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
		return true;
	}

	private static boolean hasClientTarget(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		var box = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(2.0);
		for (var e : player.level().getEntities(player, box, entity -> entity instanceof LivingEntity l && l.isAlive())) {
			Vec3 aim = e.position().add(0, e.getBbHeight() * 0.6, 0);
			Vec3 delta = aim.subtract(eye);
			double dist = delta.length();
			if (dist <= RANGE && dist >= 0.3 && delta.scale(1.0 / dist).dot(look) >= AIM_COS) return true;
		}
		return false;
	}

	private static LivingEntity findTarget(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestCos = AIM_COS;
		var box = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(2.0);
		boolean inDuel = DuelRuntime.fighting(player.getUUID());
		for (var e : level.getEntities(player, box, entity -> entity instanceof LivingEntity l && l.isAlive())) {
			if (!(e instanceof LivingEntity other)) continue;
			if (inDuel) {
				if (!(other instanceof ServerPlayer sp) || !DuelRuntime.opponents(player.getUUID(), sp.getUUID())) continue;
			} else {
				if (!(other instanceof ServerPlayer) && !(other instanceof Mob mob && mob.entityTags().contains("flake_test_dummy"))) continue;
			}
			Vec3 aim = other.position().add(0, other.getBbHeight() * 0.6, 0);
			Vec3 delta = aim.subtract(eye);
			double dist = delta.length();
			if (dist > RANGE || dist < 0.3) continue;
			double cos = delta.scale(1.0 / dist).dot(look);
			if (cos < bestCos) continue;
			var clip = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (clip.getType() != HitResult.Type.MISS) continue;
			best = other; bestCos = cos;
		}
		return best;
	}

	public static void reset(ServerPlayer player) { READY_AT.remove(player.getUUID()); player.getCooldowns().removeCooldown(ID); }

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			READY_AT.entrySet().removeIf(e -> e.getValue() <= now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READY_AT.remove(handler.player.getUUID()));
	}
}
