package dev.flake.features.hud.client;

import dev.flake.client.network.FlakeUiClient;
import dev.flake.client.ui.UiIcons;
import dev.flake.client.ui.UiTheme;
import dev.flake.combat.CombatFeedbackPayload;
import dev.flake.content.ContentCatalog;
import dev.flake.features.hud.HudStatePayload;
import dev.flake.text.FlakeI18n;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Styled hotbar plus right-side Flake UI item cooldown/buff bars and combat failure/reason cards.
 */
public final class FlakeItemHud {
	private static final int VIOLET = 0xffb37aff;
	private static final int WARN = 0xffffa857;
	private static final long TOAST_LIFE_NANOS = 2_800_000_000L;
	private static final long READY_FLASH_NANOS = 700_000_000L;
	private static final int MAX_COOLDOWN_BARS = 4;
	private static final int MAX_TOASTS = 4;

	private static final List<Toast> toasts = new ArrayList<>();
	private static final Map<String, TrackedItem> tracked = new LinkedHashMap<>();
	private static final Map<String, ItemStack> itemIconCache = new HashMap<>();

	private FlakeItemHud() {}

	private record Row(int slot, ItemStack stack, String name, float cooldown, float total, float buffLeft, float buffTotal) {}

	private static final class TrackedItem {
		ItemStack stack = ItemStack.EMPTY;
		String name = "";
		boolean wasCooling;
		float smoothBar;
		long readyUntilNanos;
	}

	private static final class Toast {
		final String iconId;
		final String subjectKey;
		final String reasonKey;
		String arg;
		byte tone;
		long bornNanos;
		float pulse;

		Toast(String iconId, String subjectKey, String reasonKey, String arg, byte tone, long bornNanos) {
			this.iconId = iconId;
			this.subjectKey = subjectKey;
			this.reasonKey = reasonKey;
			this.arg = arg;
			this.tone = tone;
			this.bornNanos = bornNanos;
			this.pulse = 1.0f;
		}
	}

	public static void reset() {
		toasts.clear();
		tracked.clear();
		itemIconCache.clear();
	}

	public static void pushFeedback(CombatFeedbackPayload payload) {
		if (payload == null) return;
		long now = System.nanoTime();
		String iconId = payload.iconId() == null ? "" : payload.iconId();
		String subjectKey = payload.subjectKey() == null ? "" : payload.subjectKey();
		String reasonKey = payload.reasonKey() == null ? "" : payload.reasonKey();
		String arg = payload.arg() == null ? "" : payload.arg();

		for (int i = toasts.size() - 1; i >= 0; i--) {
			var existing = toasts.get(i);
			if (existing.iconId.equals(iconId) && existing.subjectKey.equals(subjectKey) && existing.reasonKey.equals(reasonKey)) {
				existing.arg = arg;
				existing.tone = payload.tone();
				existing.bornNanos = now;
				existing.pulse = 1.0f;
				return;
			}
		}
		toasts.add(new Toast(iconId, subjectKey, reasonKey, arg, payload.tone(), now));
		while (toasts.size() > MAX_TOASTS) toasts.removeFirst();
	}

	public static void notifyClientCooldown(ItemStack stack) {
		var client = Minecraft.getInstance();
		if (client == null || client.player == null || stack == null || stack.isEmpty()) return;
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		var row = row(client.player, -1, stack, partial);
		float seconds = Math.max(0.1f, row.cooldown);
		String subjectKey = stack.get(DataComponents.CUSTOM_NAME) != null
				? stack.getHoverName().getString()
				: stack.getItem().getDescriptionId();
		pushFeedback(new CombatFeedbackPayload(
				itemId(stack),
				subjectKey,
				"flake.feedback.cooldown",
				String.format(Locale.ROOT, "%.1f", seconds),
				CombatFeedbackPayload.TONE_COOLDOWN));
	}

	private static Row row(Player player, int slot, ItemStack stack, float partial) {
		float percent = stack.isEmpty() ? 0 : player.getCooldowns().getCooldownPercent(stack, partial);
		var hud = FlakeUiClient.hud();
		// Prefer the server-synced total (includes passive modifiers); fall back to the item spec.
		float total = ContentCatalog.cooldownSeconds(stack);
		if (!stack.isEmpty()) {
			int synced = hud == null ? 0 : hud.itemCooldownTotal(itemId(stack));
			if (synced > 0) total = synced / 20f;
		}
		// Cooldown and buff are independent server states: a buff can end early (stun) while cooldown runs.
		float buffTotal = 0, buffLeft = 0;
		String statusId = ContentCatalog.buffStatusId(stack);
		if (statusId != null && hud != null) {
			for (var status : hud.statuses()) {
				if (!statusId.equals(status.id())) continue;
				buffTotal = Math.max(0.05f, status.totalTicks() / 20f);
				buffLeft = Math.max(0f, status.ticksLeft() / 20f - FlakeUiClient.hudElapsedSeconds());
				break;
			}
		}
		return new Row(slot, stack, stack.isEmpty() ? "" : stack.getHoverName().getString(),
				total > 0 ? percent * total : percent, total, buffLeft, buffTotal);
	}

	/** Draws 9 hotbar slots (+ the off-hand when occupied) starting at x,y with square slots of the given size. */
	public static void hotbar(GuiGraphicsExtractor g, Minecraft client, int x, int y, int size) {
		var player = client.player;
		if (player == null) return;
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		int selected = player.getInventory().getSelectedSlot();
		int count = 9 + (player.getOffhandItem().isEmpty() ? 0 : 1);
		for (int i = 0; i < count; i++) {
			int sx = x + i * size + (i == 9 ? 6 : 0);
			ItemStack stack = i == 9 ? player.getOffhandItem() : player.getInventory().getItem(i);
			var row = row(player, i, stack, partial);
			boolean buff = row.buffLeft > 0, cooling = row.cooldown > 0.05f;
			boolean sel = i == selected;
			boolean charged = critReady(stack);
			float pulse = charged ? (float) (Math.sin((player.tickCount + partial) * 0.5f + i * 1.3f) * 0.5 + 0.5) : 0f;
			if (charged) sx += Math.round((float) Math.sin((player.tickCount + partial) * 0.9f + i * 1.7f));
			g.fill(sx + 1, y, sx + size - 1, y + size - 2, sel ? UiTheme.CARD_ALT : UiTheme.CARD);
			g.outline(sx + 1, y, size - 2, size - 2, sel ? UiTheme.CYAN : buff ? VIOLET : UiTheme.LINE);
			if (charged) {
				int glow = pulse > 0.5f ? UiTheme.AMBER : VIOLET;
				g.outline(sx, y - 1, size, size, UiTheme.withAlpha(glow, 0.45f + 0.55f * pulse));
			}
			if (sel) g.fill(sx + 3, y + size - 3, sx + size - 3, y + size - 2, UiTheme.CYAN);
			if (!stack.isEmpty()) UiIcons.scaledItem(g, stack, sx + (size - 16) / 2, y + (size - 16) / 2 - 1, 1.0f);
			if (cooling && !buff) {
				float f = row.total > 0 ? Math.clamp(row.cooldown / row.total, 0, 1) : Math.clamp(row.cooldown, 0, 1);
				int h = Math.round((size - 4) * f);
				g.fill(sx + 2, y + 1, sx + size - 2, y + 1 + h, 0xb0000000);
				if (row.total > 0) UiTheme.centeredText(g, String.format(Locale.ROOT, row.cooldown >= 10 ? "%.0f" : "%.1f", row.cooldown), sx + size / 2, y + size / 2 - 5, size, UiTheme.AMBER);
			}
			if (buff) UiTheme.centeredText(g, String.format(Locale.ROOT, "%.1f", row.buffLeft), sx + size / 2, y + size / 2 - 5, size, VIOLET);
			// Real bindings, so a rebind never leaves a stale "1…9"/"F" hint on the slot.
			String keyLabel = i < 9 ? hotbarKeyLabel(client, i) : offhandKeyLabel(client);
			UiTheme.scaledText(g, keyLabel, sx + 3, y + 2, 8, 0.7f, sel ? UiTheme.TEXT : UiTheme.MUTED);
			if (stack.getCount() > 1) UiTheme.rightText(g, Integer.toString(stack.getCount()), sx + size - 3, y + size - 11, 14, UiTheme.TEXT);
		}
	}

	/** Draws right-side Flake UI item cooldown/buff bars above the skill cards and reason feedback cards above them. */
	public static void extract(GuiGraphicsExtractor g, Minecraft client, HudStatePayload hud, int skillTopY, float dt, float timeSec) {
		var player = client.player;
		if (player == null) return;
		long now = System.nanoTime();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

		int cursorY = extractCooldownBars(g, client, player, skillTopY - 6, now, dt, partial);
		extractFeedbackToasts(g, client, hud, cursorY - 2, now, dt);
	}

	private static int extractCooldownBars(GuiGraphicsExtractor g, Minecraft client, Player player,
			int bottomY, long now, float dt, float partial) {
		Map<String, Row> activeRows = new LinkedHashMap<>();
		for (int slot = 0; slot <= 9; slot++) {
			ItemStack stack = slot == 9 ? player.getOffhandItem() : player.getInventory().getItem(slot);
			if (stack.isEmpty() || stack.is(Items.ARROW)) continue;
			String id = itemId(stack);
			if (id.isEmpty() || activeRows.containsKey(id)) continue;
			var row = row(player, slot, stack, partial);
			activeRows.put(id, row);
			var track = tracked.computeIfAbsent(id, k -> new TrackedItem());
			track.stack = stack;
			track.name = row.name;
			boolean cooling = row.cooldown > 0.05f;
			if (track.wasCooling && !cooling && row.buffLeft <= 0.02f) {
				track.readyUntilNanos = now + READY_FLASH_NANOS;
			}
			if (cooling) track.readyUntilNanos = 0L;
			track.wasCooling = cooling;
		}
		tracked.entrySet().removeIf(e -> !activeRows.containsKey(e.getKey()) && now >= e.getValue().readyUntilNanos);

		List<String> visibleIds = new ArrayList<>(MAX_COOLDOWN_BARS);
		for (var entry : tracked.entrySet()) {
			String id = entry.getKey();
			var row = activeRows.get(id);
			var track = entry.getValue();
			boolean buff = row != null && row.buffLeft > 0.02f;
			boolean cooling = row != null && row.cooldown > 0.05f;
			boolean readyFlash = !buff && !cooling && now < track.readyUntilNanos;
			if (!buff && !cooling && !readyFlash) continue;
			visibleIds.add(id);
			if (visibleIds.size() >= MAX_COOLDOWN_BARS) break;
		}
		if (visibleIds.isEmpty()) return bottomY;

		boolean compact = g.guiWidth() < 420;
		int w = compact ? 118 : Math.min(148, g.guiWidth() / 2 - 96);
		if (w < 92) return bottomY;
		int h = 22;
		int x = g.guiWidth() - w - 6;
		int y = bottomY - h;

		for (String id : visibleIds) {
			var row = activeRows.get(id);
			var track = tracked.get(id);
			boolean buff = row != null && row.buffLeft > 0.02f;
			boolean cooling = row != null && row.cooldown > 0.05f;
			int accent;
			float target;
			String rightText;
			if (buff) {
				accent = VIOLET;
				target = Math.clamp(row.buffLeft / Math.max(0.05f, row.buffTotal), 0f, 1f);
				rightText = HudStatePayload.formatShortSeconds(row.buffLeft);
			} else if (cooling) {
				accent = UiTheme.AMBER;
				target = row.total > 0 ? Math.clamp(1.0f - (row.cooldown / row.total), 0f, 1f) : Math.clamp(1.0f - row.cooldown, 0f, 1f);
				rightText = row.total > 0 ? HudStatePayload.formatShortSeconds(row.cooldown) : String.format(Locale.ROOT, "%.0f%%", row.cooldown * 100f);
			} else {
				accent = UiTheme.GREEN;
				target = 1.0f;
				rightText = FlakeI18n.tr("flake.hud.items_ready");
			}
			track.smoothBar += (target - track.smoothBar) * Math.min(1f, dt * 18f);

			UiTheme.panel(g, x, y, w, h);
			if (!buff && !cooling) {
				float flashAlpha = Math.clamp((track.readyUntilNanos - now) / (float) READY_FLASH_NANOS, 0f, 1f);
				g.fill(x + 1, y + 1, x + w - 1, y + h - 1, UiTheme.withAlpha(UiTheme.GREEN, flashAlpha * 0.14f));
				g.outline(x, y, w, h, UiTheme.lerpColor(UiTheme.LINE, UiTheme.GREEN, flashAlpha * 0.85f));
			}
			g.fill(x, y, x + 2, y + h, accent);

			if (!track.stack.isEmpty()) UiIcons.scaledItem(g, track.stack, x + 5, y + 4, 0.75f);

			var font = client.font;
			int timeW = font.width(rightText);
			int textX = x + 21;
			UiTheme.text(g, track.name, textX, y + 5, x + w - 6 - timeW - 4 - textX, UiTheme.TEXT);
			UiTheme.rightText(g, rightText, x + w - 6, y + 5, timeW + 2, accent);
			UiTheme.bar(g, textX, y + h - 5, x + w - 6 - textX, 2, track.smoothBar, accent);
			y -= (h + 3);
		}
		return y + h + 3;
	}

	private static void extractFeedbackToasts(GuiGraphicsExtractor g, Minecraft client, HudStatePayload hud,
			int bottomY, long now, float dt) {
		toasts.removeIf(t -> now - t.bornNanos >= TOAST_LIFE_NANOS);
		if (toasts.isEmpty()) return;

		var font = client.font;
		boolean compact = g.guiWidth() < 420;
		int minW = compact ? 118 : Math.min(148, g.guiWidth() / 2 - 96);
		int maxW = Math.min(204, Math.max(minW, g.guiWidth() / 2 - 24));
		int y = bottomY;

		for (int i = toasts.size() - 1; i >= 0; i--) {
			var toast = toasts.get(i);
			float ageSec = (now - toast.bornNanos) / 1_000_000_000.0f;
			float lifeSec = TOAST_LIFE_NANOS / 1_000_000_000.0f;
			float in = Math.clamp(ageSec / 0.14f, 0f, 1f);
			float out = 1.0f - Math.clamp((ageSec - (lifeSec - 0.45f)) / 0.45f, 0f, 1f);
			float alpha = Math.min(in, out);
			toast.pulse = Math.max(0f, toast.pulse - dt * 3.2f);
			if (alpha <= 0.02f) continue;

			String subject = resolveSubject(toast);
			String reason = resolveReason(toast);
			boolean hasSubject = !subject.isEmpty();
			int h = hasSubject ? 26 : 19;

			int contentW = Math.max(font.width(reason), hasSubject ? font.width(subject) : 0);
			int w = Math.clamp(contentW + 30, minW, maxW);
			int slide = Math.round((1.0f - in) * 14f);
			int x = g.guiWidth() - w - 6 + slide;
			y -= h;
			if (y < 6) break;

			int accent = toneColor(toast.tone);
			int back = UiTheme.withAlpha(UiTheme.BACK, 0.94f * alpha);
			int line = UiTheme.withAlpha(UiTheme.lerpColor(UiTheme.LINE, accent, 0.35f + 0.65f * toast.pulse), alpha);

			g.fill(x + 3, y + 4, x + w + 3, y + h + 4, UiTheme.withAlpha(0x000000, 0.45f * alpha));
			g.fill(x, y, x + w, y + h, back);
			g.outline(x, y, w, h, line);
			g.fill(x + 1, y + 1, x + w - 1, y + 2, UiTheme.withAlpha(accent, 0.55f * alpha));
			g.fill(x, y, x + 2, y + h, UiTheme.withAlpha(accent, alpha));

			int textX = x + 21;
			if ("skill".equals(toast.iconId)) {
				String fighterClass = hud == null ? "NONE" : hud.fighterClass();
				UiIcons.classEmblem(g, x + 6, y + (h - 9) / 2, fighterClass, UiTheme.withAlpha(accent, alpha));
			} else {
				var icon = resolveIcon(toast.iconId);
				if (!icon.isEmpty()) {
					UiIcons.scaledItem(g, icon, x + 5, y + (h - 12) / 2, 0.75f);
				} else {
					g.fill(x + 8, y + h / 2 - 2, x + 13, y + h / 2 + 3, UiTheme.withAlpha(accent, alpha));
					textX = x + 18;
				}
			}

			int maxTextW = x + w - 6 - textX;
			if (hasSubject) {
				UiTheme.text(g, subject, textX, y + 4, maxTextW, UiTheme.withAlpha(UiTheme.TEXT, alpha));
				UiTheme.text(g, reason, textX, y + 14, maxTextW, UiTheme.withAlpha(accent, alpha));
			} else {
				UiTheme.text(g, reason, textX, y + 5, maxTextW, UiTheme.withAlpha(accent, alpha));
			}

			float lifeProgress = Math.clamp(1.0f - ageSec / lifeSec, 0f, 1f);
			int barW = x + w - 6 - textX;
			g.fill(textX, y + h - 3, textX + barW, y + h - 2, UiTheme.withAlpha(0x060608, alpha));
			int filled = Math.round(barW * lifeProgress);
			if (filled > 0) g.fill(textX, y + h - 3, textX + filled, y + h - 2, UiTheme.withAlpha(accent, 0.9f * alpha));

			y -= 4;
		}
	}

	private static String resolveSubject(Toast toast) {
		if (!toast.subjectKey.isEmpty()) {
			String tr = FlakeI18n.tr(toast.subjectKey);
			return tr;
		}
		if (!toast.iconId.isEmpty() && !"skill".equals(toast.iconId)) {
			var icon = resolveIcon(toast.iconId);
			if (!icon.isEmpty()) return icon.getHoverName().getString();
		}
		return "";
	}

	private static String resolveReason(Toast toast) {
		if (toast.reasonKey.isEmpty()) return "";
		return toast.arg.isEmpty() ? FlakeI18n.tr(toast.reasonKey) : FlakeI18n.tr(toast.reasonKey, toast.arg);
	}

	private static int toneColor(byte tone) {
		return switch (tone) {
			case CombatFeedbackPayload.TONE_BLOCKED -> UiTheme.RED;
			case CombatFeedbackPayload.TONE_COOLDOWN -> UiTheme.AMBER;
			case CombatFeedbackPayload.TONE_WARN -> WARN;
			default -> UiTheme.CYAN;
		};
	}

	private static ItemStack resolveIcon(String itemId) {
		if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
		if (itemIconCache.size() >= 64) itemIconCache.clear();
		return itemIconCache.computeIfAbsent(itemId, key -> {
			try {
				return BuiltInRegistries.ITEM.getOptional(Identifier.parse(key))
						.map(ItemStack::new).orElse(ItemStack.EMPTY);
			} catch (RuntimeException ignored) {
				return ItemStack.EMPTY;
			}
		});
	}

	/** Hotbar slot label from the live key mapping; falls back to the slot number only if unmapped. */
	private static String hotbarKeyLabel(Minecraft client, int index) {
		var keys = client.options.keyHotbarSlots;
		if (keys == null || index >= keys.length || keys[index] == null) return Integer.toString(index + 1);
		String label = keys[index].getTranslatedKeyMessage().getString();
		return label.isEmpty() ? Integer.toString(index + 1) : label;
	}

	private static String offhandKeyLabel(Minecraft client) {
		var key = client.options.keySwapOffhand;
		if (key == null) return "F";
		String label = key.getTranslatedKeyMessage().getString();
		return label.isEmpty() ? "F" : label;
	}

	private static String itemId(ItemStack stack) {
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key == null ? "" : key.toString();
	}

	/** True when the server marked this stack as "next hit crits" (Void Paladin charge). */
	private static boolean critReady(ItemStack stack) {
		if (stack.isEmpty()) return false;
		var data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr("flake_crit_ready", false);
	}
}
