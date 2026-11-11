package dev.flake.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Status projection contributions, registered by their owning feature at startup. */
public final class CombatStatuses {
	public record Status(String id, int ticksLeft, int totalTicks, int tone) {}
	@FunctionalInterface
	public interface Source {
		void append(ServerPlayer player, int now, List<Status> statuses);
	}

	private static final List<Source> SOURCES = new ArrayList<>();

	private CombatStatuses() {}

	public static void register(Source source) { SOURCES.add(source); }

	public static List<Status> statuses(ServerPlayer player, int now) {
		var result = new ArrayList<Status>();
		for (var source : SOURCES) source.append(player, now, result);
		return result;
	}
}
