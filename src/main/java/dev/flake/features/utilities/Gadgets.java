package dev.flake.features.utilities;
import dev.flake.features.duel.DuelRuntime;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Small utility active items with vanilla textures: «Димова шашка» and «Адреналін». Provisional numbers. */
public final class Gadgets {
	public static final int SMOKE_COOLDOWN = 20, ADRENALINE_COOLDOWN = 20;

	private static final Identifier SMOKE_ID = Identifier.fromNamespaceAndPath("flake", "smoke_bomb");
	private static final Identifier ADRENALINE_ID = Identifier.fromNamespaceAndPath("flake", "adrenaline");
	public static Item SMOKE_BOMB, ADRENALINE;

	static void bind(Item smoke, Item adrenaline) { SMOKE_BOMB = smoke; ADRENALINE = adrenaline; }

	/** Server-side gadget effect; the item itself is registered centrally in ItemsSlice. */
	public interface Effect { boolean apply(ServerPlayer player, ServerLevel level); }

	private Gadgets() {}

	public static ItemStack smokeStack() { return new ItemStack(SMOKE_BOMB); }
	public static ItemStack adrenalineStack() { return new ItemStack(ADRENALINE); }

	/** Cloud around the caster: opponents inside are blinded briefly, the caster slips away faster. */
	public static boolean smoke(ServerPlayer p, ServerLevel level) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 60, 1.6, 0.8, 1.6, 0.02);
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, true, true));
		for (var other : level.players()) {
			if (DuelRuntime.opponents(p.getUUID(), other.getUUID()) && other.position().distanceToSqr(p.position()) <= 4.5 * 4.5)
				other.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, true));
		}
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35f, 1.8f);
		return true;
	}

	public static boolean adrenaline(ServerPlayer p, ServerLevel level) {
		p.heal(3.5f);
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, true, true));
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 14, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 2.0f);
		return true;
	}


	public static void reset(ServerPlayer player) {
		player.getCooldowns().removeCooldown(SMOKE_ID);
		player.getCooldowns().removeCooldown(ADRENALINE_ID);
	}
}
