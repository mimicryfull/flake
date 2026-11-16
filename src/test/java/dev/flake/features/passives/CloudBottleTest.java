package dev.flake.features.passives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.flake.bootstrap.Features;
import dev.flake.content.ContentCatalog;
import dev.flake.features.loadout.Loadout.PassiveItem;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CloudBottleTest {
	@BeforeAll
	static void install() {
		Features.installDefinitions();
	}

	@Test
	void registersPassiveSpecAndPayloadRoundTrips() {
		var spec = ContentCatalog.passive(PassiveItem.CLOUD_BOTTLE);
		assertNotNull(spec);
		assertEquals(PassiveItem.CLOUD_BOTTLE, spec.id());
		assertTrue(CloudBottleRuntime.DOUBLE_JUMP_Y_VEL > 0.42);

		var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			CloudJumpPayload.CODEC.encode(buf, CloudJumpPayload.INSTANCE);
			assertEquals(CloudJumpPayload.INSTANCE, CloudJumpPayload.CODEC.decode(buf));
			assertEquals(0, buf.readableBytes());
		} finally {
			buf.release();
		}
	}
}
