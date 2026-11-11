package dev.flake.features.darkcube;

import dev.flake.features.loadout.Loadout.ActiveItem;
import dev.flake.content.ContentSpec.ActiveItemSpec;
import dev.flake.content.ContentSpec.Lifecycle;
import dev.flake.content.ContentCatalog;
import dev.flake.combat.item.ActiveCombatItem;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slice for «Куб темряви» (Cube of Darkness): item metadata, cooldown, kit stack, and server lifecycle. */
public final class DarkCubeDefinition implements Lifecycle {
	public static final ActiveItemSpec SPEC = new ActiveItemSpec(
			ActiveItem.DARK_CUBE,
			"flake.active.dark_cube.short",
			DarkCubeRuntime.COOLDOWN_TICKS,
			0,
			false,
			DarkCubeRuntime::stack
	);

	private DarkCubeDefinition() {}

	public static void registerItems() {
		DarkCubeRuntime.bind(dev.flake.content.ItemRegistration.register(DarkCubeRuntime.ID, key -> new ActiveCombatItem(
				new net.minecraft.world.item.Item.Properties().setId(key).stacksTo(1), DarkCubeRuntime.ID) {
			@Override protected boolean canConsumeOnClient(net.minecraft.world.entity.player.Player player) { return DarkCubeRuntime.hasClientTarget(player); }
			@Override protected boolean activateServer(ServerPlayer player, net.minecraft.world.entity.Entity target) { return DarkCubeRuntime.fire(player); }
		}));
		DarkCubeRuntime.bindSound(dev.flake.content.ItemRegistration.sound(DarkCubeRuntime.ID));
	}

	public static void install() {
		ContentCatalog.registerActive(SPEC);
		ContentCatalog.registerLifecycle(new DarkCubeDefinition());
	}

	@Override public void register() { DarkCubeRuntime.register(); }
	@Override public void reset(ServerPlayer player) { DarkCubeRuntime.reset(player); }
}
