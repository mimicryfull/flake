package dev.flake.features.mirrortrap;

import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MirrorTrapRuntimeTest {
	@Test void specAndConstantsMatchDesign() {
		assertEquals(ActiveItem.MIRROR_TRAP, MirrorTrapDefinition.SPEC.id());
		assertEquals("flake.active.mirror_trap.short", MirrorTrapDefinition.SPEC.compactTitleKey());
		assertEquals(20, MirrorTrapRuntime.COOLDOWN_TICKS);
		assertEquals(300, MirrorTrapRuntime.TARGET_COOLDOWN_TICKS);
		assertEquals(80, MirrorTrapRuntime.BUFF_TICKS);
		assertEquals(ItemStack.EMPTY, MirrorTrapRuntime.stack());
	}
}
