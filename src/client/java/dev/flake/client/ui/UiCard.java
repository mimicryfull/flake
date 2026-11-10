package dev.flake.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Large interactive card with animated hover, icon slot, multi-line description, and status badge. */
public final class UiCard extends AbstractWidget {
	private final String title;
	private final String subtitle;
	private final String line1;
	private final String line2;
	private final String badge;
	private final int badgeColor;
	private final boolean selected;
	private final Runnable action;
	private final BiConsumer<GuiGraphicsExtractor, Integer[]> iconRenderer;
	private float hover;
	private float press;
	private long lastFrame = System.nanoTime();

	public UiCard(
			int x, int y, int w, int h,
			String title, String subtitle, String line1, String line2,
			String badge, int badgeColor, boolean selected,
			BiConsumer<GuiGraphicsExtractor, Integer[]> iconRenderer,
			Runnable action
	) {
		super(x, y, w, h, Component.literal(title));
		this.title = title;
		this.subtitle = subtitle;
		this.line1 = line1;
		this.line2 = line2;
		this.badge = badge;
		this.badgeColor = badgeColor;
		this.selected = selected;
		this.iconRenderer = iconRenderer;
		this.action = action;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float step = Math.min(1f, (now - lastFrame) / 85_000_000f);
		lastFrame = now;
		hover += ((isHoveredOrFocused() && active ? 1f : 0f) - hover) * step;
		press = Math.max(0f, press - step * 1.5f);

		int x = getX();
		int y = getY() - Math.round(hover * 1.5f);
		int w = width;
		int h = height;

		g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x66000000);
		int bg = selected ? UiTheme.CARD_ALT : UiTheme.CARD;
		g.fill(x, y, x + w, y + h, bg);

		if (hover > 0.01f) {
			g.fill(x, y, x + w, y + h, UiTheme.withAlpha(badgeColor, hover * 0.09f));
		}
		if (press > 0.01f) {
			g.fill(x, y, x + w, y + h, UiTheme.withAlpha(UiTheme.TEXT, press * 0.14f));
		}

		int baseBorder = selected ? badgeColor : UiTheme.LINE;
		int border = isFocused() ? UiTheme.CYAN : UiTheme.lerpColor(baseBorder, badgeColor, hover * 0.85f);
		g.outline(x, y, w, h, border);

		float topBar = selected ? 1f : hover;
		if (topBar > 0.02f) {
			int barW = Math.max(4, Math.round((w - 2) * topBar));
			int barX = x + 1 + (w - 2 - barW) / 2;
			g.fill(barX, y + 1, barX + barW, y + 3, badgeColor);
		}

		int contentX = x + 10;
		int headerY = y + 10;
		if (iconRenderer != null) {
			iconRenderer.accept(g, new Integer[] { x + 10, y + 10 });
			contentX = x + 38;
		}

		UiTheme.text(g, title, contentX, headerY, w - (contentX - x) - 8, UiTheme.TEXT);
		if (!subtitle.isEmpty()) {
			UiTheme.text(g, subtitle, contentX, headerY + 12, w - (contentX - x) - 8, badgeColor);
		}

		int descY = iconRenderer != null ? y + 38 : y + 34;
		if (!line1.isEmpty() && h >= 72) {
			UiTheme.text(g, line1, x + 10, descY, w - 20, UiTheme.MUTED);
		}
		if (!line2.isEmpty() && h >= 86) {
			UiTheme.text(g, line2, x + 10, descY + 12, w - 20, UiTheme.MUTED);
		}

		if (!badge.isEmpty()) {
			var font = Minecraft.getInstance().font;
			int bw = Math.min(w - 16, font.width(badge) + 12);
			int bx = x + 10;
			int by = y + h - 19;
			g.fill(bx, by, bx + bw, by + 13, 0xff0b0b0e);
			g.outline(bx, by, bw, 13, selected ? badgeColor : UiTheme.LINE);
			UiTheme.centeredText(g, badge, bx + bw / 2, by + 3, bw - 4, selected ? badgeColor : UiTheme.MUTED);
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active && action != null) {
			press = 1f;
			action.run();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (active && isFocused() && action != null && (event.key() == InputConstants.KEY_RETURN
				|| event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) {
			press = 1f;
			playDownSound(Minecraft.getInstance().getSoundManager());
			action.run();
			return true;
		}
		return false;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
