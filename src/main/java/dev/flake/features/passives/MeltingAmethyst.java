package dev.flake.features.passives;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** «Плавлячий аметист»: пасивний предмет, що скорочує перезарядку здібностей. */
public final class MeltingAmethyst {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("flake", "melting_amethyst");

	private MeltingAmethyst() {}


	public static Item ITEM;

	public static void bind(Item item) { ITEM = item; }

	public static ItemStack stack() { return new ItemStack(ITEM); }
}
