package dev.flake.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-bound structured combat feedback (item/skill failure reasons and cooldown alerts) for the Flake HUD. */
public record CombatFeedbackPayload(
		String iconId,
		String subjectKey,
		String reasonKey,
		String arg,
		byte tone
) implements CustomPacketPayload {
	public static final byte TONE_BLOCKED = 0;
	public static final byte TONE_COOLDOWN = 1;
	public static final byte TONE_WARN = 2;
	public static final byte TONE_INFO = 3;

	public static final Type<CombatFeedbackPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath("flake", "combat_feedback_v1"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CombatFeedbackPayload> CODEC =
			StreamCodec.ofMember(CombatFeedbackPayload::write, CombatFeedbackPayload::read);

	@Override
	public Type<? extends CustomPacketPayload> type() { return TYPE; }

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(iconId == null ? "" : iconId, 96);
		buf.writeUtf(subjectKey == null ? "" : subjectKey, 128);
		buf.writeUtf(reasonKey == null ? "" : reasonKey, 128);
		buf.writeUtf(arg == null ? "" : arg, 64);
		buf.writeByte(tone);
	}

	private static CombatFeedbackPayload read(RegistryFriendlyByteBuf buf) {
		return new CombatFeedbackPayload(
				buf.readUtf(96),
				buf.readUtf(128),
				buf.readUtf(128),
				buf.readUtf(64),
				buf.readByte());
	}
}
