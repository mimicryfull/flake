package dev.flake.duel;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** «Плавлячий аметист»: пасивний предмет, що скорочує перезарядку здібностей. */
public final class MeltingAmethyst {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "melting_amethyst");
	public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);
	public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, KEY,
			new Item(new Item.Properties().setId(KEY)));

	private MeltingAmethyst() {}

	/** Forces class initialization during mod startup, while item registration is still allowed. */
	public static void register() {
	}

	public static ItemStack stack() { return new ItemStack(ITEM); }
}
