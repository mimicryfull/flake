package dev.flake.features.portal;

import dev.flake.Flake;
import dev.flake.content.ItemRegistration;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Registration entry point for the expandable Parallax Vortex Portal block and item. */
public final class PortalVortexFeature {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Flake.MOD_ID, "portal_vortex");

	private static PortalVortexBlock block;
	private static BlockEntityType<PortalVortexBlockEntity> blockEntityType;
	private static Item item;

	private PortalVortexFeature() {}

	public static void install() {}

	public static void registerItems() {
		var blockKey = ResourceKey.create(Registries.BLOCK, ID);
		block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new PortalVortexBlock(
				BlockBehaviour.Properties.of()
						.setId(blockKey)
						.noCollision()
						.noOcclusion()
						.strength(1.5f)
						.sound(SoundType.AMETHYST)
						.lightLevel(state -> 12)));

		var beKey = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, ID);
		blockEntityType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, beKey,
				FabricBlockEntityTypeBuilder.create(PortalVortexBlockEntity::new, block).build());

		item = ItemRegistration.register(ID, itemKey -> new BlockItem(block,
				new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
	}

	public static PortalVortexBlock block() {
		return block;
	}

	public static BlockEntityType<PortalVortexBlockEntity> blockEntityType() {
		return blockEntityType;
	}

	public static Item item() {
		return item;
	}

	public static ItemStack createStack() {
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
