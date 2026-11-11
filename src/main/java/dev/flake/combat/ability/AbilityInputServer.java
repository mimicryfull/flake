package dev.flake.combat.ability;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Network input adapter; packet identity and codec stay unchanged. */
public final class AbilityInputServer {
	private static boolean installed;
	private AbilityInputServer() {}

	public static void install() {
		if (installed) return;
		installed = true;
		PayloadTypeRegistry.serverboundPlay().register(AbilitySlotPayload.TYPE, AbilitySlotPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AbilitySlotPayload.TYPE, (payload, context) ->
				context.server().execute(() -> ClassAbilityApplication.activate(context.player(), payload.slot())));
	}
}
