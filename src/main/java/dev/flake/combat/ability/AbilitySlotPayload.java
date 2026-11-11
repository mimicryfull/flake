package dev.flake.combat.ability;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** No client-provided class or ability identifier. Invalid slots are ignored server-side. */
public record AbilitySlotPayload(int slot) implements CustomPacketPayload {
	public static final Type<AbilitySlotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("flake", "ability_slot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilitySlotPayload> CODEC =
			ByteBufCodecs.VAR_INT.map(AbilitySlotPayload::new, AbilitySlotPayload::slot).cast();
	@Override public Type<AbilitySlotPayload> type() { return TYPE; }
}
