package dev.flake.ui;

import dev.flake.ui.HudStatePayload.Fighter;
import dev.flake.ui.HudStatePayload.SkillState;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudStatePayloadTest {
	@Test
	void formatsRoundTimerAndShortSeconds() {
		assertEquals("00:00", HudStatePayload.formatRoundTimer(0f));
		assertEquals("00:09", HudStatePayload.formatRoundTimer(9.8f));
		assertEquals("01:25", HudStatePayload.formatRoundTimer(85.2f));
		assertEquals("10:00", HudStatePayload.formatRoundTimer(600f));
		assertEquals("2.4 с", HudStatePayload.formatShortSeconds(2.41f));
		assertEquals("0.0 с", HudStatePayload.formatShortSeconds(-1.0f));
	}

	@Test
	void evaluatesSkillStatesCorrectly() {
		assertEquals(SkillState.READY, HudStatePayload.skillState("BASTION", false, -1, 0f));
		assertEquals(SkillState.BLOCKED, HudStatePayload.skillState("CONCUSSED", true, 5, 1.5f));
		assertEquals(SkillState.CHARGING, HudStatePayload.skillState("CONCUSSED", false, 0, 3.0f));
		assertEquals(SkillState.CHARGING, HudStatePayload.skillState("CONCUSSED", false, 15, 2.2f));
		assertEquals(SkillState.COOLDOWN, HudStatePayload.skillState("CONCUSSED", false, -1, 1.2f));
		assertEquals(SkillState.READY, HudStatePayload.skillState("CONCUSSED", false, -1, 0f));
	}

	@Test
	void encodesAndDecodesHudPayloadRoundTrip() {
		var original = HudStatePayload.legacy(
				"CONCUSSED", "DIAMOND", "DIAMOND", "IRON", "DIAMOND", "DIAMOND_SWORD",
				"IRON_AEGIS", "GOLDEN_APPLE", "WIND_CHARGE",
				true, 32, 60, 10, 22, 40, 80, 4, 10, false, true,
				"FIGHTING", 2, 840, 0, 1, 0, 2,
				new Fighter("PlayerOne", "CONCUSSED", 18.5f, 20.0f, true),
				new Fighter("PlayerTwo", "BASTION", 12.0f, 20.0f, false),
				"Дружня дуель", 2, true, true,
				List.of(
						new HudStatePayload.Status("stun", 8, 10, 0),
						new HudStatePayload.Status("matadora", 60, 100, 1),
						new HudStatePayload.Status("minecraft:speed", 120, 120, 1)
				)
		);
		var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		HudStatePayload.CODEC.encode(buf, original);
		var decoded = HudStatePayload.CODEC.decode(buf);
		assertEquals(original, decoded);
		assertEquals(3, decoded.statuses().size());
		assertEquals(new HudStatePayload.Status("matadora", 60, 100, 1), decoded.statuses().get(1));
		var loadout = decoded.toLoadout();
		assertEquals(dev.flake.build.Loadout.ArmorMaterial.IRON, loadout.leggings());
		assertEquals(dev.flake.build.Loadout.PassiveItem.IRON_AEGIS, loadout.passiveItem());
		assertEquals(dev.flake.build.Loadout.ActiveItem.GOLDEN_APPLE, loadout.activeItem1());
		assertEquals(dev.flake.build.Loadout.ActiveItem.WIND_CHARGE, loadout.activeItem2());
		assertEquals(32, decoded.slotCooldownRemaining(1));
		assertEquals(40, decoded.slotCooldownRemaining(2));
		assertEquals(10, decoded.slotActiveElapsed(1));
		assertEquals(4, decoded.slotActiveElapsed(2));
		buf.release();
	}

	@Test void encodesViewerRoleItemTotalsAndInfiniteStatuses() {
		var original = new HudStatePayload(
				"BASTION", "DIAMOND", "DIAMOND", "DIAMOND", "DIAMOND", "DIAMOND_SWORD",
				"NONE", "SHIELD", "ENDER_PEARL", true,
				0, 120, -1, 0, 0, 280, -1, 80, false, true,
				"FIGHTING", 1, 0, 300, 0, 0, 2,
				new Fighter("A", "BASTION", 20f, 20f, false),
				new Fighter("B", "VAMPIRE", 20f, 20f, false),
				"Lobby", 3, false, false,
				9L, HudStatePayload.Role.SPECTATOR, HudStatePayload.LoadState.CORRUPT, "countdown", 45,
				List.of(new HudStatePayload.ItemCooldown("minecraft:ender_pearl", 20)),
				List.of(new HudStatePayload.Status("minecraft:night_vision", 0, 0, 1, 2, true))
		);
		var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		HudStatePayload.CODEC.encode(buf, original);
		var decoded = HudStatePayload.CODEC.decode(buf);
		assertEquals(original, decoded);
		assertTrue(decoded.spectator());
		assertFalse(decoded.participant());
		assertEquals(9L, decoded.buildRevision());
		assertEquals("countdown", decoded.blockReason());
		assertEquals(45, decoded.blockRemainingTicks());
		assertEquals(20, decoded.itemCooldownTotal("minecraft:ender_pearl"));
		var infinite = decoded.statuses().getFirst();
		assertTrue(infinite.infinite());
		assertEquals(2, infinite.amplifier());
		assertEquals(120, decoded.slotCooldownTotal(1));
		assertEquals(280, decoded.slotCooldownTotal(2));
		buf.release();
	}

	@Test void malformedStatusesAndCooldownsAreBounded() {
		var build = HudStatePayload.legacy("BASTION", "NONE", "NONE", "NONE", "NONE", "IRON_SWORD", "NONE", "NONE", "NONE",
				true, 0, 0, -1, 0, 0, 0, -1, 0, false, true, "PREPARING", 1, 0, 0, 0, 0, 1, null, null, "", 0, false, false,
				List.of(new HudStatePayload.Status("stun", -5, -9, 0)));
		assertEquals(0, build.statuses().getFirst().ticksLeft());
		assertEquals(0, build.statuses().getFirst().totalTicks());
		assertEquals(0, build.itemCooldownTotal("missing"));
	}

	private HudStatePayload payload(String fighterClass, String active1, String active2) {
		return HudStatePayload.legacy(fighterClass, "NONE", "GOLD", "CHAINMAIL", "IRON", "IRON_SWORD",
				"MOBILITY", active1, active2, true,
				0, 60, -1, 22, 0, 80, -1, 10, false, true,
				"PREPARING", 1, 0, 0, 0, 0, 1, null, null, "", 0, false, false, List.of());
	}
	@Test void unknownContentCannotSilentlyBecomeDefaultBuild() {
		assertThrows(IllegalArgumentException.class, () -> payload("UNKNOWN", "NONE", "NONE").toLoadout());
		assertThrows(IllegalArgumentException.class, () -> payload("BASTION", "UNKNOWN", "NONE").toLoadout());
		var build = payload("BASTION", "WIND_CHARGE", "SHIELD").toLoadout();
		assertEquals(dev.flake.build.Loadout.ArmorMaterial.NONE, build.helmet());
		assertEquals(dev.flake.build.Loadout.ArmorMaterial.GOLD, build.chestplate());
		assertEquals(dev.flake.build.Loadout.ArmorMaterial.CHAINMAIL, build.leggings());
		assertEquals(dev.flake.build.Loadout.PassiveItem.MOBILITY, build.passiveItem());
		assertTrue(build.errors().isEmpty());
		assertFalse(payload("BASTION", "SHIELD", "SHIELD").toLoadout().errors().isEmpty());
	}
	@Test void buildSerializationVersionIsSeparateFromLegacyAndAbilitiesStayTwoSlot() {
		assertEquals("hud_state_v2", HudStatePayload.TYPE.id().getPath());
		assertEquals(SkillState.CHARGING, HudStatePayload.skillState("CONCUSSED", 2, false, 1, 3f));
		for (String cls : List.of("BASTION", "VAMPIRE", "DEMOLITIONIST")) {
			assertEquals(SkillState.READY, HudStatePayload.skillState(cls, 1, false, -1, 0));
			assertEquals(SkillState.READY, HudStatePayload.skillState(cls, 2, false, -1, 0));
		}
	}
}
