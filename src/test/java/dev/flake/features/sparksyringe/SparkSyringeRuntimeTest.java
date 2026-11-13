package dev.flake.features.sparksyringe;

import dev.flake.features.loadout.Loadout.ActiveItem;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SparkSyringeRuntimeTest {
	@Test void specAndConstantsMatchDesign() {
		assertEquals(ActiveItem.SPARK_SYRINGE, SparkSyringeDefinition.SPEC.id());
		assertEquals("flake.active.spark_syringe.short", SparkSyringeDefinition.SPEC.compactTitleKey());
		assertEquals(20, SparkSyringeRuntime.COOLDOWN_TICKS);
		assertEquals(360, SparkSyringeRuntime.TARGET_COOLDOWN_TICKS);
		assertEquals(60, SparkSyringeRuntime.BUFF_TICKS);
		assertEquals(ItemStack.EMPTY, SparkSyringeRuntime.stack());
	}
}
