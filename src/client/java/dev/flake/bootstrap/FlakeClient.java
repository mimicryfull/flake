package dev.flake.bootstrap;

import dev.flake.features.lobby.client.FlakeScreen;
import dev.flake.client.settings.BattleModeSettings;
import dev.flake.client.settings.FocusOverlaySettings;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flake.Flake;
import dev.flake.combat.ability.AbilitySlotPayload;
import dev.flake.render.vfx.CombatVfxPayload;
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
	public static KeyMapping battleModeKey;

	@Override
	public void onInitializeClient() {
		dev.flake.bootstrap.EffectActors.install();
		dev.flake.text.FlakeI18n.setLanguageSupplier(() -> {
			var mc = net.minecraft.client.Minecraft.getInstance();
			return mc != null && mc.options != null ? mc.options.languageCode : dev.flake.text.FlakeI18n.UK_UA;
		});
		FocusOverlaySettings.load();
		BattleModeSettings.load();
		dev.flake.features.hud.client.FlakePlayerHud.register();
		// No inventory in PvP: the kit is used from the hotbar / item panel only.
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (dev.flake.features.hud.client.FlakePlayerHud.active() && screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
				client.execute(() -> client.setScreenAndShow(null));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> dev.flake.render.vfx.VfxRenderState.clear());
		ClientPlayNetworking.registerGlobalReceiver(CombatVfxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> dev.flake.render.vfx.VfxRenderState.receive(payload)));
		var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Flake.MOD_ID, "controls"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.flake.open", InputConstants.KEY_G, category));
		ability1Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.flake.ability_1", InputConstants.KEY_H, category));
		ability2Key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.flake.ability_2", InputConstants.KEY_R, category));
		battleModeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.flake.battle_mode", InputConstants.KEY_B, category));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			dev.flake.render.vfx.VfxRenderState.checkWorld(client.level);
			while (openKey.consumeClick()) {
				if (client.player != null && client.canInterruptScreen()) client.setScreenAndShow(new FlakeScreen());
			}
			while (ability1Key.consumeClick()) {
				activate(1);
			}
			while (ability2Key.consumeClick()) {
				activate(2);
			}
			while (battleModeKey.consumeClick()) {
				boolean on = BattleModeSettings.toggle();
				if (client.player != null) {
					client.player.sendSystemMessage(
							net.minecraft.network.chat.Component.literal(
									dev.flake.text.FlakeI18n.tr(on ? "flake.screen.battle_mode.on" : "flake.screen.battle_mode.off")));
				}
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
