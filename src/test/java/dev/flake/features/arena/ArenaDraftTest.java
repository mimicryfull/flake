package dev.flake.features.arena;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaDraftTest {
	@Test
	void requiresAllPointsAndNormalizesCorners() {
		var draft = new ArenaDraft();
		assertThrows(IllegalStateException.class, draft::build);
		draft.setCorner("minecraft:overworld", new ArenaDraft.BlockPosition(10, 80, 10), true);
		draft.setCorner("minecraft:overworld", new ArenaDraft.BlockPosition(-10, 60, -10), false);
		var spawn = new Arena.Position(0, 64, 0, 0, 0);
		draft.setPosition("minecraft:overworld", spawn, ArenaDraft.Point.FIRST);
		draft.setPosition("minecraft:overworld", spawn, ArenaDraft.Point.SECOND);
		draft.setPosition("minecraft:overworld", spawn, ArenaDraft.Point.LOBBY);
		assertEquals(new Arena.Bounds(-10, 60, -10, 10, 80, 10), draft.build().bounds());
	}

	@Test
	void rejectsMixedDimensionsWithoutChangingDraft() {
		var draft = new ArenaDraft();
		draft.setCorner("minecraft:overworld", new ArenaDraft.BlockPosition(0, 0, 0), true);
		assertThrows(IllegalArgumentException.class, () -> draft.setCorner("minecraft:the_nether", new ArenaDraft.BlockPosition(1, 1, 1), false));
		assertThrows(IllegalStateException.class, draft::build);
	}
}
