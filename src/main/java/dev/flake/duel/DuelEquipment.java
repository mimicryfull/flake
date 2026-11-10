package dev.flake.duel;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class DuelEquipment {
	private DuelEquipment() {}
	public static ItemStack temporary(ItemStack item) {
		var data = new CompoundTag();
		data.putBoolean("flake_duel_kit", true);
		item.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		return item;
	}
	public static boolean isTemporary(ItemStack item) {
		var data = item.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean("flake_duel_kit").orElse(false);
	}
}
