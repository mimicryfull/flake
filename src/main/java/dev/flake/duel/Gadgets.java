package dev.flake.duel;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Small utility active items with vanilla textures: «Димова шашка» and «Адреналін». Provisional numbers. */
public final class Gadgets {
	public static final int SMOKE_COOLDOWN = 300, ADRENALINE_COOLDOWN = 440;

	private static final Identifier SMOKE_ID = Identifier.fromNamespaceAndPath("flake", "smoke_bomb");
	private static final Identifier ADRENALINE_ID = Identifier.fromNamespaceAndPath("flake", "adrenaline");
	public static final Item SMOKE_BOMB = gadget(SMOKE_ID, SMOKE_COOLDOWN, Gadgets::smoke);
	public static final Item ADRENALINE = gadget(ADRENALINE_ID, ADRENALINE_COOLDOWN, Gadgets::adrenaline);

	private interface Effect { boolean apply(ServerPlayer player, ServerLevel level); }

	private Gadgets() {}

	private static Item gadget(Identifier id, int cooldown, Effect effect) {
		var key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, key, new dev.flake.shared.FlakeActiveItem(new Item.Properties().setId(key).stacksTo(1), id) {
			@Override
			protected boolean activateServer(ServerPlayer sp, net.minecraft.world.entity.Entity directTarget) {
				if (!(sp.level() instanceof ServerLevel sl) || DuelRuntime.blocksAbility(sp.getUUID())) return false;
				if (!effect.apply(sp, sl)) return false;
				sp.getCooldowns().addCooldown(id, cooldown);
				return true;
			}
		});
	}

	public static ItemStack smokeStack() { return new ItemStack(SMOKE_BOMB); }
	public static ItemStack adrenalineStack() { return new ItemStack(ADRENALINE); }

	/** Cloud around the caster: opponents inside are blinded briefly, the caster slips away faster. */
	private static boolean smoke(ServerPlayer p, ServerLevel level) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 60, 1.6, 0.8, 1.6, 0.02);
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, true, true));
		for (var other : level.players()) {
			if (DuelRuntime.opponents(p.getUUID(), other.getUUID()) && other.position().distanceToSqr(p.position()) <= 4.5 * 4.5)
				other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, true));
		}
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35f, 1.8f);
		return true;
	}

	private static boolean adrenaline(ServerPlayer p, ServerLevel level) {
		p.heal(3.5f);
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, true, true));
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 14, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 2.0f);
		return true;
	}

	public static void register() {}

	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(SMOKE_ID);
		player.getCooldowns().removeCooldown(ADRENALINE_ID);
	}
}
