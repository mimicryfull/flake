package dev.flake.features.lobby.client;

import dev.flake.features.loadout.client.BuildEditorScreen;
import dev.flake.client.network.FlakeUiClient;
import dev.flake.client.ui.widgets.UiButton;
import dev.flake.client.ui.UiIcons;
import dev.flake.client.ui.UiTheme;
import dev.flake.text.FlakeI18n;
import dev.flake.features.lobby.LobbySnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Room browser, room screen and create screen for independent player lobbies. */
public final class FlakeScreen extends Screen {
	public enum ScreenView { MAIN_MENU, BROWSER, CREATE_LOBBY, ROOM, BUILD_EDITOR, ARENA_SETTINGS }

	private static ScreenView view = ScreenView.MAIN_MENU;
	private static String lobbyName = "", mode = "DUEL", visibility = "PUBLIC", arena = "default", arenaTarget = "default";
	private static int teamCount = 2, teamSize = 1;
	private int x, y, w, h;
	private EditBox nameBox;
	private String signature = "";

	public FlakeScreen() { super(Component.literal("Flake")); }
	public static void navigateTo(ScreenView target) { view = target; }

	private void go(ScreenView target) {
		if (target == ScreenView.BUILD_EDITOR) minecraft.gui.setScreen(new BuildEditorScreen(this));
		else { view = target; rebuildWidgets(); }
	}

	@Override protected void init() {
		if (nameBox != null) { lobbyName = nameBox.getValue(); nameBox = null; }
		if (lobbyName.isEmpty()) lobbyName = FlakeI18n.tr("flake.screen.default_lobby_name");
		w = Math.min(560, width - 16); h = Math.min(350, height - 16);
		x = (width - w) / 2; y = (height - h) / 2;
		FlakeUiClient.query();
		signature = signature();
		if (view == ScreenView.BUILD_EDITOR) { view = ScreenView.MAIN_MENU; go(ScreenView.BUILD_EDITOR); return; }
		int navW = Math.min(82, (w - 92) / 4);
		button(x + w - navW - 8, y + 8, navW, 20, FlakeI18n.tr("flake.screen.nav.close"), this::onClose);
		button(x + w - navW * 2 - 12, y + 8, navW, 20, FlakeI18n.tr("flake.screen.nav.settings"), () -> go(ScreenView.ARENA_SETTINGS));
		button(x + w - navW * 3 - 16, y + 8, navW, 20, FlakeI18n.tr("flake.screen.nav.gear"), () -> go(ScreenView.BUILD_EDITOR));
		button(x + w - navW * 4 - 20, y + 8, navW, 20, FlakeI18n.tr("flake.screen.nav.lobby"), () -> go(FlakeUiClient.snapshot() != null && FlakeUiClient.snapshot().inRoom() ? ScreenView.ROOM : ScreenView.BROWSER));
		switch (view) {
			case MAIN_MENU -> initMenu();
			case BROWSER -> initBrowser();
			case CREATE_LOBBY -> initCreate();
			case ROOM -> initRoom();
			case ARENA_SETTINGS -> initSettings();
			default -> { }
		}
	}

	private void initMenu() {
		int bw = Math.min(240, w - 32), bx = x + (w - bw) / 2, step = 30;
		int by = y + h / 2 - step;
		button(bx, by, bw, 26, FlakeI18n.tr("flake.screen.menu.create"), () -> go(ScreenView.CREATE_LOBBY));
		button(bx, by + step, bw, 26, FlakeI18n.tr("flake.screen.menu.browse"), () -> go(ScreenView.BROWSER));
		button(bx, by + step * 2, bw, 26, FlakeI18n.tr("flake.screen.menu.editor"), () -> go(ScreenView.BUILD_EDITOR));
		var snapshot = FlakeUiClient.snapshot();
		if (snapshot != null && snapshot.inRoom()) button(bx, by + step * 3, bw, 26, FlakeI18n.tr("flake.screen.menu.my_room", snapshot.title()), () -> go(ScreenView.ROOM));
	}

	private void initBrowser() {
		var snapshot = FlakeUiClient.snapshot();
		var rooms = snapshot == null ? List.<LobbySnapshotPayload.RoomSummary>of() : snapshot.rooms();
		int rowH = 32, top = y + 76;
		int maxRows = Math.max(1, (h - 118) / rowH);
		int shown = Math.min(rooms.size(), maxRows);
		for (int i = 0; i < shown; i++) {
			var room = rooms.get(i);
			int rowY = top + i * rowH;
			button(x + w - 116, rowY + 6, 100, 20,
					room.mine() ? FlakeI18n.tr("flake.screen.btn.open") : room.joinable() ? FlakeI18n.tr("flake.screen.btn.join") : FlakeI18n.tr("flake.screen.btn.locked"),
					() -> {
						if (room.mine()) go(ScreenView.ROOM);
						else if (room.joinable()) { FlakeUiClient.join(room.id()); go(ScreenView.ROOM); }
					}).active = room.mine() || room.joinable();
		}
		button(x + 16, y + h - 32, 120, 22, FlakeI18n.tr("flake.screen.btn.refresh"), FlakeUiClient::query);
		button(x + 144, y + h - 32, 140, 22, FlakeI18n.tr("flake.screen.btn.create_new"), () -> go(ScreenView.CREATE_LOBBY));
		var selfRoom = snapshot == null ? "" : snapshot.roomId();
		if (!selfRoom.isEmpty()) button(x + w - 148, y + h - 32, 132, 22, FlakeI18n.tr("flake.screen.btn.back_to_room"), () -> go(ScreenView.ROOM));
	}

	private void initCreate() {
		int cw = Math.min(360, w - 32), bx = x + (w - cw) / 2;
		nameBox = addRenderableWidget(new EditBox(font, bx, y + 58, cw, 20, Component.literal(FlakeI18n.tr("flake.screen.lobby_name_narrator"))));
		nameBox.setMaxLength(32); nameBox.setValue(lobbyName);

		int half = (cw - 6) / 2;
		for (int i = 0; i < 2; i++) {
			String value = i == 0 ? "DUEL" : "TEAM_FIGHT";
			button(bx + i * (half + 6), y + 100, half, 22, (mode.equals(value) ? "✓ " : "") + UiTheme.modeName(value),
					() -> { mode = value; rebuildWidgets(); });
		}
		for (int i = 0; i < 2; i++) {
			String value = i == 0 ? "PUBLIC" : "PRIVATE";
			button(bx + i * (half + 6), y + 142, half, 22,
					(visibility.equals(value) ? "✓ " : "") + FlakeI18n.tr("flake.screen.visibility." + value.toLowerCase()),
					() -> { visibility = value; rebuildWidgets(); });
		}
		int arenas = Math.max(1, arenaChoices().size());
		button(bx, y + 184, cw, 22, FlakeI18n.tr("flake.screen.arena_cycle", arenaLabel(), arenas),
				() -> { arena = nextArena(); rebuildWidgets(); });

		int countW = (cw - 12) / 3;
		for (int i = 2; i <= 4; i++) {
			int value = i;
			var widget = button(bx + (i - 2) * (countW + 6), y + 226, countW, 22,
					(teamCount == i ? "✓ " : "") + FlakeI18n.tr("flake.screen.teams_count", i), () -> { teamCount = value; rebuildWidgets(); });
			widget.active = !mode.equals("DUEL");
		}
		int sizeW = (cw - 16) / 5;
		for (int i = 1; i <= 5; i++) {
			int value = i;
			var widget = button(bx + (i - 1) * (sizeW + 4), y + 264, sizeW, 22,
					(teamSize == i ? "✓ " : "") + FlakeI18n.tr("flake.screen.team_size", i), () -> { teamSize = value; rebuildWidgets(); });
			widget.active = !mode.equals("DUEL");
		}
		button(bx, y + h - 40, cw, 26, FlakeI18n.tr("flake.screen.btn.create_go"), () -> {
			String name = nameBox.getValue().isBlank() ? FlakeI18n.tr("flake.screen.default_lobby_name") : nameBox.getValue().strip();
			lobbyName = name;
			FlakeUiClient.createLobby(name, mode, visibility, arena, teamCount, teamSize);
			go(ScreenView.ROOM);
		});
	}

	private void initRoom() {
		var snapshot = FlakeUiClient.snapshot();
		if (snapshot == null || !snapshot.inRoom()) {
			int bw = Math.min(240, w - 32);
			button(x + (w - bw) / 2, y + h / 2, bw, 26, FlakeI18n.tr("flake.screen.btn.browse"), () -> go(ScreenView.BROWSER));
			return;
		}
		boolean member = snapshot.viewerIsMember(), ready = snapshot.viewerReady(), owner = snapshot.viewerIsOwner();
		boolean inMatch = !"PREPARING".equals(snapshot.phase());
		int count = Math.clamp(snapshot.teamCount(), 2, 4), gap = 8;
		int colW = (w - 32 - gap * (count - 1)) / count;
		for (int i = 0; i < count; i++) {
			int team = i;
			var self = FlakeUiClient.self();
			boolean own = member && self != null && self.team() == team;
			long size = snapshot.players().stream().filter(p -> p.member() && p.team() == team).count();
			var join = button(x + 16 + i * (colW + gap) + 4, y + h - 100, colW - 8, 20,
					own ? FlakeI18n.tr("flake.screen.your_team") : FlakeI18n.tr("flake.screen.team_num", i + 1),
					() -> FlakeUiClient.switchTeam(team));
			join.active = member && !inMatch && !own && size < snapshot.teamSize();
		}

		int bw = (w - 40) / 2;
		if (!member) {
			button(x + 16, y + h - 36, bw, 24, FlakeI18n.tr("flake.screen.btn.join"), () -> FlakeUiClient.join(snapshot.roomId()))
					.active = true;
			button(x + 24 + bw, y + h - 36, bw, 24, FlakeI18n.tr("flake.screen.btn.refresh"), FlakeUiClient::query);
			return;
		}
		var primary = button(x + 16, y + h - 68, bw, 24,
				ready ? FlakeI18n.tr("flake.screen.btn.unready") : FlakeI18n.tr("flake.screen.btn.ready_check"),
				() -> FlakeUiClient.ready(!ready));
		primary.active = !inMatch && (ready || Boolean.TRUE.equals(FlakeUiClient.self() == null ? null : FlakeUiClient.self().validBuild()));
		button(x + 24 + bw, y + h - 68, bw, 24, FlakeI18n.tr("flake.screen.btn.gear_go"), () -> go(ScreenView.BUILD_EDITOR)).active = !inMatch && !ready;
		button(x + 16, y + h - 36, bw, 24, FlakeI18n.tr("flake.screen.btn.leave"), () -> { FlakeUiClient.leave(); go(ScreenView.BROWSER); });
		if (owner) {
			button(x + 24 + bw, y + h - 36, bw, 24,
					inMatch ? FlakeI18n.tr("flake.screen.btn.stop_match") : FlakeI18n.tr("flake.screen.btn.start"),
					() -> { if (inMatch) FlakeUiClient.stopMatch(); else FlakeUiClient.startMatch(); })
					.active = inMatch || snapshot.players().stream().filter(LobbySnapshotPayload.Player::member).count() >= 2;
		} else {
			button(x + 24 + bw, y + h - 36, bw, 24, FlakeI18n.tr("flake.screen.btn.refresh"), FlakeUiClient::query);
		}
		// Owner tools live in a dedicated screen: full member/invite lists with paging.
		if (owner && !inMatch) {
			button(x + w - 108, y + 44, 92, 18, FlakeI18n.tr("flake.screen.btn.manage"), () -> minecraft.gui.setScreen(new LobbyManageScreen(this)));
		}
	}

	private void initSettings() {
		int bx = x + 16, bw = (w - 40) / 2, row = 0;
		button(bx, y + 40, bw, 18, FlakeI18n.tr("flake.screen.arena_target", arenaTarget), () -> {
			var names = arenaChoices();
			int index = names.indexOf(arenaTarget);
			arenaTarget = names.get((index + 1) % names.size());
			rebuildWidgets();
		});
		for (String command : List.of("corner1", "corner2", "spawn1", "spawn2", "lobby", "save", "status")) {
			String label = switch (command) {
				case "corner1" -> FlakeI18n.tr("flake.screen.arena.corner1");
				case "corner2" -> FlakeI18n.tr("flake.screen.arena.corner2");
				case "spawn1" -> FlakeI18n.tr("flake.screen.arena.spawn1");
				case "spawn2" -> FlakeI18n.tr("flake.screen.arena.spawn2");
				case "lobby" -> FlakeI18n.tr("flake.screen.arena.lobby");
				case "save" -> FlakeI18n.tr("flake.screen.arena.save");
				default -> FlakeI18n.tr("flake.screen.arena.status");
			};
			button(bx, y + 62 + row++ * 24, bw, 20, label, () -> command("flake arena " + command
					+ ("list".equals(command) ? "" : " " + arenaTarget)));
		}
		int right = bx + bw + 8; row = 0;
		for (var accent : UiTheme.Accent.values()) {
			button(right, y + 62 + row++ * 24, bw, 20, accent.title(), () -> { UiTheme.setAccent(accent); rebuildWidgets(); });
		}
		var hud = FlakeUiClient.hud();
		button(right, y + 62 + row++ * 24, bw, 22,
				FlakeI18n.tr(hud == null || hud.effectsEnabled() ? "flake.screen.vfx.on" : "flake.screen.vfx.off"),
				() -> command("flake effects"));
	}

	private List<String> arenaChoices() {
		var snapshot = FlakeUiClient.snapshot();
		var list = snapshot == null ? new ArrayList<String>() : new ArrayList<>(snapshot.arenas());
		if (list.isEmpty()) list.add("default");
		return list;
	}

	private String arenaLabel() { return arena == null || arena.isBlank() ? "default" : arena; }

	private String nextArena() {
		var choices = arenaChoices();
		int index = choices.indexOf(arenaLabel());
		return choices.get((index + 1) % choices.size());
	}

	private UiButton button(int bx, int by, int bw, int bh, String text, Runnable action) {
		return addRenderableWidget(new UiButton(bx, by, bw, bh, text, false, action));
	}
	private void command(String value) { if (minecraft.player != null) minecraft.player.connection.sendCommand(value); }

	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		g.fill(0, 0, width, height, 0xb8050508); UiTheme.panel(g, x, y, w, h);
		UiTheme.scaledText(g, "FLAKE", x + 12, y + 12, 64, 1.3f, UiTheme.CYAN);
		g.fill(x + 12, y + 36, x + w - 12, y + 37, UiTheme.LINE);
		if (view == ScreenView.MAIN_MENU) UiTheme.centeredText(g, FlakeI18n.tr("flake.screen.main_subtitle"), x + w / 2, y + 58, w - 32, UiTheme.MUTED);
		if (view == ScreenView.BROWSER) extractBrowser(g);
		if (view == ScreenView.CREATE_LOBBY) extractCreate(g);
		if (view == ScreenView.ROOM) extractRoom(g);
		if (view == ScreenView.ARENA_SETTINGS) {
			UiTheme.text(g, FlakeI18n.tr("flake.screen.label.arena"), x + 16, y + 46, w / 2 - 20, UiTheme.CYAN);
			UiTheme.text(g, FlakeI18n.tr("flake.screen.label.theme_effects"), x + w / 2 + 4, y + 46, w / 2 - 20, UiTheme.CYAN);
		}
		super.extractRenderState(g, mx, my, delta);
	}

	private void extractCreate(GuiGraphicsExtractor g) {
		int cw = Math.min(360, w - 32), bx = x + (w - cw) / 2;
		UiTheme.text(g, FlakeI18n.tr("flake.screen.label.lobby_name"), bx, y + 44, cw, UiTheme.CYAN);
		UiTheme.text(g, FlakeI18n.tr("flake.screen.label.mode"), bx, y + 86, cw, UiTheme.MUTED);
		UiTheme.text(g, FlakeI18n.tr("flake.screen.label.visibility"), bx, y + 128, cw, UiTheme.MUTED);
		UiTheme.text(g, FlakeI18n.tr("flake.screen.label.arena_pick"), bx, y + 170, cw, UiTheme.MUTED);
		UiTheme.text(g, FlakeI18n.tr("flake.screen.label.teams"), bx, y + 212, cw, UiTheme.MUTED);
		UiTheme.text(g, FlakeI18n.tr("flake.screen.label.team_size"), bx, y + 250, cw, UiTheme.MUTED);
	}

	private void extractBrowser(GuiGraphicsExtractor g) {
		var snapshot = FlakeUiClient.snapshot();
		UiTheme.text(g, FlakeI18n.tr("flake.screen.browser.title"), x + 16, y + 46, w - 32, UiTheme.CYAN);
		UiTheme.text(g, FlakeI18n.tr("flake.screen.browser.subtitle"), x + 16, y + 60, w - 140, UiTheme.MUTED);
		String error = FlakeUiClient.error();
		if (!error.isEmpty()) UiTheme.text(g, error, x + 16, y + h - 50, w - 32, UiTheme.RED);
		if (snapshot == null || snapshot.rooms().isEmpty()) {
			UiTheme.centeredText(g, FlakeI18n.tr("flake.screen.browser.empty"), x + w / 2, y + h / 2 - 16, w - 32, UiTheme.MUTED);
			return;
		}
		int rowH = 32, top = y + 76, maxRows = Math.max(1, (h - 118) / rowH);
		for (int i = 0; i < Math.min(snapshot.rooms().size(), maxRows); i++) {
			var room = snapshot.rooms().get(i);
			int rowY = top + i * rowH;
			g.fill(x + 12, rowY, x + w - 12, rowY + rowH - 4, room.mine() ? UiTheme.CARD_ALT : UiTheme.CARD);
			if (room.mine()) g.fill(x + 12, rowY, x + 14, rowY + rowH - 4, UiTheme.CYAN);
			UiTheme.text(g, room.title(), x + 20, rowY + 5, w - 170, UiTheme.TEXT);
			String meta = UiTheme.modeName(room.mode()) + " · " + room.arena() + " · " + room.players() + "/" + room.maxPlayers()
					+ (room.privateRoom() ? " · " + FlakeI18n.tr("flake.screen.visibility.private") : "");
			UiTheme.text(g, meta, x + 20, rowY + 17, w - 170, UiTheme.MUTED);
			String state = switch (room.state()) {
				case "FIGHTING" -> FlakeI18n.tr("flake.screen.room_state.fighting");
				case "COUNTDOWN" -> FlakeI18n.tr("flake.screen.room_state.countdown");
				default -> FlakeI18n.tr("flake.screen.room_state.preparing");
			};
			UiTheme.text(g, state, x + w - 210, rowY + 11, 86, UiTheme.GREEN);
		}
	}

	private void extractRoom(GuiGraphicsExtractor g) {
		var snapshot = FlakeUiClient.snapshot();
		if (snapshot == null || !snapshot.inRoom()) {
			UiTheme.centeredText(g, FlakeI18n.tr("flake.screen.no_active_lobby"), x + w / 2, y + h / 2 - 24, w - 32, UiTheme.MUTED);
			return;
		}
		UiTheme.text(g, snapshot.title() + " · " + UiTheme.modeName(snapshot.mode()) + " · " + snapshot.arena(),
				x + 16, y + 44, w - 130, UiTheme.CYAN);
		String error = FlakeUiClient.error();
		UiTheme.text(g, error.isEmpty() ? snapshot.status() : error, x + 16, y + 58, w - 32, error.isEmpty() ? UiTheme.MUTED : UiTheme.RED);
		int count = Math.clamp(snapshot.teamCount(), 2, 4), colW = (w - 32 - 8 * (count - 1)) / count;
		int bottom = y + h - (snapshot.viewerIsMember() ? 140 : 92);
		for (int i = 0; i < count; i++) {
			int bx = x + 16 + i * (colW + 8), by = y + 76;
			g.fill(bx, by, bx + colW, bottom, UiTheme.CARD);
			UiTheme.text(g, FlakeI18n.tr("flake.screen.team_caps", i + 1), bx + 8, by + 8, colW - 16, UiTheme.CYAN);
			int row = 0;
			for (var player : snapshot.players()) {
				if (!player.member() || player.team() != i) continue;
				int py = by + 27 + row++ * 27;
				if (py + 20 > bottom - 6) break;
				UiIcons.classEmblem(g, bx + 8, py, player.fighterClass(), UiTheme.CYAN);
				UiTheme.text(g, player.name() + (player.owner() ? " ★" : ""), bx + 22, py, colW - 28, UiTheme.TEXT);
				UiTheme.text(g, player.ready() ? FlakeI18n.tr("flake.screen.status.ready") : UiTheme.className(player.fighterClass()),
						bx + 22, py + 11, colW - 28, player.ready() ? UiTheme.GREEN : UiTheme.MUTED);
			}
		}
	}

	private static String signature() {
		var snap = FlakeUiClient.snapshot();
		return (snap == null ? "" : snap.roomId() + snap.phase() + snap.players().hashCode() + snap.rooms().hashCode()
				+ snap.viewerIsOwner() + snap.viewerIsMember() + snap.viewerReady() + snap.teamCount() + snap.teamSize() + snap.arena())
				+ (view == ScreenView.ROOM ? "r" : "");
	}

	@Override public void tick() { if (!signature.equals(signature())) rebuildWidgets(); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
}
