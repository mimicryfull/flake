package dev.flake.ability;

import dev.flake.build.Loadout.FighterClass;
import dev.flake.ui.HudStatePayload;
import org.junit.jupiter.api.Test;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import static org.junit.jupiter.api.Assertions.*;

class AbilitySlotsTest {
	@Test void packetContainsOnlySlotAndCannotChooseClassOrAbility() {
		var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			AbilitySlotPayload.CODEC.encode(buf, new AbilitySlotPayload(2));
			assertEquals(1, buf.readableBytes());
			assertEquals(2, AbilitySlotPayload.CODEC.decode(buf).slot());
			assertEquals(0, buf.readableBytes());
		} finally { buf.release(); }
	}
	@Test void resolvesTwoSlotsFromCurrentClassOnly() {
		assertEquals(2, AbilitySlots.COUNT);
		assertEquals(AbilitySlots.Ability.GRAVITATIONAL_DISSONANCE, AbilitySlots.resolve(FighterClass.CONCUSSED, 1));
		assertEquals(AbilitySlots.Ability.ELECTRIC_QUAKE, AbilitySlots.resolve(FighterClass.CONCUSSED, 2));
		for (var cls : FighterClass.values()) {
			for (int slot = 1; slot <= 2; slot++) {
				assertTrue(AbilitySlots.resolve(cls, slot).implemented());
				assertEquals(AbilitySlots.resolve(cls, slot).title(), AbilitySlots.title(cls.name(), slot));
				assertEquals(HudStatePayload.SkillState.READY, HudStatePayload.skillState(cls.name(), slot, false, -1, 0));
			}
		}
	}
	@Test void rejectsForgedOrMissingSlotsAndClasses() {
		for (int slot : new int[] {Integer.MIN_VALUE, -1, 0, 3, Integer.MAX_VALUE}) {
			assertFalse(AbilitySlots.valid(slot));
			assertFalse(AbilitySlots.resolve(FighterClass.CONCUSSED, slot).implemented());
		}
		assertFalse(AbilitySlots.resolve("forged", 1).implemented());
		assertFalse(AbilitySlots.resolve((String) null, 1).implemented());
	}
	@Test void activePhasesUseRealServerTimingNotHudGuesses() {
		assertEquals(dev.flake.duel.Shockwave.CHARGE_TICKS, AbilitySlots.resolve("CONCUSSED", 1).activeTicks());
		assertEquals(dev.flake.duel.ElectricQuake.PROPAGATION_TICKS, AbilitySlots.resolve("CONCUSSED", 2).activeTicks());
		assertEquals(ClassSkills.MINE_LIFE_TICKS, AbilitySlots.resolve("DEMOLITIONIST", 1).activeTicks());
		assertEquals(ClassSkills.BULWARK_TICKS, AbilitySlots.resolve("BASTION", 2).activeTicks());
		assertEquals(ClassSkills.BLOODLUST_TICKS, AbilitySlots.resolve("VAMPIRE", 2).activeTicks());
		assertEquals(22, AbilitySlots.resolve("CONCUSSED", 1).activeTicks());
		assertEquals(10, AbilitySlots.resolve("CONCUSSED", 2).activeTicks());
	}

	@Test void mineConstantsMatchMechanics() {
		assertEquals(20, ClassSkills.MINE_ARM_TICKS);
		assertEquals(400, ClassSkills.MINE_LIFE_TICKS);
		assertEquals(1.7, ClassSkills.MINE_TRIGGER_RADIUS, 1.0e-9);
	}

	@Test void eachSlotHasItsOwnTimingAndHudState() {
		assertEquals(60, AbilitySlots.resolve("CONCUSSED", 1).cooldownTicks());
		assertEquals(80, AbilitySlots.resolve("CONCUSSED", 2).cooldownTicks());
		assertEquals(HudStatePayload.SkillState.COOLDOWN, HudStatePayload.skillState("CONCUSSED", 1, false, -1, 2));
		assertEquals(HudStatePayload.SkillState.READY, HudStatePayload.skillState("CONCUSSED", 2, false, -1, 0));
		assertEquals(HudStatePayload.SkillState.BLOCKED, HudStatePayload.skillState("CONCUSSED", 2, true, -1, 0));
	}
}
