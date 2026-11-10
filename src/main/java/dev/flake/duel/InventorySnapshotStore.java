package dev.flake.duel;

import com.mojang.serialization.DynamicOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class InventorySnapshotStore {
	private static final int VERSION = 1;
	private static final int MAX_SLOTS = 128;
	private final Path directory;
	private final DynamicOps<Tag> ops;

	public InventorySnapshotStore(Path directory, DynamicOps<Tag> ops) {
		this.directory = directory.toAbsolutePath();
		this.ops = ops;
	}

	public void save(InventorySnapshot snapshot) throws IOException {
		if (snapshot.size() > MAX_SLOTS) throw new IOException("Inventory is too large");
		var data = new CompoundTag();
		data.putInt("version", VERSION);
		data.putString("owner", snapshot.owner().toString());
		data.putInt("selected", snapshot.selectedSlot());
		data.putInt("size", snapshot.size());
		var items = new ArrayList<ItemStack>(snapshot.size());
		for (int slot = 0; slot < snapshot.size(); slot++) items.add(snapshot.item(slot));
		Tag encoded = ItemStack.OPTIONAL_CODEC.listOf().encodeStart(ops, items).result()
				.orElseThrow(() -> new IOException("Could not encode inventory"));
		data.put("items", encoded);
		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory, "snapshot-", ".tmp");
		try {
			NbtIo.writeCompressed(data, temporary);
			Files.createLink(path(snapshot.owner()), temporary);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public InventorySnapshot load(UUID owner) throws IOException {
		CompoundTag data = NbtIo.readCompressed(path(owner), NbtAccounter.create(8L * 1024 * 1024));
		try {
			if (data.getInt("version").orElseThrow() != VERSION) throw new IllegalArgumentException("Unsupported version");
			if (!owner.toString().equals(data.getString("owner").orElseThrow())) throw new IllegalArgumentException("Wrong owner");
			int size = data.getInt("size").orElseThrow();
			if (size < 0 || size > MAX_SLOTS) throw new IllegalArgumentException("Invalid inventory size");
			int selected = data.getInt("selected").orElseThrow();
			Tag encoded = data.get("items");
			if (encoded == null) throw new IllegalArgumentException("Missing items");
			var items = ItemStack.OPTIONAL_CODEC.listOf().parse(ops, encoded).result()
					.orElseThrow(() -> new IllegalArgumentException("Invalid items"));
			if (items.size() != size) throw new IllegalArgumentException("Wrong inventory size");
			return new InventorySnapshot(owner, items, selected);
		} catch (RuntimeException exception) {
			throw new IOException("Invalid inventory snapshot for " + owner, exception);
		}
	}

	private Path path(UUID owner) {
		return directory.resolve(owner + ".nbt");
	}
}
