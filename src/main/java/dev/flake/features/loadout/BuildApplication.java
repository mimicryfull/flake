package dev.flake.features.loadout;

import dev.flake.text.FlakeI18n;
import net.minecraft.server.level.ServerPlayer;

/** Editor request decoding and presentation; mutation policy belongs to {@link LoadoutService}. */
public final class BuildApplication {
	private BuildApplication() {}

	public record Reply(BuildResultPayload payload, boolean synchronize) {}

	public static Reply apply(ServerPlayer player, BuildApplyPayload payload) {
		var service = LoadoutService.of(player.level().getServer());
		var id = player.getUUID();
		// Preserve lock precedence over malformed editor data.
		if (service.locked(id)) return new Reply(new BuildResultPayload(BuildResultPayload.Status.LOCKED,
				service.revision(id), FlakeI18n.tr("flake.editor.error.locked")), false);
		Loadout desired;
		try {
			desired = payload.toLoadout();
		} catch (IllegalArgumentException | NullPointerException invalid) {
			return new Reply(new BuildResultPayload(BuildResultPayload.Status.INVALID,
					service.revision(id), FlakeI18n.tr("flake.editor.error.invalid")), false);
		}
		var result = service.apply(player, payload.revision(), desired);
		String message = switch (result.status()) {
			case OK -> "";
			case LOCKED -> FlakeI18n.tr("flake.editor.error.locked");
			case STALE -> FlakeI18n.tr("flake.editor.error.stale");
			case SAVE_FAILED -> FlakeI18n.tr("flake.editor.error.save");
			case INVALID -> String.join(" · ", result.errors());
		};
		return new Reply(new BuildResultPayload(BuildResultPayload.Status.valueOf(result.status().name()),
				result.revision(), message), result.status() != LoadoutService.Status.LOCKED);
	}
}
