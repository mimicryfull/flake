package dev.flake.client.ui.widgets;

import dev.flake.client.ui.UiIcons;
import dev.flake.client.ui.UiTheme;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Interactive equipment slot widget for the visual build editor.
 * Renders either the equipped item icon or a schematic silhouette when empty,
 * along with slot title, equipped item name, and active selection highlight.
 */
public final class UiSlotButton extends AbstractWidget {
	private final String slotLabel;
	private final String itemName;
	private final String subInfo;
	private final String silhouetteKind;
	private final ItemStack stack;
	private final boolean selectedSlot;
	private final boolean emptySlot;
	private final int accentColor;
	private final Runnable onSelect;
	private float hover;
	private float press;
	private long lastFrame = System.nanoTime();

	public UiSlotButton(
			int x, int y, int w, int h,
			String slotLabel,
			String itemName,
			String subInfo,
			String silhouetteKind,
			ItemStack stack,
			boolean selectedSlot,
			boolean emptySlot,
			int accentColor,
			Runnable onSelect
	) {
		super(x, y, w, h, Component.literal(slotLabel + ": " + itemName));
		this.slotLabel = slotLabel;
		this.itemName = itemName;
		this.subInfo = subInfo;
		this.silhouetteKind = silhouetteKind;
		this.stack = stack == null ? ItemStack.EMPTY : stack;
		this.selectedSlot = selectedSlot;
		this.emptySlot = emptySlot;
		this.accentColor = accentColor;
		this.onSelect = onSelect;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime();
		float step = Math.min(1f, (now - lastFrame) / 80_000_000f);
		lastFrame = now;
		hover += ((isHoveredOrFocused() && active ? 1f : 0f) - hover) * step;
		press = Math.max(0f, press - step * 1.6f);

		int x = getX();
		int y = getY();
		int w = width;
		int h = height;

		int bg = selectedSlot ? UiTheme.CARD_ALT : (emptySlot ? 0xff0d0d11 : UiTheme.CARD);
		g.fill(x, y, x + w, y + h, bg);

		if (selectedSlot || hover > 0.01f) {
			float glow = selectedSlot ? Math.max(0.14f, hover * 0.20f) : hover * 0.11f;
			g.fill(x, y, x + w, y + h, UiTheme.withAlpha(accentColor, glow));
		}
		if (press > 0.01f) {
			g.fill(x, y, x + w, y + h, UiTheme.withAlpha(UiTheme.TEXT, press * 0.16f));
		}

		int baseBorder = selectedSlot ? accentColor : UiTheme.LINE;
		int border = isFocused() || selectedSlot ? accentColor : UiTheme.lerpColor(baseBorder, accentColor, hover * 0.85f);
		g.outline(x, y, w, h, border);
		if (selectedSlot || hover > 0.05f) {
			g.fill(x, y, x + 2, y + h, selectedSlot ? accentColor : UiTheme.withAlpha(accentColor, hover));
		}

		// Left icon box inside the slot
		int iconBoxSize = Math.min(h - 6, 30);
		if (w < 120) iconBoxSize = Math.min(iconBoxSize, 22);
		int iconBoxX = x + 5;
		int iconBoxY = y + (h - iconBoxSize) / 2;
		g.fill(iconBoxX, iconBoxY, iconBoxX + iconBoxSize, iconBoxY + iconBoxSize, 0xff09090c);
		g.outline(iconBoxX, iconBoxY, iconBoxSize, iconBoxSize, selectedSlot ? accentColor : UiTheme.LINE);

		int iconCenterX = iconBoxX + iconBoxSize / 2;
		int iconCenterY = iconBoxY + iconBoxSize / 2;
		if ("CLASS".equals(silhouetteKind)) {
			float timeSec = (now / 1_000_000L % 100_000L) / 1000.0f;
			UiIcons.skillBox(g, iconCenterX - 11, iconCenterY - 11, subInfo, accentColor, false, 1f, timeSec);
		} else if (!stack.isEmpty()) {
			float scale = Math.min(2f, (iconBoxSize - 4) / 16f);
			int offset = Math.round(8f * scale);
			UiIcons.scaledItem(g, stack, iconCenterX - offset, iconCenterY - offset, scale);
		} else {
			UiIcons.slotSilhouette(g, iconCenterX, iconCenterY, silhouetteKind,
					selectedSlot ? accentColor : 0xff464958);
		}

		if (w > iconBoxSize + 16) {
			int textX = iconBoxX + iconBoxSize + 7;
			int maxTextW = w - (textX - x) - 5;
			if (h >= 34) {
				int textY = y + (h - (h >= 48 ? 33 : 22)) / 2;
				UiTheme.text(g, slotLabel, textX, textY, maxTextW, selectedSlot ? accentColor : UiTheme.MUTED);
				UiTheme.text(g, itemName, textX, textY + 12, maxTextW, emptySlot ? UiTheme.MUTED : UiTheme.TEXT);
				if (h >= 48 && !subInfo.isEmpty() && !"CLASS".equals(silhouetteKind)) {
					UiTheme.text(g, subInfo, textX, textY + 24, maxTextW, emptySlot ? UiTheme.MUTED : accentColor);
				}
			} else {
				UiTheme.text(g, slotLabel, textX, y + 3, maxTextW, selectedSlot ? accentColor : UiTheme.MUTED);
				UiTheme.text(g, itemName, textX, y + 13, maxTextW, emptySlot ? UiTheme.MUTED : UiTheme.TEXT);
			}
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active && onSelect != null) {
			press = 1f;
			onSelect.run();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (active && isFocused() && onSelect != null && (event.key() == InputConstants.KEY_RETURN
				|| event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) {
			press = 1f;
			playDownSound(Minecraft.getInstance().getSoundManager());
			onSelect.run();
			return true;
		}
		return false;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
