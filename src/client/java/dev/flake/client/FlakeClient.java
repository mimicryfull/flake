package dev.flake.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flake.ExampleMod;
import dev.flake.ability.AbilitySlotPayload;
import dev.flake.duel.ShockwavePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class FlakeClient implements ClientModInitializer {
	public static KeyMapping openKey;
	public static KeyMapping ability1Key;
	public static KeyMapping ability2Key;

	@Override
	public void onInitializeClient() {
		dev.flake.shared.FlakeI18n.setLanguageSupplier(() -> {
			var mc = net.minecraft.client.Minecraft.getInstance();
			return mc != null && mc.options != null ? mc.options.languageCode : dev.flake.shared.FlakeI18n.UK_UA;
		});
		MaskSettings.load();
		dev.flake.client.ui.FlakePlayerHud.register();
		// No inventory in PvP: the kit is used from the hotbar / item panel only.
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (dev.flake.client.ui.FlakePlayerHud.active() && screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
				client.execute(() -> client.setScreenAndShow(null));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ShockwaveRenderState.clear());
		ClientPlayNetworking.registerGlobalReceiver(ShockwavePayload.TYPE, (payload, context) ->
				context.client().execute(() -> ShockwaveRenderState.receive(payload)));
		var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ExampleMod.MOD_ID, "controls"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.flake.open", InputConstants.KEY_G, category));
		ability1Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.flake.ability_1", InputConstants.KEY_H, category));
		ability2Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.flake.ability_2", InputConstants.KEY_R, category));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ShockwaveRenderState.checkWorld(client.level);
			while (openKey.consumeClick()) {
				if (client.player != null && client.canInterruptScreen()) client.setScreenAndShow(new FlakeScreen());
			}
			while (ability1Key.consumeClick()) {
				activate(1);
			}
			while (ability2Key.consumeClick()) {
				activate(2);
			}
		});
	}

	private static void activate(int slot) {
		if (net.minecraft.client.Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(AbilitySlotPayload.TYPE))
			ClientPlayNetworking.send(new AbilitySlotPayload(slot));
	}

	public static String abilityKeyLabel(int slot) {
		var key = slot == 1 ? ability1Key : ability2Key;
		return key == null ? "—" : key.getTranslatedKeyMessage().getString();
	}
}
