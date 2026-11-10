package dev.flake.duel;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventorySnapshotTest {
	@Test
	void keepsOwnerSelectionAndIndependentSlotList() {
		var owner = UUID.randomUUID();
		var items = new ArrayList<ItemStack>();
		items.add(ItemStack.EMPTY);
		var snapshot = new InventorySnapshot(owner, items, 2);
		items.clear();
		assertEquals(owner, snapshot.owner());
		assertEquals(2, snapshot.selectedSlot());
		assertEquals(1, snapshot.size());
		assertTrue(snapshot.item(0).isEmpty());
	}

	@Test
	void rejectsWrongOwnerAndOccupiedInventory() {
		var owner = UUID.randomUUID();
		var snapshot = new InventorySnapshot(owner, java.util.List.of(ItemStack.EMPTY), 0);
		var other = new FakeTarget(UUID.randomUUID(), true);
		assertThrows(IllegalArgumentException.class, () -> snapshot.restore(other));
		var occupied = new FakeTarget(owner, true);
		assertThrows(IllegalStateException.class, () -> snapshot.restore(occupied));
		assertFalse(occupied.isEmpty(0));
	}

	@Test
	void restoresOnlyOnce() {
		var owner = UUID.randomUUID();
		var snapshot = new InventorySnapshot(owner, java.util.List.of(ItemStack.EMPTY), 2);
		var target = new FakeTarget(owner, false);
		snapshot.restore(target);
		assertEquals(2, target.selected);
		assertThrows(IllegalStateException.class, () -> snapshot.restore(target));
	}

	private static class FakeTarget implements InventorySnapshot.Target {
		private final UUID owner;
		private boolean occupied;
		private int selected;
		FakeTarget(UUID owner, boolean occupied) { this.owner = owner; this.occupied = occupied; }
		public UUID owner() { return owner; }
		public int size() { return 1; }
		public boolean isEmpty(int slot) { return !occupied; }
		public void setItem(int slot, ItemStack value) { occupied = !value.isEmpty(); }
		public void select(int slot) { selected = slot; }
	}
}
