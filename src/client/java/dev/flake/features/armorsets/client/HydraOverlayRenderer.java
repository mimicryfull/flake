package dev.flake.features.armorsets.client;

import dev.flake.Flake;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Renders Hydra Armor's 20% opacity burgundy first-person tint when its boosted regen is active. */
public final class HydraOverlayRenderer {
	/** ARGB burgundy (#5E081C) at exact 20% opacity (0x33 = 51 / 255). */
	private static final int BURGUNDY_20_ARGB = 0x335E081C;

	private HydraOverlayRenderer() {}

	public static void register() {
		HudElementRegistry.addFirst(
				Identifier.fromNamespaceAndPath(Flake.MOD_ID, "hydra_frenzy_tint"),
				(g, tracker) -> render(g));
	}

	private static void render(GuiGraphicsExtractor g) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (!mc.options.getCameraType().isFirstPerson()) return;
		if (!HydraAuraActor.isLocalFrenzyActive(mc.player.getId())) return;
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), BURGUNDY_20_ARGB);
	}
}
