package dev.flake.combat;

import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.text.FlakeI18n;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Structured combat feedback routed to Flake HUD panels on modded clients (with action-bar fallback). */
public final class CombatFeedback {
	private static final ThreadLocal<Boolean> ATTEMPT_REPORTED = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static volatile Consumer<ItemStack> clientCooldownListener;

	private CombatFeedback() {}

	/** Client-only hook registered by UI bootstrap so item cooldown clicks trigger HUD feedback immediately. */
	public static void onClientCooldown(Consumer<ItemStack> listener) {
		clientCooldownListener = listener;
	}

	public static void notifyClientCooldown(ItemStack stack) {
		var listener = clientCooldownListener;
		if (listener != null && stack != null && !stack.isEmpty()) listener.accept(stack);
	}

	public static void beginAttempt() {
		ATTEMPT_REPORTED.set(Boolean.FALSE);
	}

	public static boolean finishAttemptReported() {
		boolean reported = Boolean.TRUE.equals(ATTEMPT_REPORTED.get());
		ATTEMPT_REPORTED.set(Boolean.FALSE);
		return reported;
	}

	public static void send(ServerPlayer player, String iconId, String subjectKey, String reasonKey, String arg, byte tone) {
		if (player == null) return;
		ATTEMPT_REPORTED.set(Boolean.TRUE);
		if (ServerPlayNetworking.canSend(player, CombatFeedbackPayload.TYPE)) {
			ServerPlayNetworking.send(player, new CombatFeedbackPayload(iconId, subjectKey, reasonKey, arg, tone));
			return;
		}
		String reason = arg == null || arg.isEmpty() ? FlakeI18n.tr(reasonKey) : FlakeI18n.tr(reasonKey, arg);
		String subject = subjectKey == null || subjectKey.isEmpty() ? "" : FlakeI18n.tr(subjectKey);
		String text = subject.isEmpty() ? reason : subject + " · " + reason;
		player.sendSystemMessage(Component.literal(text), true);
	}

	public static void actionBar(ServerPlayer player, String key, Object... args) {
		String arg = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
		send(player, "", "", key, arg, CombatFeedbackPayload.TONE_INFO);
	}

	public static void unavailable(ServerPlayer player) {
		unavailable(player, (Ability) null);
	}

	public static void unavailable(ServerPlayer player, Ability ability) {
		String title = abilityTitle(ability);
		if (title.isEmpty()) {
			send(player, "skill", "", "flake.feedback.no_class", "", CombatFeedbackPayload.TONE_BLOCKED);
		} else {
			send(player, "skill", title, "flake.feedback.unavailable", "", CombatFeedbackPayload.TONE_BLOCKED);
		}
	}

	public static void unavailable(ServerPlayer player, ItemStack stack) {
		send(player, itemIconId(stack), itemSubjectKey(stack), "flake.feedback.unavailable", "", CombatFeedbackPayload.TONE_BLOCKED);
	}

	public static void onCooldown(ServerPlayer player, int ticks) {
		onCooldown(player, (Ability) null, ticks);
	}

	public static void onCooldown(ServerPlayer player, Ability ability, int ticks) {
		send(player, "skill", abilityTitle(ability),
				"flake.feedback.cooldown", formatSeconds(ticks), CombatFeedbackPayload.TONE_COOLDOWN);
	}

	public static void onCooldown(ServerPlayer player, ItemStack stack, int ticks) {
		send(player, itemIconId(stack), itemSubjectKey(stack),
				"flake.feedback.cooldown", formatSeconds(ticks), CombatFeedbackPayload.TONE_COOLDOWN);
	}

	public static void noTarget(ServerPlayer player) {
		noTarget(player, (Ability) null);
	}

	public static void noTarget(ServerPlayer player, Ability ability) {
		send(player, "skill", abilityTitle(ability),
				"flake.feedback.no_target", "", CombatFeedbackPayload.TONE_WARN);
	}

	public static void noTarget(ServerPlayer player, ItemStack stack) {
		send(player, itemIconId(stack), itemSubjectKey(stack), "flake.feedback.no_target", "", CombatFeedbackPayload.TONE_WARN);
	}

	public static void alreadyActive(ServerPlayer player, Ability ability) {
		send(player, "skill", abilityTitle(ability),
				"flake.feedback.already_active", "", CombatFeedbackPayload.TONE_WARN);
	}

	public static void alreadyActive(ServerPlayer player, ItemStack stack) {
		send(player, itemIconId(stack), itemSubjectKey(stack), "flake.feedback.already_active", "", CombatFeedbackPayload.TONE_WARN);
	}

	public static void mainHandOnly(ServerPlayer player, ItemStack stack) {
		send(player, itemIconId(stack), itemSubjectKey(stack), "flake.feedback.main_hand_only", "", CombatFeedbackPayload.TONE_WARN);
	}

	public static void phaseLocked(ServerPlayer player, ItemStack stack) {
		send(player, itemIconId(stack), itemSubjectKey(stack), "flake.feedback.phase_locked", "", CombatFeedbackPayload.TONE_BLOCKED);
	}

	public static void blocked(ServerPlayer player) {
		blocked(player, (Ability) null);
	}

	public static void blocked(ServerPlayer player, Ability ability) {
		send(player, "skill", abilityTitle(ability),
				blockReasonKey(player), "", CombatFeedbackPayload.TONE_BLOCKED);
	}

	private static String abilityTitle(Ability ability) {
		return ability == null || ability == Ability.UNIMPLEMENTED ? "" : ability.titleKey();
	}

	public static void blocked(ServerPlayer player, ItemStack stack) {
		send(player, itemIconId(stack), itemSubjectKey(stack), blockReasonKey(player), "", CombatFeedbackPayload.TONE_BLOCKED);
	}

	private static String blockReasonKey(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator()) return "flake.feedback.unavailable";
		if (Stuns.blocked(player)) return "flake.feedback.stunned";
		if (DuelRuntime.protectedInventory(player.getUUID()) && !DuelRuntime.fighting(player.getUUID())) {
			return "flake.feedback.inventory_locked";
		}
		return "flake.feedback.wait_start";
	}

	private static String formatSeconds(int ticks) {
		float seconds = Math.max(0.1f, ticks / 20f);
		return String.format(Locale.ROOT, "%.1f", seconds);
	}

	private static String itemIconId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key == null ? "" : key.toString();
	}

	private static String itemSubjectKey(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return "";
		return stack.get(DataComponents.CUSTOM_NAME) != null
				? stack.getHoverName().getString()
				: stack.getItem().getDescriptionId();
	}
}
