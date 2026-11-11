package dev.flake.combat;

import dev.flake.features.loadout.Loadout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;

/** Equipment features contribute setup without adding special cases to match preparation. */
public final class CombatEquipment {
	private static final List<BiConsumer<ServerPlayer, Loadout>> SETUP = new ArrayList<>();
	private CombatEquipment() {}
	public static void register(BiConsumer<ServerPlayer, Loadout> setup) { SETUP.add(setup); }
	public static void apply(ServerPlayer player, Loadout build) { for (var setup : SETUP) setup.accept(player, build); }
}
