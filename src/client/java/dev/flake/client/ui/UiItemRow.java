package dev.flake.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flake.features.loadout.Loadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Interactive item row in the build editor's right-hand selection panel.
 * Exposes hover state and candidate Loadout so the screen can render real-time stat comparisons.
 */
public final class UiItemRow extends AbstractWidget {
	private final String title;
	private final String categoryAndStats;
	private final String badge;
	private final ItemStack icon;
	private final String silhouetteKind;
	private final boolean equipped;
	private final boolean unequipOption;
	private final Loadout candidateLoadout;
	private final Runnable onEquip;
	private float hover;
	private float press;
	private long lastFrame = System.nanoTime();

	public UiItemRow(
			int x, int y, int w, int h,
			String title,
			String categoryAndStats,
			String badge,
			ItemStack icon,
			String silhouetteKind,
			boolean equipped,
			boolean unequipOption,
			Loadout candidateLoadout,
			Runnable onEquip
	) {
		super(x, y, w, h, Component.literal(title));
		this.title = title;
		this.categoryAndStats = categoryAndStats;
		this.badge = badge;
		this.icon = icon == null ? ItemStack.EMPTY : icon;
		this.silhouetteKind = silhouetteKind;
		this.equipped = equipped;
		this.unequipOption = unequipOption;
		this.candidateLoadout = candidateLoadout;
		this.onEquip = onEquip;
	}

	public Loadout candidateLoadout() {
		return candidateLoadout;
	}

	public String itemTitle() {
		return title;
	}

	public String itemDetails() {
		return categoryAndStats;
	}

	public boolean isEquippedItem() {
		return equipped;
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

		boolean validCandidate = candidateLoadout == null || candidateLoadout.errors().isEmpty();
		int accent = equipped ? UiTheme.GREEN : (!validCandidate ? UiTheme.RED : (unequipOption ? UiTheme.AMBER : UiTheme.CYAN));

		int bg = equipped ? UiTheme.CARD_ALT : UiTheme.CARD;
		g.fill(x, y, x + w, y + h, bg);
		if (hover > 0.01f) {
			g.fill(x, y, x + w, y + h, UiTheme.withAlpha(accent, hover * 0.14f));
		}
		if (press > 0.01f) {
			g.fill(x, y, x + w, y + h, UiTheme.withAlpha(UiTheme.TEXT, press * 0.18f));
		}

		int border = equipped ? UiTheme.GREEN : UiTheme.lerpColor(UiTheme.LINE, accent, hover * 0.85f);
		g.outline(x, y, w, h, border);
		g.fill(x, y, x + 2, y + h, equipped ? UiTheme.GREEN : UiTheme.withAlpha(accent, Math.max(0.25f, hover)));

		int iconBox = Math.min(h - 4, 24);
		int ix = x + 5;
		int iy = y + (h - iconBox) / 2;
		g.fill(ix, iy, ix + iconBox, iy + iconBox, 0xff09090c);
		g.outline(ix, iy, iconBox, iconBox, equipped ? UiTheme.GREEN : UiTheme.LINE);

		if ("CLASS".equals(silhouetteKind)) {
			float timeSec = (now / 1_000_000L % 100_000L) / 1000.0f;
			UiIcons.skillBox(g, ix + (iconBox - 22) / 2, iy + (iconBox - 22) / 2, badge, accent, false, 1f, timeSec);
		} else if (!icon.isEmpty()) {
			UiIcons.scaledItem(g, icon, ix + (iconBox - 18) / 2, iy + (iconBox - 18) / 2, 1.12f);
		} else {
			UiIcons.slotSilhouette(g, ix + iconBox / 2, iy + iconBox / 2, silhouetteKind, 0xff555863);
		}

		int textX = ix + iconBox + 6;
		var font = Minecraft.getInstance().font;
		String shownBadge = equipped ? "ЕКІПІРОВАНО" : ("CLASS".equals(silhouetteKind) ? "ОБРАТИ" : badge);
		int badgeW = font.width(shownBadge) + 8;
		int badgeX = x + w - badgeW - 5;
		int badgeY = y + 4;
		g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 11, 0xff09090c);
		g.outline(badgeX, badgeY, badgeW, 11, equipped ? UiTheme.GREEN : (!validCandidate ? UiTheme.RED : UiTheme.LINE));
		UiTheme.centeredText(g, shownBadge, badgeX + badgeW / 2, badgeY + 2, badgeW - 2,
				equipped ? UiTheme.GREEN : (!validCandidate ? UiTheme.RED : accent));

		int titleMaxW = Math.max(20, badgeX - textX - 4);
		UiTheme.text(g, title, textX, y + 4, titleMaxW, equipped ? UiTheme.GREEN : UiTheme.TEXT);
		UiTheme.text(g, categoryAndStats, textX, y + 15, w - (textX - x) - 6, UiTheme.MUTED);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (active && onEquip != null) {
			press = 1f;
			onEquip.run();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (active && isFocused() && onEquip != null && (event.key() == InputConstants.KEY_RETURN
				|| event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) {
			press = 1f;
			playDownSound(Minecraft.getInstance().getSoundManager());
			onEquip.run();
			return true;
		}
		return false;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
