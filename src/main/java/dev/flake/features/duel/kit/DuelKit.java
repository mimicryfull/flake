package dev.flake.features.duel.kit;

import dev.flake.features.loadout.Loadout;
import dev.flake.combat.CombatEquipment;
import dev.flake.combat.PvpVitals;
import dev.flake.features.armor.ArmorItems;
import dev.flake.content.ContentCatalog;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Mutates a player only after the match has durably saved every original player state. */
public final class DuelKit {
	private DuelKit() {}

	public static void apply(ServerPlayer player, Loadout build, UUID matchId) {
		player.stopUsingItem();
		player.closeContainer();
		player.getInventory().clearContent();
		player.removeAllEffects();
		PvpVitals.apply(player);
		player.setGameMode(GameType.ADVENTURE);
		var food = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess());
		new net.minecraft.world.food.FoodData().addAdditionalSaveData(food);
		player.getFoodData().readAdditionalSaveData(net.minecraft.world.level.storage.TagValueInput.create(
				net.minecraft.util.ProblemReporter.DISCARDING, player.registryAccess(), food.buildResult()));
		player.getFoodData().setFoodLevel(14);
		player.getFoodData().setSaturation(0f);
		player.experienceLevel = 0;
		player.totalExperience = 0;
		player.experienceProgress = 0;
		player.setAbsorptionAmount(0);
		player.setRemainingFireTicks(0);
		player.setTicksFrozen(0);
		player.setAirSupply(player.getMaxAirSupply());
		player.setItemSlot(EquipmentSlot.HEAD, ArmorItems.create(build.helmet(), Loadout.ArmorSlot.HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, ArmorItems.create(build.chestplate(), Loadout.ArmorSlot.CHESTPLATE));
		player.setItemSlot(EquipmentSlot.LEGS, ArmorItems.create(build.leggings(), Loadout.ArmorSlot.LEGGINGS));
		player.setItemSlot(EquipmentSlot.FEET, ArmorItems.create(build.boots(), Loadout.ArmorSlot.BOOTS));

		ContentCatalog.resetPlayer(player);
		PvpVitals.apply(player);
		CombatEquipment.apply(player, build);
		int slot = 0;
		var weapon = ContentCatalog.weapon(build.weapon());
		player.getInventory().setItem(slot++, weapon.createStack());
		for (var item : List.of(build.activeItem1(), build.activeItem2())) {
			var active = ContentCatalog.active(item);
			if (active == null || item == Loadout.ActiveItem.NONE) continue;
			if (active.offhand()) player.setItemSlot(EquipmentSlot.OFFHAND, active.createStack());
			else player.getInventory().setItem(slot++, active.createStack());
		}
		var passive = ContentCatalog.passive(build.passiveItem());
		if (passive != null) passive.handler().onApplyKit(player);
		if (weapon.arrowCount() > 0) player.getInventory().setItem(slot, new ItemStack(Items.ARROW, weapon.arrowCount()));
		for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
			var item = player.getInventory().getItem(index);
			if (!item.isEmpty()) DuelEquipment.temporary(item, matchId);
		}
		player.getInventory().setSelectedSlot(0);
		player.setHealth(player.getMaxHealth());
		player.inventoryMenu.broadcastFullState();
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(0, 0, 0));
	}
}
