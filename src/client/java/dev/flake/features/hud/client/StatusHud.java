package dev.flake.features.hud.client;

import dev.flake.client.ui.UiIcons;
import dev.flake.client.ui.UiTheme;

import dev.flake.features.loadout.Loadout;
import dev.flake.text.FlakeI18n;
import dev.flake.content.ContentCatalog;
import dev.flake.features.hud.HudStatePayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class StatusHud {
	private static final int ROW_HEIGHT = 13;
	private static final int PAD = 4;
	private static final Map<String, String> LABELS = new HashMap<>();
	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	private static float visibility;
	private static boolean active;
	private static long lastExtractNano;

	private StatusHud() {}

	public static boolean active() {
		return dev.flake.client.settings.BattleModeSettings.enabled()
				&& active && System.nanoTime() - lastExtractNano < 500_000_000L;
	}

	public static void extract(GuiGraphicsExtractor g, Minecraft client, HudStatePayload hud, float dt) {
		lastExtractNano = System.nanoTime();
		var statuses = hud.statuses();
		float target = statuses.isEmpty() ? 0f : 1f;
		visibility += (target - visibility) * Math.min(1f, dt * 10f);
		if (statuses.isEmpty() && visibility < 0.02f) {
			visibility = 0f;
			active = false;
			return;
		}
		active = !statuses.isEmpty() || visibility > 0.02f;
		if (visibility < 0.02f) return;

		var font = client.font;
		int maxLabel = 0;
		for (var status : statuses) maxLabel = Math.max(maxLabel, font.width(label(status.id())));
		int secondsWidth = font.width("999с");
		int rows = statuses.size();
		int maxW = Math.max(60, Math.min(190, g.guiWidth() - 12));
		int w = Math.clamp(maxLabel + 16 + secondsWidth + 8, Math.min(92, maxW), maxW);
		int h = rows * ROW_HEIGHT + PAD * 2;
		int x = 6;
		int compactOffset = g.guiWidth() < 420 ? 25 : 6;
		int buildTop = g.guiHeight() - 30 - compactOffset;
		int y = Math.clamp(buildTop - h - 4, 4, Math.max(4, g.guiHeight() - h - 4));

		g.fill(x + 3, y + 4, x + w + 3, y + h + 4, UiTheme.withAlpha(0x000000, visibility * 0.45f));
		g.fill(x, y, x + w, y + h, UiTheme.withAlpha(UiTheme.BACK, visibility * 0.94f));
		g.outline(x, y, w, h, UiTheme.withAlpha(UiTheme.LINE, visibility));
		g.fill(x + 1, y + 1, x + w - 1, y + 2, UiTheme.withAlpha(UiTheme.CYAN, visibility));

		int rowY = y + PAD;
		for (var status : statuses) {
			int accent = status.tone() == 0 ? UiTheme.RED : UiTheme.GREEN;
			int accentAlpha = UiTheme.withAlpha(accent, visibility);
			var icon = icon(status.id());
			if (!icon.isEmpty()) {
				UiIcons.scaledItem(g, icon, x + PAD, rowY + 1, 0.5f);
			} else {
				g.fill(x + PAD + 2, rowY + 4, x + PAD + 7, rowY + 9, accentAlpha);
			}

			String label = status.amplifier() > 0 ? label(status.id()) + " " + roman(status.amplifier() + 1) : label(status.id());
			String remaining = status.infinite() ? "∞" : Math.max(1, (status.ticksLeft() + 19) / 20) + "с";
			int textX = x + PAD + 12;
			int textW = x + w - PAD - font.width(remaining) - 4 - textX;
			UiTheme.text(g, label, textX, rowY + 1, textW, UiTheme.withAlpha(UiTheme.TEXT, visibility));
			UiTheme.rightText(g, remaining, x + w - PAD, rowY + 1, font.width(remaining), accentAlpha);

			float progress = status.infinite() ? 1f : status.ticksLeft() / (float) Math.max(1, status.totalTicks());
			int barW = x + w - PAD - textX;
			g.fill(textX, rowY + 9, textX + barW, rowY + 11, UiTheme.withAlpha(0x060608, visibility));
			int filled = Math.round(barW * Math.clamp(progress, 0f, 1f));
			if (filled > 0) g.fill(textX, rowY + 9, textX + filled, rowY + 11, accentAlpha);
			rowY += ROW_HEIGHT;
		}
	}

	private static String roman(int level) {
		return switch (level) {
			case 0 -> "";
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> Integer.toString(level);
		};
	}

	private static String label(String id) {
		// Cache key includes the active language, otherwise a language switch keeps old labels forever.
		String cacheKey = id + '@' + languageCode();
		String cached = LABELS.get(cacheKey);
		if (cached != null) return cached;
		String label = id;
		if (id.indexOf(':') >= 0) {
			try {
				var effect = BuiltInRegistries.MOB_EFFECT.getOptional(Identifier.parse(id));
				if (effect.isPresent()) label = effect.get().getDisplayName().getString();
			} catch (RuntimeException ignored) {
				label = id;
			}
		} else {
			var definition = ContentCatalog.status(id);
			String key = definition == null ? "flake.status." + id : definition.titleKey();
			String translated = FlakeI18n.tr(key);
			label = translated.equals(key) ? id : translated;
		}
		if (LABELS.size() >= 128) LABELS.clear();
		LABELS.put(cacheKey, label);
		return label;
	}

	/** Selected language code plus the labels it produced; a mismatch means the cache is stale. */
	private static String languageCode() {
		var client = Minecraft.getInstance();
		var manager = client == null ? null : client.getLanguageManager();
		return manager == null ? "en_us" : manager.getSelected();
	}

	private static ItemStack icon(String id) {
		if (ICONS.size() >= 128) ICONS.clear();
		return ICONS.computeIfAbsent(id, StatusHud::createIcon);
	}

	private static ItemStack createIcon(String id) {
		var definition = ContentCatalog.status(id);
		if (definition != null) return definition.createIcon();
		for (var item : Loadout.ActiveItem.values()) {
			var spec = ContentCatalog.active(item);
			if (spec != null && id.equals(spec.buffStatusId())) return UiIcons.activeItem(item);
		}
		return ItemStack.EMPTY;
	}
}
