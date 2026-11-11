package dev.flake.features.duel.recovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryJournalTest {
	@TempDir Path directory;
	private static CompoundTag data(UUID owner, UUID token) {
		var result = new CompoundTag();
		result.putInt("version", 1); result.putString("owner", owner.toString()); result.putString("token", token.toString());
		return result;
	}
	@Test void pendingOriginalCannotBeOverwritten() throws IOException {
		var owner = UUID.randomUUID(); var token = UUID.randomUUID(); var journal = new RecoveryJournal(directory);
		journal.create(owner, data(owner, token));
		assertThrows(IOException.class, () -> journal.create(owner, data(owner, UUID.randomUUID())));
		assertEquals(token.toString(), journal.read(owner).getString("token").orElseThrow());
	}

	@Test void acknowledgesOnlyMatchingTokenAndKeepsAnArchive() throws IOException {
		var owner = UUID.randomUUID(); var token = UUID.randomUUID(); var journal = new RecoveryJournal(directory);
		journal.create(owner, data(owner, token));
		assertThrows(IOException.class, () -> journal.complete(owner, UUID.randomUUID()));
		assertTrue(journal.exists(owner));
		journal.complete(owner, token);
		assertFalse(journal.exists(owner));
		assertTrue(Files.isRegularFile(directory.resolve("completed/" + owner + "-" + token + ".nbt")));
	}

	@Test void canFinishAfterCrashBetweenArchiveLinkAndPendingRemoval() throws IOException {
		var owner = UUID.randomUUID(); var token = UUID.randomUUID(); var journal = new RecoveryJournal(directory);
		journal.create(owner, data(owner, token));
		Files.createDirectory(directory.resolve("completed"));
		Files.createLink(directory.resolve("completed/" + owner + "-" + token + ".nbt"), directory.resolve(owner + ".nbt"));
		journal.complete(owner, token);
		assertFalse(journal.exists(owner));
	}

	@Test void badOwnerOrCorruptDataNeverDisappears() throws IOException {
		var owner = UUID.randomUUID(); var journal = new RecoveryJournal(directory);
		assertThrows(IOException.class, () -> journal.create(owner, data(UUID.randomUUID(), UUID.randomUUID())));
		Files.writeString(directory.resolve(owner + ".nbt"), "broken snapshot");
		assertThrows(IOException.class, () -> journal.read(owner));
		assertEquals("broken snapshot", Files.readString(directory.resolve(owner + ".nbt")));
	}
}
