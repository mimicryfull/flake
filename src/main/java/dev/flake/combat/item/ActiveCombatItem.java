package dev.flake.combat.item;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Shared base item for Flake RMB-activated items and weapons.
 * Centralizes block/entity right-click delegation, cooldown checks, and off-hand shield pass-through.
 */
public abstract class ActiveCombatItem extends Item {
	protected final Identifier cooldownId;

	protected ActiveCombatItem(Properties properties, Identifier cooldownId) {
		super(properties);
		this.cooldownId = cooldownId;
	}

	/** Return false on the client when no valid target is in sight so an off-hand shield can be raised. */
	protected boolean canConsumeOnClient(Player player) {
		return true;
	}

	/** Server-authoritative activation; returns true when the item effect fired. */
	protected abstract boolean activateServer(ServerPlayer player, Entity directTarget);

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) return InteractionResult.PASS;
		if (level.isClientSide()) {
			return hand == InteractionHand.MAIN_HAND && player.getOffhandItem().is(Items.SHIELD) && !canConsumeOnClient(player)
					? InteractionResult.PASS : InteractionResult.CONSUME;
		}
		return player instanceof ServerPlayer sp && activateServer(sp, null)
				? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		var player = context.getPlayer();
		return player == null ? InteractionResult.PASS : use(context.getLevel(), player, context.getHand());
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;
		if (player.level().isClientSide()) return InteractionResult.CONSUME;
		return player instanceof ServerPlayer sp && activateServer(sp, target)
				? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
	}
}
