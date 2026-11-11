package dev.flake.content;

import dev.flake.combat.item.ActiveCombatItem;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

/** Small Fabric registration helpers. Content and activation remain owned by their feature. */
public final class ItemRegistration {
	private ItemRegistration() {}

	public static Item register(Identifier id, Function<ResourceKey<Item>, Item> factory) {
		var key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(key));
	}

	public static Item active(Identifier id, BiPredicate<ServerPlayer, Entity> action) {
		return register(id, key -> new ActiveCombatItem(new Item.Properties().setId(key).stacksTo(1), id) {
			@Override protected boolean activateServer(ServerPlayer player, Entity target) { return action.test(player, target); }
		});
	}

	public static SoundEvent sound(Identifier id) {
		return Registry.register(BuiltInRegistries.SOUND_EVENT, ResourceKey.create(Registries.SOUND_EVENT, id),
				SoundEvent.createVariableRangeEvent(id));
	}
}
