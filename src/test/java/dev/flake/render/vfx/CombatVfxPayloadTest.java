package dev.flake.render.vfx;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatVfxPayloadTest {
	@Test void everyVisualPhaseRoundTripsWithoutChangingTheExistingWireIdentity() {
		assertEquals("flake:shockwave", CombatVfxPayload.TYPE.id().toString());
		for (var phase : CombatVfxPayload.Phase.values()) {
			var original = new CombatVfxPayload(new Vec3(1, 2, 3), 7, phase, 0.75f, 9, new Vec3(4, 5, 6));
			var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
			try {
				CombatVfxPayload.CODEC.encode(buffer, original);
				assertEquals(original, CombatVfxPayload.CODEC.decode(buffer));
			} finally { buffer.release(); }
		}
	}

	@Test void phaseOrdinalsRemainCompatibleWithExistingClients() {
		assertEquals(List.of("CHARGE", "BURST", "CANCEL", "CLEAR", "QUAKE", "IMPACT", "BOLT", "MASK_ON", "MASK_OFF",
				"CRYSTAL_ON", "CRYSTAL_OFF", "CRIT", "SHIELD_RAM", "SHIELD_RAM_SHIELD", "BULWARK_ON", "BULWARK_OFF",
				"BULWARK_HIT", "BLOOD_LUNGE", "BLOODLUST_ON", "BLOODLUST_OFF", "BLOOD_SIPHON", "HAMMER_STUN",
				"AXE_THROW", "BLEED_CRIT", "SPEAR_DASH", "BROADSWORD_SWEEP", "CHRONOSPHERE"),
				java.util.Arrays.stream(CombatVfxPayload.Phase.values()).map(Enum::name).toList());
	}
}
