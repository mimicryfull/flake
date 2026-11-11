package dev.flake.features.loadout;

import dev.flake.features.loadout.Loadout;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One atomic build replacement. The client sends the whole desired build plus the revision it edited,
 * so the server can reject stale writes instead of applying them slot by slot.
 */
public record BuildApplyPayload(
		long revision,
		String fighterClass,
		String helmet,
		String chestplate,
		String leggings,
		String boots,
		String weapon,
		String passiveItem,
		String activeItem1,
		String activeItem2
) implements CustomPacketPayload {
	public static final Type<BuildApplyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("flake", "build_apply"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuildApplyPayload> CODEC = StreamCodec.ofMember(
			BuildApplyPayload::write, BuildApplyPayload::read);

	public static BuildApplyPayload of(long revision, Loadout build) {
		return new BuildApplyPayload(revision, build.fighterClass().name(), build.helmet().name(), build.chestplate().name(),
				build.leggings().name(), build.boots().name(), build.weapon().name(), build.passiveItem().name(),
				build.activeItem1().name(), build.activeItem2().name());
	}

	/** Client-side convenience for the optimistic draft; server re-validates everything anyway. */
	public Loadout toLoadout() {
		return new Loadout(
				Loadout.FighterClass.valueOf(fighterClass),
				Loadout.ArmorMaterial.valueOf(helmet),
				Loadout.ArmorMaterial.valueOf(chestplate),
				Loadout.ArmorMaterial.valueOf(leggings),
				Loadout.ArmorMaterial.valueOf(boots),
				Loadout.Weapon.valueOf(weapon),
				Loadout.PassiveItem.valueOf(passiveItem),
				Loadout.ActiveItem.valueOf(activeItem1),
				Loadout.ActiveItem.valueOf(activeItem2));
	}

	@Override public Type<BuildApplyPayload> type() { return TYPE; }

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarLong(revision);
		buf.writeUtf(fighterClass, 32);
		buf.writeUtf(helmet, 32);
		buf.writeUtf(chestplate, 32);
		buf.writeUtf(leggings, 32);
		buf.writeUtf(boots, 32);
		buf.writeUtf(weapon, 32);
		buf.writeUtf(passiveItem, 32);
		buf.writeUtf(activeItem1, 32);
		buf.writeUtf(activeItem2, 32);
	}

	private static BuildApplyPayload read(RegistryFriendlyByteBuf buf) {
		return new BuildApplyPayload(buf.readVarLong(), buf.readUtf(32), buf.readUtf(32), buf.readUtf(32),
				buf.readUtf(32), buf.readUtf(32), buf.readUtf(32), buf.readUtf(32), buf.readUtf(32), buf.readUtf(32));
	}
}
