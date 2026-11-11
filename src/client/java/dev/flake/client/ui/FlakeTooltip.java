package dev.flake.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * Flake-styled item tooltip. Shares the editor palette instead of the vanilla tooltip box:
 * accent header, title/category, wrapped description, stat rows, effect bullets and notes.
 */
public final class FlakeTooltip {
	public record Row(String label, String value, int tone) {}
	public record Model(ItemStack icon, String title, String category, String description,
			List<Row> rows, List<String> effects, List<String> notes, int accent) {
		public Model {
			rows = rows == null ? List.of() : List.copyOf(rows);
			effects = effects == null ? List.of() : List.copyOf(effects);
			notes = notes == null ? List.of() : List.copyOf(notes);
		}
		public static Model of(ItemStack icon, String title, String category, String description) {
			return new Model(icon, title, category, description, List.of(), List.of(), List.of(), UiTheme.CYAN);
		}
	}

	private static final int PAD = 9;
	private static final int LINE = 10;
	private static final int MAX_WIDTH = 268;
	private static final int MIN_WIDTH = 150;

	private FlakeTooltip() {}

	public static void render(GuiGraphicsExtractor g, Model model, int mouseX, int mouseY) {
		if (model == null) return;
		var client = Minecraft.getInstance();
		Font font = client.font;
		int screenW = g.guiWidth(), screenH = g.guiHeight();
		int maxWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, screenW - 12));
		int innerWidth = maxWidth - PAD * 2;

		boolean hasIcon = model.icon() != null && !model.icon().isEmpty();
		int headerH = hasIcon ? 30 : 20;
		List<String> description = wrap(font, model.description(), innerWidth);
		List<Row> rows = new ArrayList<>(model.rows());
		List<String> effects = new ArrayList<>();
		for (String effect : model.effects()) {
			List<String> wrapped = wrap(font, effect, innerWidth - 8);
			if (!wrapped.isEmpty()) effects.addAll(wrapped);
		}
		List<String> notes = new ArrayList<>();
		for (String note : model.notes()) notes.addAll(wrap(font, note, innerWidth));

		int maxHeight = Math.max(48, screenH - 8);
		int height = measure(headerH, description, rows, effects, notes);
		// Long localized text must never overflow the screen: trim the least important tail.
		while (height > maxHeight) {
			if (!notes.isEmpty()) notes.removeLast();
			else if (!effects.isEmpty()) effects.removeLast();
			else if (!description.isEmpty()) description.removeLast();
			else if (!rows.isEmpty()) rows.removeLast();
			else break;
			height = measure(headerH, description, rows, effects, notes);
		}
		boolean hasRows = !rows.isEmpty();
		boolean hasEffects = !effects.isEmpty();
		boolean hasNotes = !notes.isEmpty();

		int x = mouseX + 12;
		if (x + maxWidth > screenW - 4) x = mouseX - 12 - maxWidth;
		x = Math.clamp(x, 4, Math.max(4, screenW - maxWidth - 4));
		int y = mouseY - 12;
		y = Math.clamp(y, 4, Math.max(4, screenH - height - 4));

		int width = maxWidth;
		UiTheme.panel(g, x, y, width, height);
		g.fill(x, y + 1, x + 2, y + height - 1, model.accent());
		g.fill(x + PAD, y + PAD + headerH, x + width - PAD, y + PAD + headerH + 1, UiTheme.LINE);

		int contentY = y + PAD;
		if (hasIcon) {
			g.fill(x + PAD, contentY, x + PAD + 20, contentY + 20, 0xff09090c);
			g.outline(x + PAD, contentY, 20, 20, UiTheme.withAlpha(model.accent(), 0.45f));
			UiIcons.scaledItem(g, model.icon(), x + PAD + 2, contentY + 2, 1f);
			UiTheme.text(g, model.title(), x + PAD + 26, contentY + 1, width - PAD * 2 - 26, UiTheme.TEXT);
			UiTheme.text(g, model.category(), x + PAD + 26, contentY + 12, width - PAD * 2 - 26, model.accent());
		} else {
			UiTheme.text(g, model.title(), x + PAD, contentY, width - PAD * 2, UiTheme.TEXT);
			UiTheme.text(g, model.category(), x + PAD, contentY + 10, width - PAD * 2, model.accent());
		}
		contentY += headerH;

		if (!description.isEmpty()) {
			contentY += 4;
			for (String line : description) {
				UiTheme.text(g, line, x + PAD, contentY, innerWidth, UiTheme.MUTED);
				contentY += LINE;
			}
		}

		if (hasRows) {
			contentY += 6;
			UiTheme.text(g, dev.flake.text.FlakeI18n.tr("flake.tooltip.stats"), x + PAD, contentY, innerWidth, UiTheme.TEXT);
			contentY += 10;
			for (Row row : rows) {
				int valueColor = row.tone() == 0 ? UiTheme.TEXT : row.tone();
				String label = row.label() == null ? "" : row.label();
				String value = row.value() == null ? "" : row.value();
				int valueWidth = Math.min(font.width(value), innerWidth - 40);
				UiTheme.text(g, label, x + PAD, contentY, innerWidth - valueWidth - 6, UiTheme.MUTED);
				UiTheme.rightText(g, value, x + width - PAD, contentY, valueWidth + 2, valueColor);
				contentY += 11;
			}
		}

		if (hasEffects) {
			contentY += 6;
			UiTheme.text(g, dev.flake.text.FlakeI18n.tr("flake.tooltip.effects"), x + PAD, contentY, innerWidth, UiTheme.TEXT);
			contentY += 10;
			for (String line : effects) {
				g.fill(x + PAD, contentY + 4, x + PAD + 2, contentY + 6, model.accent());
				UiTheme.text(g, line, x + PAD + 8, contentY, innerWidth - 8, UiTheme.CYAN);
				contentY += LINE;
			}
		}

		if (hasNotes) {
			contentY += 6;
			for (String line : notes) {
				UiTheme.text(g, line, x + PAD, contentY, innerWidth, UiTheme.AMBER);
				contentY += LINE;
			}
		}
	}

	private static int measure(int headerH, List<String> description, List<Row> rows, List<String> effects, List<String> notes) {
		int height = PAD + headerH + (description.isEmpty() ? 0 : 4 + description.size() * LINE);
		if (!rows.isEmpty()) height += 6 + 10 + rows.size() * 11;
		if (!effects.isEmpty()) height += 6 + 10 + effects.size() * LINE;
		if (!notes.isEmpty()) height += 6 + notes.size() * LINE;
		return height + PAD;
	}

	static List<String> wrap(Font font, String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isBlank() || maxWidth <= 0) return lines;
		for (String paragraph : text.split("\\n", -1)) {
			if (paragraph.isBlank()) {
				lines.add("");
				continue;
			}
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.trim().split("\\s+")) {
				String candidate = line.isEmpty() ? word : line + " " + word;
				if (font.width(candidate) <= maxWidth) {
					line.setLength(0);
					line.append(candidate);
					continue;
				}
				if (!line.isEmpty()) {
					lines.add(line.toString());
					line.setLength(0);
				}
				if (font.width(word) <= maxWidth) {
					line.append(word);
				} else {
					String rest = word;
					while (!rest.isEmpty()) {
						String piece = font.plainSubstrByWidth(rest, maxWidth);
						if (piece.isEmpty()) break;
						if (font.width(rest) <= maxWidth) {
							line.append(rest);
							break;
						}
						lines.add(piece);
						rest = rest.substring(piece.length());
					}
				}
			}
			if (!line.isEmpty()) lines.add(line.toString());
		}
		return lines;
	}
}
