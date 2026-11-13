package dev.flake.features.phasecrystal;

import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseCrystalRuntimeTest {
	@Test void specAndConstantsMatchDesign() {
		assertEquals(ActiveItem.PHASE_CRYSTAL, PhaseCrystalDefinition.SPEC.id());
		assertEquals("flake.active.phase_crystal.short", PhaseCrystalDefinition.SPEC.compactTitleKey());
		assertEquals(20, PhaseCrystalRuntime.COOLDOWN_TICKS);
		assertEquals(400, PhaseCrystalRuntime.TARGET_COOLDOWN_TICKS);
		assertEquals(40, PhaseCrystalRuntime.BUFF_TICKS);
		assertEquals(3.0, PhaseCrystalRuntime.TELEPORT_DISTANCE, 1e-5);
		assertEquals(ItemStack.EMPTY, PhaseCrystalRuntime.stack());
	}
}
