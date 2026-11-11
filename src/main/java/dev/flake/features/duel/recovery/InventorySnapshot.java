package dev.flake.features.duel.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class InventorySnapshot {
	private final UUID owner;
	private final List<ItemStack> items;
	private final int selectedSlot;
	private boolean restored;

	InventorySnapshot(UUID owner, List<ItemStack> items, int selectedSlot) {
		this.owner = Objects.requireNonNull(owner);
		if (selectedSlot < 0 || selectedSlot > 8) throw new IllegalArgumentException("Invalid selected hotbar slot");
		this.items = items.stream().map(ItemStack::copy).toList();
		this.selectedSlot = selectedSlot;
	}

	public static InventorySnapshot capture(ServerPlayer player) {
		var inventory = player.getInventory();
		var contents = new ArrayList<ItemStack>(inventory.getContainerSize());
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) contents.add(inventory.getItem(slot));
		return new InventorySnapshot(player.getUUID(), contents, inventory.getSelectedSlot());
	}

	public UUID owner() {
		return owner;
	}

	public int size() {
		return items.size();
	}

	public int selectedSlot() {
		return selectedSlot;
	}

	public ItemStack item(int slot) {
		return items.get(slot).copy();
	}

	public void restoreTo(ServerPlayer player) {
		Objects.requireNonNull(player);
		if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) {
			throw new IllegalStateException("Close the container and empty the cursor before restoring inventory");
		}
		var inventory = player.getInventory();
		restore(new Target() {
			public UUID owner() { return player.getUUID(); }
			public int size() { return inventory.getContainerSize(); }
			public boolean isEmpty(int slot) { return inventory.getItem(slot).isEmpty(); }
			public void setItem(int slot, ItemStack item) { inventory.setItem(slot, item); }
			public void select(int slot) { inventory.setSelectedSlot(slot); }
		});
		inventory.setChanged();
		player.inventoryMenu.broadcastFullState();
	}

	void restore(Target target) {
		Objects.requireNonNull(target);
		if (restored) throw new IllegalStateException("Snapshot was already restored");
		if (!owner.equals(target.owner())) throw new IllegalArgumentException("Snapshot belongs to another player");
		if (items.size() != target.size()) throw new IllegalStateException("Inventory size changed");
		for (int slot = 0; slot < items.size(); slot++) {
			if (!target.isEmpty(slot)) throw new IllegalStateException("Inventory must be empty before restoration");
		}
		restored = true;
		for (int slot = 0; slot < items.size(); slot++) target.setItem(slot, items.get(slot).copy());
		target.select(selectedSlot);
	}

	interface Target {
		UUID owner();
		int size();
		boolean isEmpty(int slot);
		void setItem(int slot, ItemStack item);
		void select(int slot);
	}
}
