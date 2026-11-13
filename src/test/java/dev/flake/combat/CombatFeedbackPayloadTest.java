package dev.flake.combat;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatFeedbackPayloadTest {
	@Test
	void roundTripsStructuredFeedbackPayload() {
		var original = new CombatFeedbackPayload(
				"flake:void_anchor",
				"item.flake.void_anchor",
				"flake.feedback.cooldown",
				"1.4",
				CombatFeedbackPayload.TONE_COOLDOWN);
		var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		CombatFeedbackPayload.CODEC.encode(buf, original);
		CombatFeedbackPayload decoded = CombatFeedbackPayload.CODEC.decode(buf);
		assertEquals(original, decoded);
	}
}
