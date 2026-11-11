package dev.flake.content;

import dev.flake.text.FlakeI18n;

import dev.flake.combat.ability.AbilitySlots.Ability;
import dev.flake.features.loadout.Loadout;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Core contracts for Flake's Vertical Slice Architecture (VSA).
 * Each gameplay feature (weapon, active item, passive item, or fighter class) is a self-contained slice.
 */
public final class ContentSpec {
	private ContentSpec() {}

	public interface Lifecycle {
		void register();
		void reset(ServerPlayer player);
	}

	public record WeaponSpec(
			Loadout.Weapon id,
			String compactTitleKey,
			int cooldownTicks,
			int arrowCount,
			Supplier<ItemStack> stackFactory
	) {
		public String compactTitle() { return FlakeI18n.tr(compactTitleKey); }
		public ItemStack createStack() { return stackFactory.get(); }
		public boolean matches(ItemStack stack) {
			var sample = stackFactory.get();
			return !sample.isEmpty() && !stack.isEmpty() && stack.is(sample.getItem());
		}
	}

	public record ActiveItemSpec(
			Loadout.ActiveItem id,
			String compactTitleKey,
			int cooldownTicks,
			int buffTicks,
			boolean offhand,
			Supplier<ItemStack> stackFactory,
			String buffStatusId
	) {
		public ActiveItemSpec(Loadout.ActiveItem id, String compactTitleKey, int cooldownTicks,
				int buffTicks, boolean offhand, Supplier<ItemStack> stackFactory) {
			this(id, compactTitleKey, cooldownTicks, buffTicks, offhand, stackFactory, null);
		}
		public String compactTitle() { return FlakeI18n.tr(compactTitleKey); }
		public ItemStack createStack() { return stackFactory.get(); }
		public boolean matches(ItemStack stack) {
			var sample = stackFactory.get();
			return !sample.isEmpty() && !stack.isEmpty() && stack.is(sample.getItem());
		}
	}

	public interface PassiveHandler {
		default void onApplyKit(ServerPlayer player) {}
		default void onFightTick(ServerPlayer player, int nowTick) {}
	}

	/** Optional custom status presentation; the HUD does not know the feature implementation. */
	public record StatusSpec(String id, String titleKey, Supplier<ItemStack> iconFactory) {
		public ItemStack createIcon() { return iconFactory.get(); }
	}

	public record PassiveSpec(
			Loadout.PassiveItem id,
			String compactTitleKey,
			String categoryKey,
			Supplier<ItemStack> iconFactory,
			PassiveHandler handler
	) {
		public String compactTitle() { return FlakeI18n.tr(compactTitleKey); }
		public String category() { return FlakeI18n.tr(categoryKey); }
		public ItemStack createIcon() { return iconFactory.get(); }
	}

	public interface ClassSpec {
		Loadout.FighterClass id();
		String roleTitleKey();
		String skillSummaryKey();
		default String roleTitle() { return FlakeI18n.tr(roleTitleKey()); }
		default String skillSummary() { return FlakeI18n.tr(skillSummaryKey()); }
		Ability slot1();
		Ability slot2();
		int activate(ServerPlayer player, int slot);
		int cooldownRemaining(ServerPlayer player, int slot, int nowTick);
		/** Actual length of the cooldown that was started, including passive modifiers. */
		default int cooldownTotal(ServerPlayer player, int slot, int nowTick) {
			return (slot == 1 ? slot1() : slot2()).cooldownTicks();
		}
		int activeElapsed(ServerPlayer player, int slot, int nowTick);
		default int activeTotal(ServerPlayer player, int slot, int nowTick) {
			return (slot == 1 ? slot1() : slot2()).activeTicks();
		}
	}
}
