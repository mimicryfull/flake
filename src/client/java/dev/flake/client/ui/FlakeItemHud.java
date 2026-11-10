package dev.flake.client.ui;

import dev.flake.duel.ControlHammer;
import dev.flake.duel.CrystalClaw;
import dev.flake.duel.DarkCube;
import dev.flake.duel.Gadgets;
import dev.flake.duel.Matadora;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Styled hotbar (drawn inside the PvP health panel) with per-slot cooldown / buff timers and a short "item used" feed.
 * Everything comes from the synced inventory and cooldown state.
 */
public final class FlakeItemHud {
	private static final int VIOLET = 0xffb37aff;
	private static final Item MATADORA_ITEM = Matadora.stack().getItem();
	private static final Item CRYSTAL_CLAW_ITEM = CrystalClaw.stack().getItem();
	private static final List<Feed> feed = new ArrayList<>();
	private static final Map<Integer, Seen> seen = new HashMap<>();

	private FlakeItemHud() {}

	private record Row(int slot, ItemStack stack, String name, float cooldown, float total, float buffLeft, float buffTotal) {}
	private record Feed(String text, long born) {}
	private static final class Seen { String key = ""; int count; float cooldown; }

	public static void reset() {
		seen.clear();
		feed.clear();
	}

	private static Row row(Player player, int slot, ItemStack stack, float partial) {
		float percent = stack.isEmpty() ? 0 : player.getCooldowns().getCooldownPercent(stack, partial);
		var hud = FlakeUiClient.hud();
		// Prefer the server-synced total (includes passive modifiers); fall back to the item spec.
		float total = dev.flake.shared.SliceRegistry.cooldownSeconds(stack);
		if (!stack.isEmpty()) {
			int synced = hud == null ? 0 : hud.itemCooldownTotal(itemId(stack));
			if (synced > 0) total = synced / 20f;
		}
		// Cooldown and buff are independent server states: a buff can end early (stun) while cooldown runs.
		float buffTotal = 0, buffLeft = 0;
		String statusId = buffStatusId(stack);
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

	/** Tracks item use (cooldown start / stack shrink) and draws the short feed on the right. */
	public static void extract(GuiGraphicsExtractor g, Minecraft client, float timeSec) {
		var player = client.player;
		if (player == null) return;
		long now = System.nanoTime();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		for (int slot = 0; slot <= 9; slot++) {
			ItemStack stack = slot == 9 ? player.getOffhandItem() : player.getInventory().getItem(slot);
			if (stack.isEmpty() || stack.is(Items.ARROW)) continue;
			var row = row(player, slot, stack, partial);
			var s = seen.computeIfAbsent(slot, k -> new Seen());
			String key = stack.getItem().toString();
			boolean same = key.equals(s.key);
			if (same && (s.cooldown <= 0.05f && row.cooldown > 0.5f || stack.getCount() < s.count)) {
				feed.add(new Feed(row.name, now));
				if (feed.size() > 5) feed.removeFirst();
			}
			s.key = key; s.count = stack.getCount(); s.cooldown = row.cooldown;
		}
		feed.removeIf(f -> (now - f.born) / 1_000_000_000.0 > 3.5);
		int fy = g.guiHeight() / 2 - 20;
		for (int i = feed.size() - 1; i >= 0; i--) {
			var f = feed.get(i);
			float age = (now - f.born) / 1_000_000_000.0f;
			float alpha = 1 - Math.clamp((age - 2.6f) / 0.9f, 0, 1);
			UiTheme.rightText(g, "▸ " + f.text, g.guiWidth() - 6, fy, 150, UiTheme.withAlpha(UiTheme.TEXT, alpha));
			fy += 11;
		}
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

	/** Maps a stack to the server status that represents its timed buff, if any. */
	private static String buffStatusId(ItemStack stack) {
		if (stack.isEmpty()) return null;
		if (stack.is(MATADORA_ITEM)) return "matadora";
		if (stack.is(CRYSTAL_CLAW_ITEM)) return "crystal_claw";
		return null;
	}

	private static String itemId(ItemStack stack) {
		var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key == null ? "" : key.toString();
	}

	/** True when the server marked this stack as "next hit crits" (Void Paladin charge). */
	private static boolean critReady(ItemStack stack) {
		if (stack.isEmpty()) return false;
		var data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr("flake_crit_ready", false);
	}
}
