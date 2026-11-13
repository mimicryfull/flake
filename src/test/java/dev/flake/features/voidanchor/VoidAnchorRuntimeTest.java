package dev.flake.features.voidanchor;

import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoidAnchorRuntimeTest {
	@Test void specAndConstantsMatchDesign() {
		assertEquals(ActiveItem.VOID_ANCHOR, VoidAnchorDefinition.SPEC.id());
		assertEquals("flake.active.void_anchor.short", VoidAnchorDefinition.SPEC.compactTitleKey());
		assertEquals(20, VoidAnchorRuntime.COOLDOWN_TICKS);
		assertEquals(240, VoidAnchorRuntime.TARGET_COOLDOWN_TICKS);
		assertEquals(30, VoidAnchorRuntime.DELAY_TICKS);
		assertEquals(5.0, VoidAnchorRuntime.PULL_RADIUS, 1e-5);
		assertEquals(1.5f, VoidAnchorRuntime.DAMAGE, 1e-5f);
		assertEquals(ItemStack.EMPTY, VoidAnchorRuntime.stack());
	}
}
