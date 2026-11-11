package dev.flake.client.ui.widgets;

import dev.flake.client.ui.UiTheme;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Flat custom control, including mouse, keyboard focus, narration and restrained hover. */
public final class UiButton extends AbstractWidget {
	private final Runnable action;
	private final boolean primary;
	private float hover;
	private float press;
	private long frame = System.nanoTime();

	public UiButton(int x, int y, int w, int h, String label, boolean primary, Runnable action) {
		super(x, y, w, h, Component.literal(label));
		this.primary = primary;
		this.action = action;
	}

	@Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float step = Math.min(1f, (now - frame) / 90_000_000f);
		frame = now;
		hover += ((isHoveredOrFocused() && active ? 1f : 0f) - hover) * step;
		press = Math.max(0f, press - step * 1.4f);

		int fill = !active ? 0xff0d0d10 : primary ? UiTheme.lerpColor(0xff141418, UiTheme.CYAN, 0.16f) : 0xff141418;
		g.fill(getX(), getY(), getRight(), getBottom(), fill);
		if (hover > .01f) {
			g.fill(getX(), getY(), getRight(), getBottom(), UiTheme.withAlpha(UiTheme.CYAN, hover * 0.11f));
		}
		if (press > .01f) {
			g.fill(getX(), getY(), getRight(), getBottom(), UiTheme.withAlpha(UiTheme.TEXT, press * 0.16f));
		}
		int baseBorder = primary && active ? UiTheme.lerpColor(UiTheme.LINE, UiTheme.CYAN, 0.45f) : UiTheme.LINE;
		int border = isFocused() ? UiTheme.CYAN : UiTheme.lerpColor(baseBorder, UiTheme.CYAN, hover * 0.75f);
		g.outline(getX(), getY(), width, height, border);

		float barFactor = primary && active ? 1f : hover;
		if (barFactor > .02f && active) {
			int barH = Math.max(2, Math.round(height * barFactor));
			int top = getY() + (height - barH) / 2;
			g.fill(getX(), top, getX() + 2, top + barH, UiTheme.CYAN);
		}

		var font = Minecraft.getInstance().font;
		String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 12));
		g.text(font, label, getX() + (width - font.width(label)) / 2, getY() + (height - 8) / 2,
				active ? UiTheme.TEXT : 0xff555863, false);
	}

	@Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active) {
			press = 1f;
			action.run();
		}
	}

	@Override public boolean keyPressed(KeyEvent event) {
		if (active && isFocused() && (event.key() == InputConstants.KEY_RETURN
				|| event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) {
			press = 1f;
			playDownSound(Minecraft.getInstance().getSoundManager());
			action.run();
			return true;
		}
		return false;
	}

	@Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
