package dev.flake.features.duel.kit;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class DuelEquipment {
	private DuelEquipment() {}
	public static ItemStack temporary(ItemStack item) {
		var current = item.get(DataComponents.CUSTOM_DATA);
		var data = temporaryData(current == null ? null : current.copyTag(), null);
		item.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		return item;
	}
	public static ItemStack temporary(ItemStack item, java.util.UUID matchId) {
		var current = item.get(DataComponents.CUSTOM_DATA);
		var data = temporaryData(current == null ? null : current.copyTag(), matchId);
		item.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		return item;
	}
	public static boolean belongsTo(ItemStack item, java.util.UUID matchId) {
		var data = item.get(DataComponents.CUSTOM_DATA);
		return data != null && belongsTo(data.copyTag(), matchId);
	}
	public static boolean isTemporary(ItemStack item) {
		var data = item.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean("flake_duel_kit").orElse(false);
	}

	static CompoundTag temporaryData(CompoundTag existing, java.util.UUID matchId) {
		var data = existing == null ? new CompoundTag() : existing.copy();
		data.putBoolean("flake_duel_kit", true);
		if (matchId != null) data.putString("flake_match", matchId.toString());
		return data;
	}

	static boolean belongsTo(CompoundTag data, java.util.UUID matchId) {
		return data.getBooleanOr("flake_duel_kit", false)
				&& matchId.toString().equals(data.getStringOr("flake_match", ""));
	}
}
