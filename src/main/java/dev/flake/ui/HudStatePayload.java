package dev.flake.ui;

import dev.flake.build.Loadout;
import dev.flake.ability.AbilitySlots;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HudStatePayload(
		String fighterClass,
		String helmet,
		String chestplate,
		String leggings,
		String boots,
		String weapon,
		String passiveItem,
		String activeItem1,
		String activeItem2,
		int cost,
		int budget,
		boolean validBuild,
		int cooldownRemainingTicks,
		int cooldownTotalTicks,
		int chargeElapsedTicks,
		int chargeTotalTicks,
		int quakeCooldownRemainingTicks,
		int quakeCooldownTotalTicks,
		int quakeActiveTicks,
		int quakeTotalTicks,
		boolean abilityBlocked,
		boolean effectsEnabled,
		String phase,
		int round,
		int timerTicks,
		int timerTotalTicks,
		int firstWins,
		int secondWins,
		int winsNeeded,
		Fighter first,
		Fighter second,
		String lobbyTitle,
		int lobbyCount,
		boolean inLobby,
		boolean ready,
		long buildRevision,
		Role viewerRole,
		LoadState loadState,
		String blockReason,
		int blockRemainingTicks,
		List<ItemCooldown> itemCooldowns,
		List<Status> statuses
) implements CustomPacketPayload {
	public static final Type<HudStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("flake", "hud_state_v2"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HudStatePayload> CODEC = StreamCodec.ofMember(
			HudStatePayload::write, HudStatePayload::read);
	public static final Fighter EMPTY_FIGHTER = new Fighter("", "", 0f, 20f, false);
	public static final int MAX_STATUSES = 12;
	public static final int MAX_ITEM_COOLDOWNS = 36;

	public enum SkillState { READY, CHARGING, COOLDOWN, BLOCKED, PASSIVE }
	/** Who the client is for this snapshot. Spectators must never get participant-only controls. */
	public enum Role { NONE, SPECTATOR, PARTICIPANT }
	public enum LoadState { MISSING, LOADED, CORRUPT }

	public HudStatePayload {
		if (first == null) first = EMPTY_FIGHTER;
		if (second == null) second = EMPTY_FIGHTER;
		if (viewerRole == null) viewerRole = Role.NONE;
		if (loadState == null) loadState = LoadState.MISSING;
		if (blockReason == null) blockReason = "";
		itemCooldowns = sanitizeCooldowns(itemCooldowns);
		statuses = sanitize(statuses);
	}

	public static HudStatePayload legacy(String fighterClass, String helmet, String chestplate, String leggings, String boots,
			String weapon, String passiveItem, String activeItem1, String activeItem2, int cost, int budget, boolean validBuild,
			int cooldownRemainingTicks, int cooldownTotalTicks, int chargeElapsedTicks, int chargeTotalTicks,
			int quakeCooldownRemainingTicks, int quakeCooldownTotalTicks, int quakeActiveTicks, int quakeTotalTicks,
			boolean abilityBlocked, boolean effectsEnabled, String phase, int round, int timerTicks, int timerTotalTicks,
			int firstWins, int secondWins, int winsNeeded, Fighter first, Fighter second, String lobbyTitle, int lobbyCount,
			boolean inLobby, boolean ready, List<Status> statuses) {
		return new HudStatePayload(fighterClass, helmet, chestplate, leggings, boots, weapon, passiveItem, activeItem1, activeItem2,
				cost, budget, validBuild, cooldownRemainingTicks, cooldownTotalTicks, chargeElapsedTicks, chargeTotalTicks,
				quakeCooldownRemainingTicks, quakeCooldownTotalTicks, quakeActiveTicks, quakeTotalTicks, abilityBlocked,
				effectsEnabled, phase, round, timerTicks, timerTotalTicks, firstWins, secondWins, winsNeeded, first, second,
				lobbyTitle, lobbyCount, inLobby, ready, 0L, Role.NONE, LoadState.MISSING, "", 0, List.of(), statuses);
	}

	private static List<Status> sanitize(List<Status> source) {
		if (source == null || source.isEmpty()) return List.of();
		var clean = new ArrayList<Status>(Math.min(source.size(), MAX_STATUSES));
		for (Status status : source) {
			if (status == null) continue;
			if (clean.size() >= MAX_STATUSES) break;
			int ticksLeft = status.infinite() ? 0 : Math.max(0, status.ticksLeft());
			int totalTicks = status.infinite() ? 0 : Math.max(ticksLeft, status.totalTicks());
			int amplifier = Math.clamp(status.amplifier(), 0, 255);
			String id = status.id() == null ? "" : status.id();
			clean.add(new Status(id, ticksLeft, totalTicks, status.tone(), amplifier, status.infinite()));
		}
		return List.copyOf(clean);
	}

	private static List<ItemCooldown> sanitizeCooldowns(List<ItemCooldown> source) {
		if (source == null || source.isEmpty()) return List.of();
		var clean = new ArrayList<ItemCooldown>(Math.min(source.size(), MAX_ITEM_COOLDOWNS));
		for (ItemCooldown cooldown : source) {
			if (cooldown == null || cooldown.id() == null || cooldown.id().isBlank()) continue;
			if (clean.size() >= MAX_ITEM_COOLDOWNS) break;
			clean.add(new ItemCooldown(cooldown.id(), Math.max(0, cooldown.totalTicks())));
		}
		return List.copyOf(clean);
	}

	@Override
	public Type<HudStatePayload> type() {
		return TYPE;
	}

	public Loadout toLoadout() {
		try {
			var cls = Loadout.FighterClass.valueOf(fighterClass);
			var helm = Loadout.ArmorMaterial.valueOf(helmet);
			var chest = Loadout.ArmorMaterial.valueOf(chestplate);
			var legs = Loadout.ArmorMaterial.valueOf(leggings);
			var bts = Loadout.ArmorMaterial.valueOf(boots);
			var wpn = Loadout.Weapon.valueOf(weapon);
			var pas = Loadout.PassiveItem.valueOf(passiveItem);
			return new Loadout(cls, helm, chest, legs, bts, wpn, pas,
					Loadout.ActiveItem.valueOf(activeItem1), Loadout.ActiveItem.valueOf(activeItem2));
		} catch (IllegalArgumentException | NullPointerException invalid) {
			throw new IllegalArgumentException("Invalid v2 build state", invalid);
		}
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(fighterClass, 32);
		buf.writeUtf(helmet, 32);
		buf.writeUtf(chestplate, 32);
		buf.writeUtf(leggings, 32);
		buf.writeUtf(boots, 32);
		buf.writeUtf(weapon, 32);
		buf.writeUtf(passiveItem, 32);
		buf.writeUtf(activeItem1, 32);
		buf.writeUtf(activeItem2, 32);
		buf.writeVarInt(cost);
		buf.writeVarInt(budget);
		buf.writeBoolean(validBuild);
		buf.writeVarInt(cooldownRemainingTicks);
		buf.writeVarInt(cooldownTotalTicks);
		buf.writeVarInt(chargeElapsedTicks);
		buf.writeVarInt(chargeTotalTicks);
		buf.writeVarInt(quakeCooldownRemainingTicks);
		buf.writeVarInt(quakeCooldownTotalTicks);
		buf.writeVarInt(quakeActiveTicks);
		buf.writeVarInt(quakeTotalTicks);
		buf.writeBoolean(abilityBlocked);
		buf.writeBoolean(effectsEnabled);
		buf.writeUtf(phase, 32);
		buf.writeVarInt(round);
		buf.writeVarInt(timerTicks);
		buf.writeVarInt(timerTotalTicks);
		buf.writeVarInt(firstWins);
		buf.writeVarInt(secondWins);
		buf.writeVarInt(winsNeeded);
		writeFighter(buf, first);
		writeFighter(buf, second);
		buf.writeUtf(lobbyTitle, 32);
		buf.writeVarInt(lobbyCount);
		buf.writeBoolean(inLobby);
		buf.writeBoolean(ready);
		buf.writeLong(buildRevision);
		buf.writeVarInt(viewerRole.ordinal());
		buf.writeVarInt(loadState.ordinal());
		buf.writeUtf(blockReason, 48);
		buf.writeVarInt(blockRemainingTicks);
		buf.writeVarInt(itemCooldowns.size());
		for (ItemCooldown cooldown : itemCooldowns) {
			buf.writeUtf(cooldown.id(), 96);
			buf.writeVarInt(cooldown.totalTicks());
		}
		buf.writeVarInt(statuses.size());
		for (Status status : statuses) {
			buf.writeUtf(status.id(), 48);
			buf.writeVarInt(status.ticksLeft());
			buf.writeVarInt(status.totalTicks());
			buf.writeVarInt(status.tone());
			buf.writeVarInt(status.amplifier());
			buf.writeBoolean(status.infinite());
		}
	}

	private static HudStatePayload read(RegistryFriendlyByteBuf buf) {
		String fighterClass = buf.readUtf(32);
		String helmet = buf.readUtf(32);
		String chestplate = buf.readUtf(32);
		String leggings = buf.readUtf(32);
		String boots = buf.readUtf(32);
		String weapon = buf.readUtf(32);
		String passiveItem = buf.readUtf(32);
		String activeItem1 = buf.readUtf(32);
		String activeItem2 = buf.readUtf(32);
		int cost = buf.readVarInt();
		int budget = buf.readVarInt();
		boolean validBuild = buf.readBoolean();
		int cooldownRemainingTicks = buf.readVarInt();
		int cooldownTotalTicks = buf.readVarInt();
		int chargeElapsedTicks = buf.readVarInt();
		int chargeTotalTicks = buf.readVarInt();
		int quakeCooldownRemainingTicks = buf.readVarInt();
		int quakeCooldownTotalTicks = buf.readVarInt();
		int quakeActiveTicks = buf.readVarInt();
		int quakeTotalTicks = buf.readVarInt();
		boolean abilityBlocked = buf.readBoolean();
		boolean effectsEnabled = buf.readBoolean();
		String phase = buf.readUtf(32);
		int round = buf.readVarInt();
		int timerTicks = buf.readVarInt();
		int timerTotalTicks = buf.readVarInt();
		int firstWins = buf.readVarInt();
		int secondWins = buf.readVarInt();
		int winsNeeded = buf.readVarInt();
		Fighter first = readFighter(buf);
		Fighter second = readFighter(buf);
		String lobbyTitle = buf.readUtf(32);
		int lobbyCount = buf.readVarInt();
		boolean inLobby = buf.readBoolean();
		boolean ready = buf.readBoolean();
		long buildRevision = buf.readLong();
		int roleOrdinal = Math.clamp(buf.readVarInt(), 0, Role.values().length - 1);
		int loadOrdinal = Math.clamp(buf.readVarInt(), 0, LoadState.values().length - 1);
		String blockReason = buf.readUtf(48);
		int blockRemainingTicks = buf.readVarInt();
		int cooldownCount = Math.clamp(buf.readVarInt(), 0, MAX_ITEM_COOLDOWNS);
		var itemCooldowns = new ArrayList<ItemCooldown>(cooldownCount);
		for (int i = 0; i < cooldownCount; i++) {
			itemCooldowns.add(new ItemCooldown(buf.readUtf(96), buf.readVarInt()));
		}
		int statusCount = Math.clamp(buf.readVarInt(), 0, MAX_STATUSES);
		var statuses = new ArrayList<Status>(statusCount);
		for (int i = 0; i < statusCount; i++) {
			statuses.add(new Status(buf.readUtf(48), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readBoolean()));
		}
		return new HudStatePayload(
				fighterClass, helmet, chestplate, leggings, boots, weapon,
				passiveItem, activeItem1, activeItem2, cost, budget, validBuild,
				cooldownRemainingTicks, cooldownTotalTicks, chargeElapsedTicks, chargeTotalTicks,
				quakeCooldownRemainingTicks, quakeCooldownTotalTicks, quakeActiveTicks, quakeTotalTicks,
				abilityBlocked, effectsEnabled, phase, round, timerTicks, timerTotalTicks,
				firstWins, secondWins, winsNeeded, first, second, lobbyTitle, lobbyCount, inLobby, ready,
				buildRevision, Role.values()[roleOrdinal], LoadState.values()[loadOrdinal], blockReason, blockRemainingTicks,
				itemCooldowns, statuses
		);
	}

	private static void writeFighter(RegistryFriendlyByteBuf buf, Fighter fighter) {
		buf.writeUtf(fighter.name, 64);
		buf.writeUtf(fighter.fighterClass, 32);
		buf.writeFloat(fighter.health);
		buf.writeFloat(fighter.maxHealth);
		buf.writeBoolean(fighter.self);
	}

	private static Fighter readFighter(RegistryFriendlyByteBuf buf) {
		return new Fighter(buf.readUtf(64), buf.readUtf(32), buf.readFloat(), buf.readFloat(), buf.readBoolean());
	}

	public static SkillState skillState(String fighterClass, boolean blocked, int chargeElapsedTicks, float cooldownRemainingSeconds) {
		return skillState(fighterClass, 1, blocked, chargeElapsedTicks, cooldownRemainingSeconds);
	}

	public static SkillState skillState(String fighterClass, int slot, boolean blocked, int chargeElapsedTicks, float cooldownRemainingSeconds) {
		if (!AbilitySlots.resolve(fighterClass, slot).implemented()) return SkillState.PASSIVE;
		if (blocked) return SkillState.BLOCKED;
		if (chargeElapsedTicks >= 0) return SkillState.CHARGING;
		if (cooldownRemainingSeconds > 0.02f) return SkillState.COOLDOWN;
		return SkillState.READY;
	}

	public int slotCooldownRemaining(int slot) { return slot == 1 ? cooldownRemainingTicks : quakeCooldownRemainingTicks; }
	public int slotCooldownTotal(int slot) { return slot == 1 ? cooldownTotalTicks : quakeCooldownTotalTicks; }
	public int slotActiveElapsed(int slot) { return slot == 1 ? chargeElapsedTicks : quakeActiveTicks; }
	public int slotActiveTotal(int slot) { return slot == 1 ? chargeTotalTicks : quakeTotalTicks; }
	public boolean participant() { return viewerRole == Role.PARTICIPANT; }
	public boolean spectator() { return viewerRole == Role.SPECTATOR; }
	public int itemCooldownTotal(String itemId) {
		for (var cooldown : itemCooldowns) if (cooldown.id().equals(itemId)) return cooldown.totalTicks();
		return 0;
	}

	public static String formatRoundTimer(float seconds) {
		int total = Math.max(0, (int) Math.floor(seconds));
		int mins = total / 60;
		int secs = total % 60;
		return String.format(Locale.ROOT, "%02d:%02d", mins, secs);
	}

	public static String formatShortSeconds(float seconds) {
		return String.format(Locale.ROOT, "%.1f с", Math.max(0f, seconds));
	}

	public record Fighter(String name, String fighterClass, float health, float maxHealth, boolean self) {}

	public record ItemCooldown(String id, int totalTicks) {}

	public record Status(String id, int ticksLeft, int totalTicks, int tone, int amplifier, boolean infinite) {
		public Status(String id, int ticksLeft, int totalTicks, int tone) {
			this(id, ticksLeft, totalTicks, tone, 0, false);
		}
	}
}
