package dev.flake.features.hud.client;

import dev.flake.client.network.FlakeUiClient;
import dev.flake.client.ui.UiIcons;
import dev.flake.client.ui.UiTheme;

import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffects;

/**
 * Єдиний PvP-док знизу по центру: хелсбар та модифікований хотбар злиті в одну панель у стилі Flake.
 */
public final class FlakePlayerHud {
	private static float shown = -1, trail = -1, regenRate, windowHealth = -1, flash;
	private static long windowNano, lastNano;
	private static float previous = -1;

	private FlakePlayerHud() {}

	/** Активний під час відліку та бою лише для учасників; глядачі бачать ванільний HUD. */
	public static boolean active() {
		var hud = FlakeUiClient.hud();
		return hud != null && FlakeUiClient.hudFresh() && hud.participant()
				&& ("COUNTDOWN".equals(hud.phase()) || "FIGHTING".equals(hud.phase()));
	}

	public static void register() {
		for (var id : java.util.List.of(VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
				VanillaHudElements.FOOD_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.INFO_BAR,
				VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOUNT_HEALTH))
			HudElementRegistry.replaceElement(id, original -> (g, tracker) -> { if (!active()) original.extractRenderState(g, tracker); });
	}

	public static void extract(GuiGraphicsExtractor g, Minecraft client) {
		var player = client.player;
		if (player == null || !active()) {
			if (shown >= 0) {
				shown = -1; trail = -1; previous = -1; windowHealth = -1; flash = 0; lastNano = 0;
				FlakeItemHud.reset();
			}
			return;
		}
		long now = System.nanoTime();
		float dt = lastNano == 0 ? 0.016f : Math.clamp((now - lastNano) / 1e9f, 0.001f, 0.1f);
		lastNano = now;
		float max = Math.max(1, player.getMaxHealth());
		float hp = Math.max(0, player.getHealth());
		float absorb = player.getAbsorptionAmount();
		if (shown < 0) { shown = trail = hp; previous = hp; windowHealth = hp; windowNano = now; }
		shown += (hp - shown) * Math.min(1, dt * 14);
		trail = hp > trail ? hp : Math.max(hp, trail - max * 0.25f * dt);
		if (hp < previous - 0.01f) flash = 1;
		previous = hp;
		flash = Math.max(0, flash - dt * 3.5f);
		if ((now - windowNano) / 1e9f >= 1.0f) {
			regenRate += ((hp - windowHealth) / ((now - windowNano) / 1e9f) - regenRate) * 0.6f;
			windowHealth = hp;
			windowNano = now;
		}
		float timeSec = (now / 1_000_000L % 100_000L) / 1000.0f;
		float fraction = Math.clamp(shown / max, 0, 1);
		boolean regenEffect = player.hasEffect(MobEffects.REGENERATION);
		boolean healing = (regenEffect || regenRate > 0.12f) && hp < max - 0.05f;

		int slot = Math.clamp((g.guiWidth() - 310) / 10, 18, 22);
		boolean hasOffhand = !player.getOffhandItem().isEmpty();
		int hotbarW = slot * 9 + (hasOffhand ? slot + 6 : 0);
		int w = Math.max(hotbarW + 12, 188);
		int h = 29 + slot + 6;
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - h - 4;

		UiTheme.panel(g, x, y, w, h);
		int low = 0xffff5a6e, mid = UiTheme.AMBER, high = UiTheme.GREEN;
		int color = fraction > 0.5f ? UiTheme.lerpColor(mid, high, (fraction - 0.5f) * 2) : UiTheme.lerpColor(low, mid, fraction * 2);

		// Верхні бокові акценти єдиного дока
		g.fill(x, y, x + 2, y + 27, color);
		g.fill(x + w - 2, y, x + w, y + 27, color);

		int bx = x + 6, by = y + 16, bw = w - 12, bh = 8;
		g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, UiTheme.LINE);
		g.fill(bx, by, bx + bw, by + bh, 0xff060608);

		int trailW = Math.round(bw * Math.clamp(trail / max, 0, 1));
		if (trailW > 0) g.fill(bx, by, bx + trailW, by + bh, 0xff9e2a3b);

		int fillW = Math.round(bw * fraction);
		if (fillW > 0) {
			g.fill(bx, by, bx + fillW, by + bh, color);
			g.fill(bx, by, bx + fillW, by + 2, UiTheme.withAlpha(0xffffffff, 0.28f));
		}
		if (healing && fillW > 6) {
			int sw = Math.max(8, fillW / 5), sx = bx + Math.round((fillW - sw) * ((timeSec * 0.9f) % 1f));
			g.fill(sx, by, sx + sw, by + bh, UiTheme.withAlpha(UiTheme.GREEN, 0.45f));
		}
		if (absorb > 0.01f) {
			int aw = Math.round(bw * Math.clamp(absorb / max, 0, 1));
			g.fill(bx, by - 2, bx + aw, by, UiTheme.AMBER);
		}
		for (float tick = 10; tick < max; tick += 10) {
			int tx = bx + Math.round(bw * tick / max);
			g.fill(tx, by, tx + 1, by + bh, 0x77000000);
		}
		if (flash > 0.02f) g.outline(x, y, w, h, UiTheme.withAlpha(0xffff5a6e, flash));
		else if (fraction < 0.3f) g.outline(x, y, w, h, UiTheme.withAlpha(0xffff5a6e, 0.35f + 0.35f * (float) Math.sin(timeSec * 7)));

		UiIcons.heart(g, bx + 1, y + 5, color);
		String hpText = String.format(Locale.ROOT, "%d / %d HP", Math.round(hp), Math.round(max));
		UiTheme.text(g, hpText, bx + 10, y + 4, 86, UiTheme.TEXT);
		UiTheme.centeredText(g, Math.round(fraction * 100) + "%", x + w / 2, y + 4, 42, color);
		String right = healing ? String.format(Locale.ROOT, "+%.1f/с", Math.max(0.1f, regenEffect ? Math.max(regenRate, 0.3f) : regenRate)) : "";
		if (!right.isEmpty()) UiTheme.rightText(g, right, x + w - 6, y + 4, 54, UiTheme.GREEN);
		else if (player.getArmorValue() > 0) UiTheme.rightText(g, "🛡 " + player.getArmorValue(), x + w - 6, y + 4, 54, UiTheme.MUTED);

		// Тонкий розділювач і вбудований модифікований хотбар
		g.fill(bx, y + 27, bx + bw, y + 28, UiTheme.LINE);
		FlakeItemHud.hotbar(g, client, x + (w - hotbarW) / 2, y + 30, slot);
	}
}
