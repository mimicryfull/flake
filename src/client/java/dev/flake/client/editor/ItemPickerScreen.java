package dev.flake.client.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flake.ability.AbilitySlots;
import dev.flake.build.Loadout;
import dev.flake.client.FlakeClient;
import dev.flake.client.ui.FlakeTooltip;
import dev.flake.client.ui.UiButton;
import dev.flake.client.ui.UiIcons;
import dev.flake.client.ui.UiTheme;
import dev.flake.editor.EditorCatalog;
import dev.flake.editor.EditorCatalog.Choice;
import dev.flake.editor.EditorCatalog.Slot;
import dev.flake.shared.FlakeI18n;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Separate Screen makes the picker truly modal: background controls cannot receive input. */
public final class ItemPickerScreen extends Screen {
	private final BuildEditorScreen editor;
	private final Slot slot;
	private EditBox search;
	private String query = "", filter = "";
	private int page;
	private int x, y, w, h, pages, results;
	private List<Choice> choices = List.of();
	private final List<AbstractWidget> dynamic = new ArrayList<>();
	private String buildSignature = "";

	public ItemPickerScreen(BuildEditorScreen editor, Slot slot) {
		super(Component.literal(FlakeI18n.tr("flake.picker.header.title", slot.title()))); this.editor = editor; this.slot = slot;
	}
	@Override protected void init() {
		dynamic.clear();
		if (filter.isEmpty()) filter = FlakeI18n.tr("flake.filter.all");
		int margin = width < 420 || height < 280 ? 6 : 20;
		w = Math.min(450, width - margin * 2); h = Math.min(320, height - margin * 2);
		x = (width - w) / 2; y = (height - h) / 2;
		if (editor.width != width || editor.height != height) editor.resize(width, height);
		addRenderableWidget(new UiButton(x + w - 64, y + 8, 54, 20, FlakeI18n.tr("flake.picker.btn.back"), false, this::onClose));
		search = addRenderableWidget(new EditBox(font, x + 12, y + 34, w - 24, 18, Component.literal(FlakeI18n.tr("flake.picker.search_narrator"))));
		search.setMaxLength(80); search.setHint(Component.literal(FlakeI18n.tr("flake.picker.search_hint"))); search.setValue(query);
		search.setResponder(value -> { query = value; page = 0; refresh(); });
		refresh();
		setInitialFocus(search);
	}
	private void refresh() {
		for (var widget : dynamic) removeWidget(widget);
		dynamic.clear();
		buildSignature = editor.build().toString();
		choices = EditorCatalog.choices(slot, editor.build());
		String allLabel = FlakeI18n.tr("flake.filter.all");
		var filters = new ArrayList<String>(); filters.add(allLabel);
		for (var choice : choices) for (String tag : choice.tags()) if (!filters.contains(tag)) filters.add(tag);
		if (!filters.contains(filter)) filter = allLabel;
		int filterW = Math.min(w - 104, Math.max(95, (w - 30) * 62 / 100));
		addDynamic(new UiButton(x + 12, y + 57, filterW, 18, FlakeI18n.tr("flake.picker.filter_btn", filter), !filter.equals(allLabel), () -> {
			filter = filters.get((filters.indexOf(filter) + 1) % filters.size()); page = 0; refresh();
		}));
		List<Choice> filtered = choices.stream().filter(c -> c.matches(query, filter)).toList();
		results = filtered.size();
		int areaH = h - 112;
		int gap = 4;
		int targetH = slot == Slot.CLASS ? 46 : 34;
		int rows = Math.max(1, (areaH + gap) / (targetH + gap));
		int rowH = Math.clamp((areaH - (rows - 1) * gap) / rows, slot == Slot.CLASS ? 40 : 30, slot == Slot.CLASS ? 52 : 38);
		int capacity = rows;
		pages = Math.max(1, (results + capacity - 1) / capacity);
		page = Math.clamp(page, 0, pages - 1);
		int rowW = w - 24;
		for (int i = page * capacity; i < Math.min(results, (page + 1) * capacity); i++) {
			var choice = filtered.get(i); int index = i - page * capacity;
			var row = new PickerRow(x + 12, y + 81 + index * (rowH + gap), rowW, rowH, choice);
			addDynamic(row);
		}
		if (pages > 1) {
			var previous = new UiButton(x + w - 98, y + h - 25, 40, 18, "←", false, () -> { page--; refresh(); });
			var next = new UiButton(x + w - 52, y + h - 25, 40, 18, "→", false, () -> { page++; refresh(); });
			previous.active = page > 0; next.active = page + 1 < pages;
			addDynamic(previous); addDynamic(next);
		}
	}
	private void addDynamic(AbstractWidget widget) { dynamic.add(widget); addRenderableWidget(widget); }
	@Override public void tick() {
		editor.tick();
		if (!buildSignature.equals(editor.build().toString())) refresh();
	}
	@Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		if (pages > 1 && vertical != 0) { page = Math.clamp(page + (vertical < 0 ? 1 : -1), 0, pages - 1); refresh(); return true; }
		return super.mouseScrolled(mx, my, horizontal, vertical);
	}
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		editor.extractRenderState(g, -10000, -10000, delta);
		g.nextStratum();
		g.fill(0, 0, width, height, 0xb8050508);
		UiTheme.panel(g, x, y, w, h);
		UiTheme.scaledText(g, slot == Slot.CLASS ? FlakeI18n.tr("flake.picker.choose_class") : slot.title(), x + 12, y + 11, w - 90, 1.15f, UiTheme.TEXT);
		if (results == 0) UiTheme.centeredText(g, FlakeI18n.tr("flake.picker.empty"), x + w / 2, y + h / 2 + 12, w - 30, UiTheme.MUTED);
		UiTheme.text(g, FlakeI18n.tr("flake.picker.footer", results, page + 1, pages), x + 12, y + h - 19, w - (pages > 1 ? 116 : 24), UiTheme.MUTED);
		super.extractRenderState(g, mx, my, delta);
		for (var child : children()) {
			if (!(child instanceof PickerRow row) || !row.visible) continue;
			if (row.isHovered() || (row.isFocused() && minecraft.getLastInputType().isKeyboard())) {
				g.nextStratum();
				FlakeTooltip.render(g, row.tooltip, row.isHovered() ? mx : row.getX(), row.isHovered() ? my : row.getBottom());
				break;
			}
		}
	}
	@Override public void onClose() { minecraft.gui.setScreen(editor); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }

	private final class PickerRow extends AbstractWidget {
		private final Choice choice;
		private final ItemStack icon;
		private final String abilitiesSummary;
		private final boolean eligible;
		private final FlakeTooltip.Model tooltip;
		private float hover;
		private long frame = System.nanoTime();
		PickerRow(int rx, int ry, int rw, int rh, Choice choice) {
			super(rx, ry, rw, rh, Component.literal(choice.title() + ". " + choice.description() + ". " + choice.eligibility()));
			this.choice = choice; eligible = choice.candidate().errors().isEmpty();
			icon = iconFor(choice);
			var abilityLines = new ArrayList<String>();
			if (slot == Slot.CLASS) {
				for (int index = 1; index <= AbilitySlots.COUNT; index++) {
					var ability = AbilitySlots.resolve(choice.id(), index);
					abilityLines.add(FlakeClient.abilityKeyLabel(index) + ": " + (ability.implemented() ? ability.title() : FlakeI18n.tr("flake.ability.unassigned")));
				}
			}
			abilitiesSummary = String.join("  ·  ", abilityLines);
			var rows = new ArrayList<FlakeTooltip.Row>();
			for (var change : EditorCatalog.comparison(editor.build(), choice.candidate())) {
				String values = number(change.current()) + " → " + number(change.candidate()) + change.stat().unit();
				String difference = change.delta() == 0 ? FlakeI18n.tr("flake.picker.no_change")
						: (change.delta() > 0 ? "+" : "") + number(change.delta()) + change.stat().unit();
				int color = change.delta() == 0 ? UiTheme.MUTED : change.improvement() ? UiTheme.GREEN : UiTheme.RED;
				rows.add(new FlakeTooltip.Row(change.stat().title(), values + "  (" + difference + ")", color));
			}
			var notes = new ArrayList<String>();
			notes.add(FlakeI18n.tr("flake.picker.current_prefix", EditorCatalog.equippedTitle(slot, editor.build())));
			notes.add(choice.eligibility());
			int accent = choice.selected() ? UiTheme.GREEN : !eligible ? UiTheme.AMBER : UiTheme.CYAN;
			tooltip = new FlakeTooltip.Model(icon, choice.title(), choice.category(), choice.description(),
					rows, abilityLines, notes, accent);
		}
		@Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
			long now = System.nanoTime(); float step = Math.min(1f, (now - frame) / 85_000_000f); frame = now;
			hover += ((isHoveredOrFocused() ? 1f : 0f) - hover) * step;
			int rx = getX(), ry = getY();
			int accent = choice.selected() ? UiTheme.GREEN : !eligible ? UiTheme.AMBER : UiTheme.CYAN;
			int bg = choice.selected() ? UiTheme.CARD_ALT : UiTheme.CARD;
			g.fill(rx, ry, getRight(), getBottom(), bg);
			if (hover > 0.01f) {
				g.fill(rx, ry, getRight(), getBottom(), UiTheme.withAlpha(accent, hover * 0.12f));
			}
			int border = choice.selected() ? UiTheme.GREEN : (isFocused() ? UiTheme.CYAN : UiTheme.lerpColor(UiTheme.LINE, accent, hover * 0.85f));
			g.outline(rx, ry, width, height, border);
			g.fill(rx, ry, rx + 2, getBottom(), choice.selected() ? UiTheme.GREEN : UiTheme.withAlpha(accent, Math.max(0.28f, hover)));

			int iconBox = Math.min(height - 6, 26);
			int ix = rx + 6, iy = ry + (height - iconBox) / 2;
			g.fill(ix, iy, ix + iconBox, iy + iconBox, 0xff09090c);
			g.outline(ix, iy, iconBox, iconBox, choice.selected() ? UiTheme.GREEN : UiTheme.LINE);
			if (slot == Slot.CLASS) {
				UiIcons.classEmblem(g, ix + (iconBox - 10) / 2, iy + (iconBox - 10) / 2, choice.id(), accent);
			} else if (icon.isEmpty()) {
				UiIcons.slotSilhouette(g, ix + iconBox / 2, iy + iconBox / 2,
						slot.armor() ? slot.name() : slot == Slot.WEAPON ? "WEAPON" : "PASSIVE", UiTheme.MUTED);
			} else {
				UiIcons.scaledItem(g, icon, ix + (iconBox - 16) / 2, iy + (iconBox - 16) / 2, 1f);
			}

			String statusText = choice.selected() ? FlakeI18n.tr("flake.picker.badge.equipped")
					: !eligible ? FlakeI18n.tr("flake.picker.badge.taken")
					: FlakeI18n.tr("flake.picker.badge.select");
			String badgeText = statusText;
			int badgeW = font.width(badgeText) + 10;
			int badgeX = getRight() - badgeW - 6;
			int badgeY = ry + 5;
			g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 12, 0xff09090c);
			g.outline(badgeX, badgeY, badgeW, 12, choice.selected() ? UiTheme.GREEN : (!eligible ? UiTheme.AMBER : UiTheme.LINE));
			UiTheme.centeredText(g, badgeText, badgeX + badgeW / 2, badgeY + 2, badgeW - 2,
					choice.selected() ? UiTheme.GREEN : (!eligible ? UiTheme.AMBER : UiTheme.CYAN));

			int textX = ix + iconBox + 7;
			int topMaxW = Math.max(24, badgeX - textX - 6);
			String header = choice.title() + "  ·  " + (slot == Slot.CLASS ? role(choice.id()) : choice.category());
			UiTheme.text(g, header, textX, ry + 5, topMaxW, choice.selected() ? UiTheme.GREEN : UiTheme.TEXT);
			UiTheme.text(g, choice.description(), textX, ry + 17, width - (textX - rx) - 8, UiTheme.MUTED);
			if (!abilitiesSummary.isEmpty() && height >= 40) {
				UiTheme.text(g, abilitiesSummary, textX, ry + 28, width - (textX - rx) - 8, UiTheme.CYAN);
			}
		}
		private void select() { if (eligible) editor.choose(slot, choice); }
		@Override public void onClick(MouseButtonEvent event, boolean doubleClick) { select(); }
		@Override public boolean keyPressed(KeyEvent event) {
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE || event.key() == InputConstants.KEY_NUMPADENTER) { select(); return true; }
			return super.keyPressed(event);
		}
		@Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
	}
	private ItemStack iconFor(Choice choice) {
		if (slot.armor()) return UiIcons.armorPieceItem(slot.armorSlot(), Loadout.ArmorMaterial.valueOf(choice.id()));
		return switch (slot) {
			case WEAPON -> UiIcons.weaponItem(Loadout.Weapon.valueOf(choice.id()));
			case PASSIVE -> UiIcons.passiveItem(Loadout.PassiveItem.valueOf(choice.id()));
			case ACTIVE_1, ACTIVE_2 -> UiIcons.activeItem(Loadout.ActiveItem.valueOf(choice.id()));
			default -> ItemStack.EMPTY;
		};
	}
	private static String role(String id) {
		try { return dev.flake.shared.SliceRegistry.fighterClass(Loadout.FighterClass.valueOf(id)).roleTitle(); }
		catch (IllegalArgumentException ignored) { return FlakeI18n.tr("flake.category.class"); }
	}
	private static String number(float value) {
		return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
	}
}
