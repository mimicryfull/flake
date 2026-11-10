package dev.flake.client.ui;

import dev.flake.client.FlakeClient;
import dev.flake.ability.AbilitySlots;
import dev.flake.ui.HudStatePayload;
import dev.flake.ui.HudStatePayload.SkillState;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

public final class FlakeHudOverlay {
	private static long lastFrameNano;
	private static final float[] skillBars = {1f, 1f};
	private static final float[] readyFlashes = new float[2];
	private static final SkillState[] previousStates = {SkillState.PASSIVE, SkillState.PASSIVE};
	private static String skillClass = "";

	private static String lastBuildSig = "";
	private static float buildPulse;

	private static float topBarSlide;
	private static float smoothHpLeft = 1f, trailHpLeft = 1f;
	private static float smoothHpRight = 1f, trailHpRight = 1f;
	private static int lastCountdownWhole = -1;
	private static float countdownPulse;

	private FlakeHudOverlay() {}

	public static void extract(GuiGraphicsExtractor g) {
		var client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null || !FlakeUiClient.hudFresh()) return;
		var screen = client.gui.screen();
		if (screen != null && !(screen instanceof ChatScreen)) return;
		var hud = FlakeUiClient.hud();
		if (hud == null) return;

		long now = System.nanoTime();
		float dt = lastFrameNano == 0 ? 0.016f : Math.min(0.1f, Math.max(0.001f, (now - lastFrameNano) / 1_000_000_000.0f));
		lastFrameNano = now;
		float timeSec = (now / 1_000_000L % 100_000L) / 1000.0f;
		float elapsed = FlakeUiClient.hudElapsedSeconds();

		if (!client.options.keyPlayerList.isDown()) {
			extractDuelBar(g, hud, elapsed, dt, timeSec);
		}
		extractBuildHud(g, client, hud, dt);
		StatusHud.extract(g, client, hud, dt);
		// Spectators and free-roam players must not see castable ability cards for a fight they are not in.
		if (hud.participant()) extractSkillHud(g, client, hud, elapsed, dt, timeSec);
		FlakePlayerHud.extract(g, client);
		if (hud.participant() && ("COUNTDOWN".equals(hud.phase()) || "FIGHTING".equals(hud.phase()))) FlakeItemHud.extract(g, client, timeSec);
	}

	private static void extractDuelBar(GuiGraphicsExtractor g, HudStatePayload hud, float elapsed, float dt, float timeSec) {
		String phase = hud.phase();
		boolean active = !"PREPARING".equals(phase) || !hud.lobbyTitle().isEmpty();
		topBarSlide += ((active ? 1f : 0f) - topBarSlide) * Math.min(1f, dt * 12f);
		if (topBarSlide < 0.02f) return;

		if ("PREPARING".equals(phase)) {
			int w = Math.min(220, g.guiWidth() - 16);
			int h = 22;
			int x = (g.guiWidth() - w) / 2;
			int y = Math.round(-h + (h + 6) * topBarSlide);
			UiTheme.panel(g, x, y, w, h);
			UiIcons.classEmblem(g, x + 7, y + 6, hud.fighterClass(), UiTheme.CYAN);
			UiTheme.text(g, hud.lobbyTitle() + " · " + hud.lobbyCount() + "/2", x + 20, y + 7, w - 98, UiTheme.TEXT);
			String badge = !hud.inLobby() ? dev.flake.shared.FlakeI18n.tr("flake.hud.badge.free")
					: hud.ready() ? dev.flake.shared.FlakeI18n.tr("flake.hud.badge.ready")
					: dev.flake.shared.FlakeI18n.tr("flake.hud.badge.waiting");
			int color = !hud.inLobby() ? UiTheme.MUTED : hud.ready() ? UiTheme.GREEN : UiTheme.CYAN;
			UiTheme.rightText(g, badge, x + w - 8, y + 7, 76, color);
			return;
		}

		int w = Math.min(344, g.guiWidth() - 16);
		int h = 42;
		int x = (g.guiWidth() - w) / 2;
		int y = Math.round(-h + (h + 6) * topBarSlide);
		UiTheme.panel(g, x, y, w, h);

		int centerW = 88;
		int centerX = x + (w - centerW) / 2;
		int phaseColor = switch (phase) {
			case "COUNTDOWN" -> UiTheme.CYAN;
			case "FIGHTING" -> UiTheme.GREEN;
			case "RESTORING" -> UiTheme.AMBER;
			case "CANCELLED" -> UiTheme.RED;
			default -> UiTheme.CYAN;
		};

		countdownPulse = Math.max(0f, countdownPulse - dt * 3.0f);
		String timerText;
		float barProgress;
		if ("COUNTDOWN".equals(phase)) {
			float sec = Math.max(0f, hud.timerTicks() / 20.0f - elapsed);
			int whole = (int) Math.ceil(sec);
			if (whole != lastCountdownWhole) {
				lastCountdownWhole = whole;
				countdownPulse = 1f;
			}
			float totalSec = Math.max(0.05f, hud.timerTotalTicks() / 20.0f);
			timerText = HudStatePayload.formatShortSeconds(sec);
			barProgress = Math.clamp(sec / totalSec, 0f, 1f);
		} else {
			lastCountdownWhole = -1;
			float sec = "FIGHTING".equals(phase)
					? Math.max(0f, hud.timerTicks() / 20.0f + elapsed)
					: Math.max(0f, hud.timerTicks() / 20.0f);
			timerText = HudStatePayload.formatRoundTimer(sec);
			barProgress = "FIGHTING".equals(phase) ? 0.5f + 0.5f * (float) Math.sin(timeSec * 3.5f) : 1.0f;
		}

		int centerBorder = UiTheme.lerpColor(phaseColor, UiTheme.TEXT, countdownPulse * 0.7f);
		g.fill(centerX, y + 5, centerX + centerW, y + h - 5, UiTheme.CARD);
		g.outline(centerX, y + 5, centerW, h - 10, centerBorder);
		if (countdownPulse > 0.02f) {
			g.fill(centerX + 1, y + 6, centerX + centerW - 1, y + h - 6, UiTheme.withAlpha(UiTheme.CYAN, countdownPulse * 0.14f));
		}

		String scoreText = hud.firstWins() + " : " + hud.secondWins();
		UiTheme.centeredText(g, scoreText, centerX + centerW / 2, y + 8, centerW - 8, phaseColor);
		int timerW = Minecraft.getInstance().font.width(timerText);
		int timerStartX = centerX + (centerW - (timerW + 10)) / 2;
		UiIcons.clock(g, timerStartX, y + 19, phaseColor, timeSec);
		UiTheme.text(g, timerText, timerStartX + 10, y + 19, centerW - 14, UiTheme.TEXT);
		UiTheme.bar(g, centerX + 5, y + h - 9, centerW - 10, 2, barProgress, phaseColor);

		int fighterW = (w - centerW - 18) / 2;
		if (fighterW >= 58) {
			smoothHpLeft = updateHp(smoothHpLeft, hud.first(), dt * 12f);
			trailHpLeft = updateTrail(trailHpLeft, smoothHpLeft, dt * 2.6f);
			smoothHpRight = updateHp(smoothHpRight, hud.second(), dt * 12f);
			trailHpRight = updateTrail(trailHpRight, smoothHpRight, dt * 2.6f);

			fighterCard(g, x + 5, y + 5, fighterW, h - 10, hud.first(), smoothHpLeft, trailHpLeft, true);
			fighterCard(g, centerX + centerW + 4, y + 5, fighterW, h - 10, hud.second(), smoothHpRight, trailHpRight, false);
		}
	}

	private static float updateHp(float current, HudStatePayload.Fighter fighter, float speed) {
		float target = fighter.name().isEmpty() ? 1f : Math.clamp(fighter.health() / Math.max(1f, fighter.maxHealth()), 0f, 1f);
		return current + (target - current) * Math.min(1f, speed);
	}

	private static float updateTrail(float trail, float current, float speed) {
		if (trail < current) return current;
		return Math.max(current, trail - speed * 0.35f);
	}

	private static void fighterCard(GuiGraphicsExtractor g, int cx, int cy, int cw, int ch,
			HudStatePayload.Fighter fighter, float hpRatio, float trailRatio, boolean left) {
		g.fill(cx, cy, cx + cw, cy + ch, UiTheme.CARD);
		g.outline(cx, cy, cw, ch, UiTheme.LINE);
		int stripeColor = fighter.name().isEmpty() ? UiTheme.LINE : fighter.self() ? UiTheme.CYAN : UiTheme.RED;
		if (left) g.fill(cx, cy, cx + 2, cy + ch, stripeColor);
		else g.fill(cx + cw - 2, cy, cx + cw, cy + ch, stripeColor);

		if (fighter.name().isEmpty()) {
			UiTheme.text(g, dev.flake.shared.FlakeI18n.tr("flake.hud.waiting_dots"), cx + 6, cy + 11, cw - 12, UiTheme.MUTED);
			return;
		}

		int hpColor = hpRatio > 0.5f ? UiTheme.GREEN : hpRatio > 0.25f ? UiTheme.AMBER : UiTheme.RED;
		String hpText = String.format(Locale.ROOT, "%.0f", Math.ceil(fighter.health()));
		int hpTextW = Minecraft.getInstance().font.width(hpText);

		UiTheme.text(g, fighter.name(), cx + 6, cy + 4, cw - hpTextW - 18, UiTheme.TEXT);
		UiIcons.heart(g, cx + cw - hpTextW - 14, cy + 5, hpColor);
		UiTheme.rightText(g, hpText, cx + cw - 5, cy + 4, 24, hpColor);

		UiIcons.classEmblem(g, cx + 5, cy + 13, fighter.fighterClass(), stripeColor);
		UiTheme.text(g, UiTheme.className(fighter.fighterClass()), cx + 17, cy + 14, cw - 22, UiTheme.MUTED);

		int barX = cx + 6, barY = cy + ch - 5, barW = cw - 12;
		g.fill(barX, barY, barX + barW, barY + 2, 0xff060608);
		int trailW = Math.round(barW * Math.clamp(trailRatio, 0f, 1f));
		if (trailW > 0) g.fill(barX, barY, barX + trailW, barY + 2, 0xff8a2c2c);
		int hpW = Math.round(barW * Math.clamp(hpRatio, 0f, 1f));
		if (hpW > 0) g.fill(barX, barY, barX + hpW, barY + 2, hpColor);
	}

	private static void extractBuildHud(GuiGraphicsExtractor g, Minecraft client, HudStatePayload hud, float dt) {
		boolean chatOpen = client.gui.screen() instanceof ChatScreen;
		if (chatOpen) return;

		var build = hud.toLoadout();
		var stats = build.stats();
		String sig = hud.fighterClass() + "|" + hud.helmet() + "|" + hud.chestplate() + "|"
				+ hud.leggings() + "|" + hud.boots() + "|" + hud.weapon() + "|"
				+ hud.passiveItem() + "|" + hud.activeItem1() + "|" + hud.activeItem2();
		if (!sig.equals(lastBuildSig)) {
			if (!lastBuildSig.isEmpty()) buildPulse = 1f;
			lastBuildSig = sig;
		}
		buildPulse = Math.max(0f, buildPulse - dt * 2.2f);

		String classLine = UiTheme.className(hud.fighterClass()) + " · " + stats.armorPoints() + " " + dev.flake.shared.FlakeI18n.tr("flake.hud.armor_abbr");
		String gearLine = UiTheme.weaponName(hud.weapon()) + " · " + hud.cost() + "/" + hud.budget();

		var font = client.font;
		boolean compact = g.guiWidth() < 420;
		int maxAllowed = compact ? 114 : Math.min(156, g.guiWidth() / 2 - 100);
		if (maxAllowed < 90) return;
		int contentW = Math.max(font.width(classLine) + 16, font.width(gearLine) + 28) + 12;
		int w = Math.clamp(contentW, 98, maxAllowed);
		int h = 30;
		int slideOffset = Math.round(-8f * buildPulse);
		int x = 6 - slideOffset;
		int y = g.guiHeight() - h - (compact ? 25 : 6);

		UiTheme.panel(g, x, y, w, h);
		int accent = UiTheme.lerpColor(UiTheme.CYAN, UiTheme.TEXT, buildPulse);
		g.fill(x, y, x + 2, y + h, accent);
		if (buildPulse > 0.02f) {
			g.fill(x + 2, y + 1, x + w - 1, y + h - 1, UiTheme.withAlpha(UiTheme.CYAN, buildPulse * 0.14f));
		}

		UiIcons.classEmblem(g, x + 6, y + 4, hud.fighterClass(), UiTheme.CYAN);
		UiTheme.text(g, classLine, x + 18, y + 5, w - 24, UiTheme.TEXT);

		UiIcons.scaledItem(g, UiIcons.armorPieceItem(dev.flake.build.Loadout.ArmorSlot.CHESTPLATE, build.chestplate()), x + 5, y + 15, 0.65f);
		UiIcons.scaledItem(g, UiIcons.weaponItem(hud.weapon()), x + 16, y + 15, 0.65f);
		UiTheme.text(g, gearLine, x + 29, y + 17, w - 34, hud.validBuild() ? UiTheme.MUTED : UiTheme.RED);
	}

	private static void extractSkillHud(GuiGraphicsExtractor g, Minecraft client, HudStatePayload hud, float elapsed, float dt, float timeSec) {
		boolean chatOpen = client.gui.screen() instanceof ChatScreen;
		boolean compact = g.guiWidth() < 420;
		int w = compact ? 118 : Math.min(148, g.guiWidth() / 2 - 96);
		if (w < 92) return;
		int h = 34;
		int x = g.guiWidth() - w - 6;
		int baseY = g.guiHeight() - h - (compact ? 25 : (chatOpen ? 18 : 6));

		boolean changed = !skillClass.equals(hud.fighterClass());
		skillClass = hud.fighterClass();
		for (int slot = 1; slot <= AbilitySlots.COUNT; slot++) {
			int i = slot - 1;
			var ability = AbilitySlots.resolve(hud.fighterClass(), slot);
			float cooldown = Math.max(0f, hud.slotCooldownRemaining(slot) / 20f - elapsed);
			int activeTicks = hud.slotActiveElapsed(slot);
			float activeTotal = Math.max(0.05f, hud.slotActiveTotal(slot) / 20f);
			float active = activeTicks < 0 ? -1 : Math.min(activeTotal, activeTicks / 20f + elapsed);
			var state = HudStatePayload.skillState(hud.fighterClass(), slot, hud.abilityBlocked(), activeTicks, cooldown);
			if (changed) { skillBars[i] = state == SkillState.READY ? 1f : 0f; readyFlashes[i] = 0f; }
			else if (previousStates[i] == SkillState.COOLDOWN && state == SkillState.READY) readyFlashes[i] = 1f;
			previousStates[i] = state;
			readyFlashes[i] = Math.max(0f, readyFlashes[i] - dt * 2.4f);
			String icon = ability == AbilitySlots.Ability.ELECTRIC_QUAKE ? "CONCUSSED_QUAKE" : hud.fighterClass();
			skillBars[i] = drawSkillCard(g, client, x, baseY - i * (h + 5), w, h,
					icon, AbilitySlots.title(hud.fighterClass(), slot), FlakeClient.abilityKeyLabel(slot),
					state, cooldown, Math.max(0.05f, hud.slotCooldownTotal(slot) / 20f), active, activeTotal,
					hud.blockReason(), skillBars[i], readyFlashes[i], slot == 1 ? UiTheme.CYAN : 0xff689dff, dt, timeSec);
		}
	}

	private static float drawSkillCard(
			GuiGraphicsExtractor g, Minecraft client, int x, int y, int w, int h,
			String iconId, String title, String keyLabel,
			SkillState state, float cooldownSec, float cooldownTotalSec, float activeElapsedSec, float activeTotalSec,
			String blockReason, float smoothBar, float flash, int activeThemeColor, float dt, float timeSec
	) {
		boolean isQuake = "CONCUSSED_QUAKE".equals(iconId);
		int accentColor;
		float targetProgress;
		String statusText;

		switch (state) {
			case CHARGING -> {
				accentColor = isQuake ? 0xff64f5ff : activeThemeColor;
				targetProgress = Math.clamp(activeElapsedSec / activeTotalSec, 0f, 1f);
				float left = Math.max(0f, activeTotalSec - activeElapsedSec);
				statusText = isQuake ? dev.flake.shared.FlakeI18n.tr("flake.hud.quake_rift") + " · " + HudStatePayload.formatShortSeconds(left) : HudStatePayload.formatShortSeconds(left);
			}
			case COOLDOWN -> {
				accentColor = UiTheme.AMBER;
				targetProgress = Math.clamp(1.0f - (cooldownSec / cooldownTotalSec), 0f, 1f);
				statusText = HudStatePayload.formatShortSeconds(cooldownSec);
			}
			case READY -> {
				accentColor = isQuake ? 0xff3898ff : UiTheme.GREEN;
				targetProgress = 1.0f;
				statusText = isQuake ? dev.flake.shared.FlakeI18n.tr("flake.hud.quake_ready") : dev.flake.shared.FlakeI18n.tr("flake.hud.skill_ready");
			}
			case BLOCKED -> {
				accentColor = UiTheme.RED;
				targetProgress = 0.0f;
				statusText = blockText(blockReason);
			}
			default -> {
				accentColor = UiTheme.MUTED;
				targetProgress = 0.0f;
				statusText = dev.flake.shared.FlakeI18n.tr("flake.hud.skill_soon");
			}
		}

		smoothBar += (targetProgress - smoothBar) * Math.min(1f, dt * 18f);

		UiTheme.panel(g, x, y, w, h);

		float pulse = state == SkillState.CHARGING ? 0.5f + 0.5f * (float) Math.sin(timeSec * 14f) : flash;
		if (pulse > 0.02f) {
			g.fill(x + 1, y + 1, x + w - 1, y + h - 1, UiTheme.withAlpha(accentColor, pulse * 0.16f));
			g.outline(x, y, w, h, UiTheme.lerpColor(UiTheme.LINE, accentColor, pulse * 0.85f));
		}
		g.fill(x, y, x + 2, y + h, accentColor);

		UiIcons.skillBox(g, x + 6, y + 6, iconId, accentColor, state == SkillState.CHARGING, smoothBar, timeSec);

		int textX = x + 32;
		var font = client.font;
		int keyW = Math.max(14, font.width(keyLabel) + 6);
		int keyX = x + w - keyW - 5;
		int keyY = y + 5;
		g.fill(keyX, keyY, keyX + keyW, keyY + 11, UiTheme.CARD_ALT);
		g.outline(keyX, keyY, keyW, 11, state == SkillState.READY || state == SkillState.CHARGING ? accentColor : UiTheme.LINE);
		UiTheme.centeredText(g, keyLabel, keyX + keyW / 2, keyY + 2, keyW - 2,
				state == SkillState.READY || state == SkillState.CHARGING ? UiTheme.TEXT : UiTheme.MUTED);

		UiTheme.text(g, title, textX, y + 6, keyX - textX - 4, UiTheme.TEXT);
		UiTheme.text(g, statusText, textX, y + 17, w - 38, accentColor);

		int barX = textX, barY = y + h - 6, barW = x + w - 6 - textX;
		UiTheme.bar(g, barX, barY, barW, 2, smoothBar, accentColor);
		if (state == SkillState.CHARGING && barW > 8) {
			int shimmerW = Math.max(6, barW / 4);
			int shimmerX = barX + Math.round((barW - shimmerW) * ((timeSec * 2.2f) % 1.0f));
			g.fill(shimmerX, barY, shimmerX + shimmerW, barY + 2, UiTheme.TEXT);
		}
		return smoothBar;
	}

	/** Exact reason instead of a vague "blocked": stun, countdown, death, locked inventory. */
	private static String blockText(String reason) {
		String key = switch (reason) {
			case "stun" -> "flake.hud.block.stun";
			case "countdown" -> "flake.hud.block.countdown";
			case "dead" -> "flake.hud.block.dead";
			case "inventory" -> "flake.hud.block.inventory";
			default -> "flake.hud.skill_blocked";
		};
		return dev.flake.shared.FlakeI18n.tr(key);
	}
}
