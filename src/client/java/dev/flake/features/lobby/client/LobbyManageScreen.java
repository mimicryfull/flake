package dev.flake.features.lobby.client;

import dev.flake.client.network.FlakeUiClient;
import dev.flake.client.ui.widgets.UiButton;
import dev.flake.client.ui.UiTheme;
import dev.flake.text.FlakeI18n;
import dev.flake.features.lobby.LobbySnapshotPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Owner-only management: invite online players, kick members and hand over ownership.
 * Both lists page instead of truncating, and every action goes through the server for permission checks.
 */
public final class LobbyManageScreen extends Screen {
	private static final int ROWS = 5;
	private final Screen parent;
	private int invitesPage, membersPage;
	private int x, y, w, h;
	private String signature = "";

	public LobbyManageScreen(Screen parent) {
		super(Component.literal(FlakeI18n.tr("flake.manage.title")));
		this.parent = parent;
	}

	@Override protected void init() {
		w = Math.min(520, width - 16); h = Math.min(330, height - 16);
		x = (width - w) / 2; y = (height - h) / 2;
		FlakeUiClient.query();
		signature = signature();
		int colW = (w - 40) / 2, leftX = x + 16, rightX = leftX + colW + 8;
		var snapshot = FlakeUiClient.snapshot();

		var candidates = candidates(snapshot);
		int invitePages = pages(candidates.size());
		invitesPage = Math.clamp(invitesPage, 0, invitePages - 1);
		for (int i = 0; i < Math.min(ROWS, candidates.size() - invitesPage * ROWS); i++) {
			var candidate = candidates.get(invitesPage * ROWS + i);
			addRenderableWidget(new UiButton(rightX, y + 92 + i * 24, colW, 20,
					FlakeI18n.tr("flake.manage.invite", candidate.name()), false,
					() -> FlakeUiClient.invite(candidate.id().toString())));
		}
		if (invitePages > 1) {
			addRenderableWidget(new UiButton(rightX, y + 92 + ROWS * 24, (colW - 6) / 2, 20, "◀", false, () -> { invitesPage--; rebuildWidgets(); }));
			addRenderableWidget(new UiButton(rightX + (colW + 6) / 2, y + 92 + ROWS * 24, (colW - 6) / 2, 20, "▶", false, () -> { invitesPage++; rebuildWidgets(); }));
		}

		var members = snapshot == null ? List.<LobbySnapshotPayload.Player>of() : snapshot.players().stream()
				.filter(p -> p.member() && !p.owner()).toList();
		int memberPages = pages(members.size());
		membersPage = Math.clamp(membersPage, 0, memberPages - 1);
		for (int i = 0; i < Math.min(ROWS, members.size() - membersPage * ROWS); i++) {
			var member = members.get(membersPage * ROWS + i);
			int rowY = y + 92 + i * 24;
			addRenderableWidget(new UiButton(leftX, rowY, (colW - 8) / 2, 20, FlakeI18n.tr("flake.manage.kick"), false,
					() -> FlakeUiClient.kick(member.id().toString())));
			addRenderableWidget(new UiButton(leftX + (colW + 8) / 2, rowY, (colW - 8) / 2, 20, FlakeI18n.tr("flake.manage.transfer"), false,
					() -> FlakeUiClient.transfer(member.id().toString())));
		}
		if (memberPages > 1) {
			addRenderableWidget(new UiButton(leftX, y + 92 + ROWS * 24, (colW - 6) / 2, 20, "◀", false, () -> { membersPage--; rebuildWidgets(); }));
			addRenderableWidget(new UiButton(leftX + (colW + 6) / 2, y + 92 + ROWS * 24, (colW - 6) / 2, 20, "▶", false, () -> { membersPage++; rebuildWidgets(); }));
		}

		addRenderableWidget(new UiButton(x + 16, y + h - 32, 130, 22, FlakeI18n.tr("flake.manage.back"), false, this::onClose));
		addRenderableWidget(new UiButton(x + w - 158, y + h - 32, 142, 22, FlakeI18n.tr("flake.screen.btn.refresh"), false, FlakeUiClient::query));
	}

	private record Candidate(String id, String name) {}

	private static List<Candidate> candidates(LobbySnapshotPayload snapshot) {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (client.getConnection() == null) return List.of();
		var memberIds = new java.util.HashSet<java.util.UUID>();
		if (snapshot != null) for (var player : snapshot.players()) if (player.member() || player.invited()) memberIds.add(player.id());
		var self = client.player == null ? null : client.player.getUUID();
		var list = new ArrayList<Candidate>();
		for (var info : client.getConnection().getListedOnlinePlayers()) {
			var id = info.getProfile().id();
			if (id.equals(self) || memberIds.contains(id)) continue;
			list.add(new Candidate(id.toString(), info.getProfile().name()));
		}
		list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
		return list;
	}

	private static int pages(int count) { return Math.max(1, (count + ROWS - 1) / ROWS); }

	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		g.fill(0, 0, width, height, 0xb8050508);
		UiTheme.panel(g, x, y, w, h);
		UiTheme.scaledText(g, FlakeI18n.tr("flake.manage.title"), x + 16, y + 14, w - 32, 1.2f, UiTheme.CYAN);
		g.fill(x + 16, y + 34, x + w - 16, y + 35, UiTheme.LINE);
		var snapshot = FlakeUiClient.snapshot();
		String error = FlakeUiClient.error();
		UiTheme.text(g, error.isEmpty() ? FlakeI18n.tr("flake.manage.hint") : error, x + 16, y + 44, w - 32,
				error.isEmpty() ? UiTheme.MUTED : UiTheme.RED);
		int colW = (w - 40) / 2, leftX = x + 16, rightX = leftX + colW + 8;
		UiTheme.text(g, FlakeI18n.tr("flake.manage.members", snapshot == null ? 0
				: snapshot.players().stream().filter(LobbySnapshotPayload.Player::member).count()),
				leftX, y + 76, colW, UiTheme.CYAN);
		UiTheme.text(g, FlakeI18n.tr("flake.manage.invites"), rightX, y + 76, colW, UiTheme.CYAN);

		var members = snapshot == null ? List.<LobbySnapshotPayload.Player>of() : snapshot.players().stream()
				.filter(p -> p.member() && !p.owner()).toList();
		if (members.isEmpty()) UiTheme.text(g, FlakeI18n.tr("flake.manage.no_members"), leftX, y + 96, colW, UiTheme.MUTED);
		var candidates = candidates(snapshot);
		if (candidates.isEmpty()) UiTheme.text(g, FlakeI18n.tr("flake.manage.no_candidates"), rightX, y + 96, colW, UiTheme.MUTED);
		super.extractRenderState(g, mx, my, delta);
	}

	@Override public void tick() { if (!signature.equals(signature())) rebuildWidgets(); }

	private static String signature() {
		var snapshot = FlakeUiClient.snapshot();
		var client = net.minecraft.client.Minecraft.getInstance();
		int online = client.getConnection() == null ? 0 : client.getConnection().getListedOnlinePlayers().size();
		return (snapshot == null ? "" : snapshot.roomId() + snapshot.players().hashCode() + snapshot.owner()) + online;
	}

	@Override public void onClose() { minecraft.gui.setScreen(parent); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) { }
}
