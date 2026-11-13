package dev.flake.features.stormharpoon;

import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StormHarpoonRuntimeTest {
	@Test void specAndConstantsMatchDesign() {
		assertEquals(ActiveItem.STORM_HARPOON, StormHarpoonDefinition.SPEC.id());
		assertEquals("flake.active.storm_harpoon.short", StormHarpoonDefinition.SPEC.compactTitleKey());
		assertEquals(20, StormHarpoonRuntime.COOLDOWN_TICKS);
		assertEquals(200, StormHarpoonRuntime.TARGET_COOLDOWN_TICKS);
		assertEquals(14.0, StormHarpoonRuntime.RANGE, 1e-5);
		assertEquals(3.0f, StormHarpoonRuntime.DAMAGE, 1e-5f);
		assertEquals(ItemStack.EMPTY, StormHarpoonRuntime.stack());
	}
}
