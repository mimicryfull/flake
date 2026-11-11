package dev.flake.features.duel.kit;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuelEquipmentTest {
	@Test void temporaryMarkingPreservesCustomItemStateAndDoesNotMutateTheOriginal() {
		var original = new CompoundTag();
		original.putBoolean("flake_crit_ready", true);
		original.putString("custom_owner", "player");
		var match = UUID.randomUUID();
		var marked = DuelEquipment.temporaryData(original, match);
		assertTrue(marked.getBooleanOr("flake_crit_ready", false));
		assertEquals("player", marked.getStringOr("custom_owner", ""));
		assertFalse(original.contains("flake_duel_kit"));
		assertTrue(DuelEquipment.belongsTo(marked, match));
	}

	@Test void finishingOneMatchCannotSelectEquipmentFromAnotherMatch() {
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		var firstItem = DuelEquipment.temporaryData(null, first);
		var secondItem = DuelEquipment.temporaryData(null, second);
		assertTrue(DuelEquipment.belongsTo(firstItem, first));
		assertFalse(DuelEquipment.belongsTo(secondItem, first));
		assertFalse(DuelEquipment.belongsTo(firstItem, second));
	}

	@Test void aMatchIdAloneDoesNotMakePersonalEquipmentTemporary() {
		var match = UUID.randomUUID();
		var personal = new CompoundTag();
		personal.putString("flake_match", match.toString());
		assertFalse(DuelEquipment.belongsTo(personal, match));
	}
}
