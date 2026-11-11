package dev.flake.features.duel.recovery;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Write-ahead journal. Pending originals are never overwritten or silently discarded. */
public final class RecoveryJournal {
	private final Path directory;

	public RecoveryJournal(Path directory) { this.directory = directory.toAbsolutePath(); }
	public boolean exists(UUID owner) { return Files.exists(path(owner)); }

	public void create(UUID owner, CompoundTag data) throws IOException {
		validate(owner, data);
		Files.createDirectories(directory);
		Path temporary = Files.createTempFile(directory, "preparing-", ".tmp");
		try {
			NbtIo.writeCompressed(data, temporary);
			try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
			// Linking is exclusive, unlike rename-with-replace. Both paths are on the same FS.
			Files.createLink(path(owner), temporary);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public CompoundTag read(UUID owner) throws IOException {
		var data = NbtIo.readCompressed(path(owner), NbtAccounter.create(8L * 1024 * 1024));
		validate(owner, data);
		return data;
	}

	/** Call only after the restored player, including its receipt tag, is verified on disk. */
	public void complete(UUID owner, UUID token) throws IOException {
		var data = read(owner);
		if (!token.toString().equals(data.getString("token").orElseThrow())) throw new IOException("Recovery token changed");
		Path archive = directory.resolve("completed");
		Files.createDirectories(archive);
		Path destination = archive.resolve(owner + "-" + token + ".nbt");
		if (Files.exists(destination)) {
			// A crash after linking the archive but before removing the pending name.
			if (!Files.isSameFile(path(owner), destination)) throw new IOException("Conflicting recovery archive");
		} else {
			Files.createLink(destination, path(owner));
		}
		Files.delete(path(owner));
	}

	private Path path(UUID owner) { return directory.resolve(owner + ".nbt"); }

	private static void validate(UUID owner, CompoundTag data) throws IOException {
		try {
			if (data.getInt("version").orElseThrow() != 1) throw new IllegalArgumentException("Unsupported recovery version");
			if (!owner.toString().equals(data.getString("owner").orElseThrow())) throw new IllegalArgumentException("Wrong snapshot owner");
			UUID.fromString(data.getString("token").orElseThrow());
		} catch (RuntimeException exception) {
			throw new IOException("Invalid recovery journal for " + owner, exception);
		}
	}
}
