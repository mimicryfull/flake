package dev.flake.client.editor;

import dev.flake.build.Loadout;
import dev.flake.client.ui.FlakeUiClient;
import dev.flake.client.ui.UiButton;
import dev.flake.client.ui.UiIcons;
import dev.flake.client.ui.UiSlotButton;
import dev.flake.client.ui.UiTheme;
import dev.flake.editor.EditorCatalog;
import dev.flake.editor.EditorCatalog.Slot;
import dev.flake.editor.EditorLayout;
import dev.flake.shared.FlakeI18n;
import dev.flake.ui.LobbyActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Locale;

/** Visual-first editor: eight equipment slots, a detached skin preview and a bottom stat strip. */
public final class BuildEditorScreen extends Screen {
	private static final ItemStack[] STAT_ICONS = {ItemStack.EMPTY, new ItemStack(Items.IRON_CHESTPLATE),
			new ItemStack(Items.IRON_SWORD), new ItemStack(Items.FEATHER), new ItemStack(Items.EMERALD)};
	private final Screen parent;
	private EditorLayout layout;
	private BuildPreview preview;
	private Loadout draft;
	private Loadout acknowledged;
	private String signature = "";
	private boolean pending;
	private long sentAt;
	private String notice = "";
	private boolean openingPicker;
	private dev.flake.ui.BuildResultPayload seenResult;

	public BuildEditorScreen(Screen parent) { super(Component.literal(FlakeI18n.tr("flake.editor.title"))); this.parent = parent; }
	public Loadout build() {
		if (draft == null) draft = serverBuild();
		return draft;
	}
	private static Loadout serverBuild() { var hud = FlakeUiClient.hud(); return hud == null ? Loadout.defaultLoadout() : hud.toLoadout(); }
	private boolean locked() {
		var hud = FlakeUiClient.hud();
		return hud == null || (hud.ready() || ((hud.first().self() || hud.second().self())
				&& !hud.phase().equals("IDLE") && !hud.phase().equals("PREPARING")));
	}
	@Override protected void init() {
		openingPicker = false;
		layout = EditorLayout.fit(width, height);
		if (acknowledged == null) acknowledged = serverBuild();
		if (draft == null) draft = acknowledged;
		signature = build().toString() + locked();
		FlakeUiClient.action(LobbyActionPayload.QUERY, "");
		int x = layout.x(), y = layout.y(), w = layout.width();
		addRenderableWidget(new UiButton(x + w - 58, y + 7, 50, 20, FlakeI18n.tr("flake.editor.btn.exit"), false, () -> minecraft.gui.setScreen(null)));
		addRenderableWidget(new UiButton(x + w - 116, y + 7, 52, 20, FlakeI18n.tr("flake.editor.btn.back"), false, this::onClose));
		Loadout b = build(); int sw = layout.sideWidth(), sh = layout.slotHeight();
		addSlot(Slot.WEAPON, 0, b.weapon().title(), b.weapon().category(), UiIcons.weaponItem(b.weapon()), false);
		addSlot(Slot.PASSIVE, 1, b.passiveItem().title(), FlakeI18n.tr("flake.editor.passive_cost", b.passiveItem().cost()), UiIcons.passiveItem(b.passiveItem()), b.passiveItem() == Loadout.PassiveItem.NONE);
		addSlot(Slot.ACTIVE_1, 2, b.activeItem1().title(), FlakeI18n.tr("flake.editor.active_cost", b.activeItem1().cost()), UiIcons.activeItem(b.activeItem1()), b.activeItem1() == Loadout.ActiveItem.NONE);
		addSlot(Slot.ACTIVE_2, 3, b.activeItem2().title(), FlakeI18n.tr("flake.editor.active_cost", b.activeItem2().cost()), UiIcons.activeItem(b.activeItem2()), b.activeItem2() == Loadout.ActiveItem.NONE);
		int index = 0;
		for (var slot : Loadout.ArmorSlot.values()) {
			var mat = b.armorAt(slot);
			var widget = addRenderableWidget(new UiSlotButton(layout.rightX(), layout.slotY(index++), sw, sh,
					sw < 120 ? switch (slot) {
						case CHESTPLATE -> FlakeI18n.tr("flake.armor_slot.chestplate.short");
						case LEGGINGS -> FlakeI18n.tr("flake.armor_slot.leggings.short");
						case BOOTS -> FlakeI18n.tr("flake.armor_slot.boots.short");
						default -> slot.title();
					} : slot.title(),
					mat == Loadout.ArmorMaterial.NONE ? FlakeI18n.tr("flake.armor.none.short") : mat.title(),
					FlakeI18n.tr("flake.editor.armor_pts", mat.armorFor(slot)), slot.name(), UiIcons.armorPieceItem(slot, mat), false, mat == Loadout.ArmorMaterial.NONE, UiTheme.CYAN,
					() -> open(Slot.valueOf(slot.name()))));
			widget.active = !locked(); widget.setTooltip(Tooltip.create(Component.literal(slot.title() + ": " + mat.title() + " · " + FlakeI18n.tr("flake.editor.armor_independent"))));
		}
		int classH = layout.bodyHeight() < 165 ? 24 : 34;
		int classY = layout.bodyY() + layout.bodyHeight() - classH;
		if (preview == null) preview = new BuildPreview(layout.centerX(), layout.bodyY(), layout.centerWidth(), Math.max(30, classY - layout.bodyY() - 17));
		else preview.setRectangle(layout.centerWidth(), Math.max(30, classY - layout.bodyY() - 17), layout.centerX(), layout.bodyY());
		preview.show(b); addRenderableWidget(preview);
		var cls = addRenderableWidget(new UiSlotButton(layout.centerX(), classY, layout.centerWidth(), classH,
				FlakeI18n.tr("flake.editor.class_caps"), b.fighterClass().title(), b.fighterClass().name(), "CLASS", ItemStack.EMPTY, true, false, UiTheme.CYAN, () -> open(Slot.CLASS)));
		cls.active = !locked(); cls.setTooltip(Tooltip.create(Component.literal(b.fighterClass().description() + " · " + FlakeI18n.tr("flake.editor.class_tooltip_note"))));
		var info = new AbstractWidget(layout.leftX(), layout.statsY(), w - layout.gap() * 2, layout.statsHeight(), Component.literal(FlakeI18n.tr("flake.editor.stats_narrator"))) {
			@Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) { }
			@Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
		};
		var stats = b.stats();
		String passiveLine = stats.passiveBonuses().isEmpty() ? FlakeI18n.tr("flake.editor.passive_none") : String.join("\n", stats.passiveBonuses());
		info.setTooltip(Tooltip.create(Component.literal(FlakeI18n.tr("flake.editor.stats_tooltip",
				stats.maxHealth(), stats.absorption(), stats.armorPoints(), stats.armorToughness(), Math.round(stats.damageReductionPercent()),
				b.weapon().ranged() ? FlakeI18n.tr("flake.editor.stats_tooltip_ranged") : FlakeI18n.tr("flake.editor.stats_tooltip_melee", stats.attackDamage()),
				passiveLine, b.activeItem1().title(), b.activeItem2().title()))));
		addRenderableWidget(info);
	}
	private void addSlot(Slot slot, int index, String title, String category, ItemStack icon, boolean empty) {
		boolean compact = layout.sideWidth() < 120;
		String label = compact ? switch (slot) {
			case PASSIVE -> FlakeI18n.tr("flake.slot.passive.short");
			case ACTIVE_1 -> FlakeI18n.tr("flake.slot.active_1.short");
			case ACTIVE_2 -> FlakeI18n.tr("flake.slot.active_2.short");
			default -> slot.title();
		} : slot.title();
		String shown = compact ? compactTitle(slot, build()) : title;
		var widget = addRenderableWidget(new UiSlotButton(layout.leftX(), layout.slotY(index), layout.sideWidth(), layout.slotHeight(),
				label, shown, category, slot == Slot.WEAPON ? "WEAPON" : slot == Slot.PASSIVE ? "PASSIVE" : "UTILITY", icon, false, empty, UiTheme.CYAN, () -> open(slot)));
		String details = switch (slot) { case WEAPON -> build().weapon().activeInfo(); case PASSIVE -> build().passiveItem().description(); case ACTIVE_1 -> build().activeItem1().description(); case ACTIVE_2 -> build().activeItem2().description(); default -> ""; };
		widget.active = !locked(); widget.setTooltip(Tooltip.create(Component.literal(slot.title() + ": " + title + "\n" + category + "\n" + details + "\n" + FlakeI18n.tr("flake.editor.click_to_choose"))));
	}
	private void open(Slot slot) { if (!locked()) { openingPicker = true; minecraft.gui.setScreen(new ItemPickerScreen(this, slot)); } }
	private static String compactTitle(Slot slot, Loadout b) {
		if (slot == Slot.WEAPON) return dev.flake.shared.SliceRegistry.weapon(b.weapon()).compactTitle();
		if (slot == Slot.PASSIVE) return dev.flake.shared.SliceRegistry.passive(b.passiveItem()).compactTitle();
		return dev.flake.shared.SliceRegistry.active(b.activeItemAt(slot == Slot.ACTIVE_1 ? 1 : 2)).compactTitle();
	}
	void choose(Slot slot, EditorCatalog.Choice choice) {
		if (locked() || !choice.candidate().errors().isEmpty() || minecraft.player == null) return;
		// Optimistic local draft drives preview/stats immediately; one atomic packet replaces the build.
		draft = choice.candidate(); pending = true; sentAt = System.nanoTime(); notice = "";
		seenResult = null;
		if (!FlakeUiClient.applyBuild(draft)) {
			// Protocol unavailable: fall back to the command path so gameplay still works.
			minecraft.player.connection.sendCommand(EditorCatalog.command(slot, choice));
		}
		minecraft.gui.setScreen(this);
	}
	@Override public void tick() {
		handleResult();
		Loadout server = serverBuild();
		if (!server.equals(acknowledged)) {
			acknowledged = server;
			if (!pending || server.equals(draft)) { draft = server; pending = false; }
		}
		if (pending && server.equals(draft)) pending = false;
		if (pending && System.nanoTime() - sentAt > 2_000_000_000L) {
			draft = server; pending = false; notice = FlakeI18n.tr("flake.editor.notice.timeout");
		}
		if (!(build().toString() + locked()).equals(signature)) rebuildWidgets();
	}
	/** Server verdict for the last atomic write: never present an unsaved build as saved. */
	private void handleResult() {
		var result = FlakeUiClient.buildResult();
		if (result == null || !FlakeUiClient.buildResultFresh() || result == seenResult) return;
		seenResult = result;
		switch (result.status()) {
			case OK -> { pending = false; notice = ""; }
			case STALE -> { pending = false; draft = serverBuild(); acknowledged = draft; notice = FlakeI18n.tr("flake.editor.error.stale"); }
			case INVALID, SAVE_FAILED -> {
				pending = false;
				draft = serverBuild();
				acknowledged = draft;
				notice = result.message().isEmpty() ? FlakeI18n.tr("flake.editor.error.save") : result.message();
			}
			case LOCKED -> { pending = false; draft = serverBuild(); acknowledged = draft; notice = FlakeI18n.tr("flake.editor.error.locked"); }
		}
		rebuildWidgets();
	}

	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		g.fill(0, 0, width, height, 0xb8050508);
		int x = layout.x(), y = layout.y(), w = layout.width();
		UiTheme.panel(g, x, y, w, layout.height());
		UiTheme.scaledText(g, "FLAKE", x + 12, y + 11, 66, 1.3f, UiTheme.CYAN);
		if (w > 340) UiTheme.text(g, FlakeI18n.tr("flake.editor.header_caps"), x + 76, y + 13, w - 210, UiTheme.MUTED);
		g.fill(x + 10, y + 32, x + w - 10, y + 33, UiTheme.LINE);
		int center = layout.centerX() + layout.centerWidth() / 2;
		int hintY = preview.getBottom() + 3;
		UiTheme.centeredText(g, FlakeI18n.tr(layout.centerWidth() < 140 ? "flake.editor.rotate_short" : "flake.editor.rotate_long"), center, hintY, layout.centerWidth(), UiTheme.MUTED);
		int floorY = preview.getBottom() - 8, floorW = Math.min(76, layout.centerWidth() - 12);
		g.fill(center - floorW / 2, floorY, center + floorW / 2, floorY + 1, UiTheme.LINE);
		extractStats(g);
		preview.show(build());
		super.extractRenderState(g, mx, my, delta);
	}
	private void extractStats(GuiGraphicsExtractor g) {
		var b = build(); var stats = b.stats(); int x = layout.leftX(), y = layout.statsY(), w = layout.width() - layout.gap() * 2;
		g.fill(x, y, x + w, y + layout.statsHeight(), UiTheme.CARD);
		g.outline(x, y, w, layout.statsHeight(), UiTheme.LINE);
		g.fill(x, y, x + w, y + 1, UiTheme.withAlpha(UiTheme.CYAN, 0.55f));
		boolean compact = w < 440;
		String[] labels = {
			FlakeI18n.tr(compact ? "flake.editor.col.hp_short" : "flake.editor.col.hp"),
			FlakeI18n.tr("flake.editor.col.armor"),
			FlakeI18n.tr("flake.editor.col.damage"),
			FlakeI18n.tr(compact ? "flake.editor.col.speed_short" : "flake.editor.col.speed"),
			FlakeI18n.tr(compact ? "flake.editor.col.cost_short" : "flake.editor.col.cost")
		};
		String[] values = {
			String.format(Locale.ROOT, "%.0f HP%s", stats.maxHealth(), stats.absorption() > 0 ? " +2" : ""),
			compact ? stats.armorPoints() + " / 20" : stats.armorPoints() + " · ≤" + Math.round(stats.damageReductionPercent()) + "%*",
			Float.isNaN(stats.attackDamage()) ? FlakeI18n.tr("flake.editor.ranged_shot") : String.format(Locale.ROOT, "%.1f HP", stats.attackDamage()),
			stats.speedPercent() + "%",
			stats.cost() + " / " + Loadout.BUDGET
		};
		int col = w / 5;
		for (int i = 0; i < 5; i++) {
			int iconX = x + i * col + 7;
			if (i == 0) UiIcons.heart(g, iconX + 1, y + 7, 0xffc99b91);
			else UiIcons.scaledItem(g, STAT_ICONS[i], iconX, y + 5, 0.625f);
			UiTheme.text(g, labels[i], iconX + 13, y + 6, col - 22, UiTheme.MUTED);
			UiTheme.scaledText(g, values[i], x + i * col + 7, y + 18, col - 12, 1f, i == 4 ? UiTheme.CYAN : UiTheme.TEXT);
		}
		String prefix = FlakeI18n.tr("flake.editor.passive_prefix");
		String effects = stats.passiveBonuses().isEmpty() ? FlakeI18n.tr("flake.editor.passive_none")
				: compact ? prefix + b.passiveItem().title() + " · " + b.passiveItem().description()
				: prefix + String.join(" · ", stats.passiveBonuses());
		UiTheme.text(g, effects, x + 7, y + 33, w - 14, UiTheme.TEXT);
		var hud = FlakeUiClient.hud();
		String foot = hud == null ? FlakeI18n.tr("flake.editor.foot.waiting")
				: hud.loadState() == dev.flake.ui.HudStatePayload.LoadState.CORRUPT ? FlakeI18n.tr("flake.editor.foot.corrupt")
				: locked() ? FlakeI18n.tr("flake.editor.foot.locked") : !notice.isEmpty() ? notice
				: pending ? FlakeI18n.tr("flake.editor.foot.pending") : FlakeI18n.tr("flake.editor.foot.armor_note");
		UiTheme.text(g, foot, x + 7, y + layout.statsHeight() - 12, w - 14, locked() || !notice.isEmpty() ? UiTheme.AMBER : UiTheme.MUTED);
	}
	@Override public void onClose() { minecraft.gui.setScreen(parent); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
	@Override public void removed() {
		// A modal owns this screen while open; preserve its mannequin there, release on real exit.
		if (!openingPicker && preview != null) preview.release();
	}
}
