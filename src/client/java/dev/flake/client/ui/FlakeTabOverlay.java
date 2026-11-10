package dev.flake.client.ui;

import dev.flake.shared.FlakeI18n;
import dev.flake.ui.LobbySnapshotPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FlakeTabOverlay {
	private static int page;
	private static int pages = 1;
	private FlakeTabOverlay() {}
	public static void reset() { page = 0; pages = 1; }
	public static void page(int delta) { page = Math.floorMod(page + delta, pages); }

	public static void extract(GuiGraphicsExtractor g) {
		var client = Minecraft.getInstance();
		if (client.getConnection() == null) return;
		var snapshot = FlakeUiClient.snapshot();
		boolean fresh = FlakeUiClient.fresh();
		List<LobbySnapshotPayload.Player> players = snapshot == null ? List.of() : snapshot.players();
		// The network player list is the only source available on servers without this protocol.
		if (!fresh) players = client.getConnection().getListedOnlinePlayers().stream().map(p ->
				new LobbySnapshotPayload.Player(p.getProfile().id(), p.getProfile().name(), "", -1, -1, p.getLatency(),
						false, false, false, 0, false, false, "")).toList();
		int w = Math.min(560, g.guiWidth() - 24);
		int rows = Math.max(1, Math.min(10, (g.guiHeight() - 133) / 38));
		pages = Math.max(1, (players.size() + rows - 1) / rows); page = Math.min(page, pages - 1);
		int first = page * rows, count = Math.min(rows, players.size() - first);
		int h = 111 + Math.max(1, count) * 38;
		int x = (g.guiWidth() - w) / 2, y = Math.max(8, (g.guiHeight() - h) / 3);
		UiTheme.panel(g, x, y, w, h);
		UiTheme.text(g, FlakeI18n.tr("flake.tab.header"), x + 14, y + 14, w - 100, UiTheme.TEXT);
		UiTheme.text(g, FlakeI18n.tr("flake.tab.online_count", players.size()), x + w - 77, y + 14, 64, UiTheme.CYAN);
		String room = !fresh ? FlakeI18n.tr("flake.tab.no_data") : snapshot.title().isEmpty() ? FlakeI18n.tr("flake.tab.lobby_empty") : snapshot.title() + " · " + snapshot.status();
		UiTheme.text(g, room, x + 14, y + 32, w - 28, UiTheme.MUTED);
		int killsX = x + w - 123, deathsX = x + w - 87, pingX = x + w - 50;
		UiTheme.text(g, FlakeI18n.tr("flake.tab.col.player_class"), x + 14, y + 54, w - 160, UiTheme.MUTED);
		UiTheme.text(g, FlakeI18n.tr("flake.tab.col.kills"), killsX, y + 54, 30, UiTheme.MUTED);
		UiTheme.text(g, FlakeI18n.tr("flake.tab.col.deaths"), deathsX, y + 54, 30, UiTheme.MUTED);
		UiTheme.text(g, FlakeI18n.tr("flake.tab.col.ping"), pingX, y + 54, 36, UiTheme.MUTED);
		if (count == 0) UiTheme.text(g, FlakeI18n.tr("flake.tab.empty_server"), x + 14, y + 80, w - 28, UiTheme.MUTED);
		for (int i = 0; i < count; i++) {
			var p = players.get(first + i);
			int rowY = y + 69 + i * 38;
			boolean self = client.player != null && client.player.getUUID().equals(p.id());
			g.fill(x + 8, rowY, x + w - 8, rowY + 34, self ? UiTheme.CARD_ALT : UiTheme.CARD);
			if (self) g.fill(x + 8, rowY, x + 10, rowY + 34, UiTheme.CYAN);
			UiTheme.text(g, p.name(), x + 16, rowY + 5, w - 155, UiTheme.TEXT);
			if (fresh) {
				UiIcons.classEmblem(g, x + 16, rowY + 19, p.fighterClass(), UiTheme.CYAN);
				UiTheme.text(g, UiTheme.className(p.fighterClass()), x + 28, rowY + 20, w - 167, UiTheme.MUTED);
			} else {
				UiTheme.text(g, FlakeI18n.tr("flake.tab.class_unavailable"), x + 16, rowY + 20, w - 155, UiTheme.MUTED);
			}
			UiTheme.text(g, p.kills() < 0 ? "—" : Integer.toString(p.kills()), killsX, rowY + 5, 30, UiTheme.CYAN);
			UiTheme.text(g, p.deaths() < 0 ? "—" : Integer.toString(p.deaths()), deathsX, rowY + 5, 30, UiTheme.TEXT);
			UiTheme.text(g, Integer.toString(p.ping()), pingX, rowY + 5, 36, p.ping() < 100 ? UiTheme.GREEN : p.ping() < 250 ? UiTheme.AMBER : UiTheme.RED);
			String status = !fresh ? FlakeI18n.tr("flake.tab.status.no_data")
					: !p.member() ? (p.roomTitle().isBlank() ? FlakeI18n.tr("flake.tab.status.outside")
							: FlakeI18n.tr("flake.tab.status.other_room", p.roomTitle()))
					: snapshot.phase().equals("FIGHTING") ? FlakeI18n.tr("flake.tab.status.fighting")
					: p.ready() ? FlakeI18n.tr("flake.tab.status.ready") : FlakeI18n.tr("flake.tab.status.in_lobby");
			UiTheme.text(g, status, killsX, rowY + 20, 110, p.ready() ? UiTheme.GREEN : UiTheme.MUTED);
		}
		UiTheme.text(g, FlakeI18n.tr("flake.tab.stats_note"), x + 14, y + h - 32, w - 28, UiTheme.MUTED);
		String openKeyName = dev.flake.client.FlakeClient.openKey != null
				? dev.flake.client.FlakeClient.openKey.getTranslatedKeyMessage().getString() : "G";
		String footer = pages > 1 ? FlakeUiClient.previousPageKey.getTranslatedKeyMessage().getString() + " / "
				+ FlakeUiClient.nextPageKey.getTranslatedKeyMessage().getString() + " · " + FlakeI18n.tr("flake.tab.page", page + 1, pages)
				: openKeyName + " · " + FlakeI18n.tr("flake.tab.open_hint");
		UiTheme.text(g, footer, x + 14, y + h - 17, w - 28, UiTheme.CYAN);
	}
}
