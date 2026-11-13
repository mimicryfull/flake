package dev.flake.features.huntermark;

import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HunterMarkRuntimeTest {
	@Test void specAndConstantsMatchDesign() {
		assertEquals(ActiveItem.HUNTER_MARK, HunterMarkDefinition.SPEC.id());
		assertEquals("flake.active.hunter_mark.short", HunterMarkDefinition.SPEC.compactTitleKey());
		assertEquals(20, HunterMarkRuntime.COOLDOWN_TICKS);
		assertEquals(280, HunterMarkRuntime.TARGET_COOLDOWN_TICKS);
		assertEquals(120, HunterMarkRuntime.MARK_TICKS);
		assertEquals(1.15f, HunterMarkRuntime.DAMAGE_MULTIPLIER, 1e-5f);
		assertEquals(ItemStack.EMPTY, HunterMarkRuntime.stack());
	}
}
