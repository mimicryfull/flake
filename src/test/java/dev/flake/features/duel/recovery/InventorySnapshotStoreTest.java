package dev.flake.features.duel.recovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class InventorySnapshotStoreTest {
	@TempDir Path directory;

	@Test
	void savesAndLoadsWithoutOverwritingExistingSnapshot() throws IOException {
		var owner = UUID.randomUUID();
		var store = new InventorySnapshotStore(directory, NbtOps.INSTANCE);
		var snapshot = new InventorySnapshot(owner, List.of(), 2);
		store.save(snapshot);
		assertEquals(owner, store.load(owner).owner());
		assertEquals(2, store.load(owner).selectedSlot());
		assertEquals(0, store.load(owner).size());
		assertThrows(IOException.class, () -> store.save(snapshot));
		assertEquals(2, store.load(owner).selectedSlot());
	}

	@Test
	void corruptDataIsNotSilentlyAcceptedOrRemoved() throws IOException {
		var owner = UUID.randomUUID();
		var file = directory.resolve(owner + ".nbt");
		Files.writeString(file, "not nbt");
		var store = new InventorySnapshotStore(directory, NbtOps.INSTANCE);
		assertThrows(IOException.class, () -> store.load(owner));
		assertEquals("not nbt", Files.readString(file));
	}
}
