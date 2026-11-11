package dev.flake.client.ui;

import dev.flake.features.loadout.Loadout;
import dev.flake.text.FlakeI18n;
import dev.flake.content.ContentCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiTheme {
	public static final int BACK = 0xf209090c, CARD = 0xff121216, CARD_ALT = 0xff1b1b22, LINE = 0xff272730;
	public static final int TEXT = 0xfff2f4f8, MUTED = 0xff888b96, GREEN = 0xff6ee7b7, RED = 0xffff7b7b, AMBER = 0xffffc46b;
	public static int CYAN = Accent.CYAN.color;
	private static Accent currentAccent = Accent.CYAN;

	public enum Accent {
		CYAN("flake.theme.cyan.title", "flake.theme.cyan.sub", 0xff5ce1e6),
		CRIMSON("flake.theme.crimson.title", "flake.theme.crimson.sub", 0xffff5c77),
		VIOLET("flake.theme.violet.title", "flake.theme.violet.sub", 0xffb37aff),
		EMERALD("flake.theme.emerald.title", "flake.theme.emerald.sub", 0xff52e59b),
		GOLD("flake.theme.gold.title", "flake.theme.gold.sub", 0xffffbe55),
		PEARL("flake.theme.pearl.title", "flake.theme.pearl.sub", 0xffe2e8f0);

		private final String titleKey;
		private final String subtitleKey;
		public final int color;

		Accent(String titleKey, String subtitleKey, int color) {
			this.titleKey = titleKey;
			this.subtitleKey = subtitleKey;
			this.color = color;
		}

		public String title() { return FlakeI18n.tr(titleKey); }
		public String subtitle() { return FlakeI18n.tr(subtitleKey); }
	}

	private UiTheme() {}

	public static Accent accent() {
		return currentAccent;
	}

	public static void setAccent(Accent accent) {
		currentAccent = accent;
		CYAN = accent.color;
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x77000000);
		g.fill(x, y, x + w, y + h, BACK);
		g.outline(x, y, w, h, LINE);
		g.fillGradient(x + 1, y + 1, x + w - 1, y + 2, CYAN, lerpColor(BACK, CYAN, 0.25f));
	}

	public static void text(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int color) {
		var font = Minecraft.getInstance().font;
		if (maxWidth <= 0) return;
		String shown = truncate(text, maxWidth);
		g.text(font, shown, x, y, color, false);
	}

	public static void scaledText(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, float scale, int color) {
		var font = Minecraft.getInstance().font;
		if (maxWidth <= 0 || scale <= 0f) return;
		int unscaledMax = Math.max(0, Math.round(maxWidth / scale));
		String shown = truncate(text, unscaledMax);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, shown, 0, 0, color, false);
		g.pose().popMatrix();
	}

	public static void centeredText(GuiGraphicsExtractor g, String text, int centerX, int y, int maxWidth, int color) {
		var font = Minecraft.getInstance().font;
		if (maxWidth <= 0) return;
		String shown = truncate(text, maxWidth);
		g.text(font, shown, centerX - font.width(shown) / 2, y, color, false);
	}

	public static void rightText(GuiGraphicsExtractor g, String text, int rightX, int y, int maxWidth, int color) {
		var font = Minecraft.getInstance().font;
		if (maxWidth <= 0) return;
		String shown = truncate(text, maxWidth);
		g.text(font, shown, rightX - font.width(shown), y, color, false);
	}

	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int fillColor) {
		if (w <= 0 || h <= 0) return;
		g.fill(x, y, x + w, y + h, 0xff060608);
		float clamped = Math.clamp(progress, 0f, 1f);
		int filled = Math.round(w * clamped);
		if (filled > 0) {
			g.fill(x, y, x + filled, y + h, fillColor);
			if (clamped > 0.03f && clamped < 0.99f && filled >= 2) {
				g.fill(x + filled - 2, y, x + filled, y + h, TEXT);
			}
		}
	}

	public static int lerpColor(int from, int to, float t) {
		float k = Math.clamp(t, 0f, 1f);
		int a = Math.round(((from >>> 24) & 0xff) + (((to >>> 24) & 0xff) - ((from >>> 24) & 0xff)) * k);
		int r = Math.round(((from >>> 16) & 0xff) + (((to >>> 16) & 0xff) - ((from >>> 16) & 0xff)) * k);
		int gr = Math.round(((from >>> 8) & 0xff) + (((to >>> 8) & 0xff) - ((from >>> 8) & 0xff)) * k);
		int b = Math.round((from & 0xff) + ((to & 0xff) - (from & 0xff)) * k);
		return (a << 24) | (r << 16) | (gr << 8) | b;
	}

	public static int withAlpha(int rgb, float alpha) {
		int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255f);
		return (a << 24) | (rgb & 0x00ffffff);
	}

	private static String truncate(String text, int maxWidth) {
		var font = Minecraft.getInstance().font;
		return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
	}

	public static String className(String id) {
		try { return Loadout.FighterClass.valueOf(id).title(); }
		catch (IllegalArgumentException ignored) { return FlakeI18n.tr("flake.accent.unknown.title"); }
	}

	public static String skillName(String classId) {
		return dev.flake.combat.ability.AbilitySlots.title(classId, 1);
	}

	public static String skillDetails(String classId) {
		try {
			var spec = ContentCatalog.fighterClass(Loadout.FighterClass.valueOf(classId));
			return spec == null ? "" : spec.skillSummary();
		} catch (IllegalArgumentException ignored) { return ""; }
	}

	public static String armorName(String id) {
		return switch (id) {
			case "IRON" -> FlakeI18n.tr("flake.armor.iron.short");
			case "DIAMOND" -> FlakeI18n.tr("flake.armor.diamond.short");
			default -> id;
		};
	}

	public static String weaponName(String id) {
		try {
			var spec = ContentCatalog.weapon(Loadout.Weapon.valueOf(id));
			return spec == null ? id : spec.compactTitle();
		} catch (IllegalArgumentException ignored) { return id; }
	}

	public static String accessoryName(String id) {
		try {
			var spec = ContentCatalog.passive(Loadout.PassiveItem.valueOf(id));
			if (spec != null) return spec.compactTitle();
		} catch (IllegalArgumentException ignored) { }
		try {
			var spec = ContentCatalog.active(Loadout.ActiveItem.valueOf(id));
			return spec == null ? id : spec.compactTitle();
		} catch (IllegalArgumentException ignored) { return id; }
	}

	public static String modeName(String modeId) {
		return switch (modeId) {
			case "DUEL" -> FlakeI18n.tr("flake.mode.duel");
			case "TEAM_FIGHT" -> FlakeI18n.tr("flake.mode.team_fight");
			default -> FlakeI18n.tr("flake.mode.duel");
		};
	}
}
