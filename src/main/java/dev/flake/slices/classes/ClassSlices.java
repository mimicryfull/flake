package dev.flake.slices.classes;

import dev.flake.ability.AbilitySlots.Ability;
import dev.flake.ability.ClassSkills;
import dev.flake.build.Loadout.FighterClass;
import dev.flake.build.LoadoutCommands;
import dev.flake.shared.CombatSlice.ClassSpec;
import dev.flake.shared.CombatSlice.Lifecycle;
import dev.flake.shared.SliceRegistry;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Vertical slices for all 4 Fighter Classes: Контужений, Бастіон, Упир, Підривник. */
public final class ClassSlices implements Lifecycle {
	private ClassSlices() {}

	public static void install() {
		SliceRegistry.registerClass(new ClassSpec() {
			@Override public FighterClass id() { return FighterClass.CONCUSSED; }
			@Override public String roleTitleKey() { return "flake.class.concussed.role"; }
			@Override public String skillSummaryKey() { return "flake.class.concussed.summary"; }
			@Override public Ability slot1() { return Ability.GRAVITATIONAL_DISSONANCE; }
			@Override public Ability slot2() { return Ability.ELECTRIC_QUAKE; }
			@Override public int cooldownRemaining(UUID id, int slot, int now) {
				return slot == 1 ? LoadoutCommands.cooldownRemainingTicks(id, now) : LoadoutCommands.quakeCooldownRemainingTicks(id, now);
			}
			@Override public int cooldownTotal(UUID id, int slot, int now) {
				return slot == 1 ? LoadoutCommands.shockwaveCooldownTotalTicks(id) : LoadoutCommands.quakeCooldownTotalTicks(id);
			}
			@Override public int activeElapsed(UUID id, int slot, int now) {
				return slot == 1 ? LoadoutCommands.chargeElapsedTicks(id, now) : LoadoutCommands.quakeActiveElapsedTicks(id, now);
			}
			@Override public int activeTotal(UUID id, int slot, int now) {
				return (slot == 1 ? Ability.GRAVITATIONAL_DISSONANCE : Ability.ELECTRIC_QUAKE).activeTicks();
			}
		});

		SliceRegistry.registerClass(skillClass(
				FighterClass.BASTION,
				"flake.class.bastion.role",
				"flake.class.bastion.summary",
				Ability.SHIELD_RAM,
				Ability.BULWARK
		));

		SliceRegistry.registerClass(skillClass(
				FighterClass.VAMPIRE,
				"flake.class.vampire.role",
				"flake.class.vampire.summary",
				Ability.BLOOD_LUNGE,
				Ability.BLOODLUST
		));

		SliceRegistry.registerClass(skillClass(
				FighterClass.DEMOLITIONIST,
				"flake.class.demolitionist.role",
				"flake.class.demolitionist.summary",
				Ability.PROXIMITY_MINE,
				Ability.BLAST_JUMP
		));

		SliceRegistry.registerLifecycle(new ClassSlices());
	}

	private static ClassSpec skillClass(FighterClass cls, String roleKey, String summaryKey, Ability s1, Ability s2) {
		return new ClassSpec() {
			@Override public FighterClass id() { return cls; }
			@Override public String roleTitleKey() { return roleKey; }
			@Override public String skillSummaryKey() { return summaryKey; }
			@Override public Ability slot1() { return s1; }
			@Override public Ability slot2() { return s2; }
			@Override public int cooldownRemaining(UUID id, int slot, int now) { return ClassSkills.remaining(id, slot, now); }
			@Override public int cooldownTotal(UUID id, int slot, int now) {
				int stored = ClassSkills.cooldownTotal(id, slot, now);
				return stored > 0 ? stored : (slot == 1 ? s1 : s2).cooldownTicks();
			}
			@Override public int activeElapsed(UUID id, int slot, int now) { return ClassSkills.activeElapsed(id, slot, now); }
			@Override public int activeTotal(UUID id, int slot, int now) { return (slot == 1 ? s1 : s2).activeTicks(); }
		};
	}

	@Override public void register() { ClassSkills.register(); }
	@Override public void reset(ServerPlayer player) { ClassSkills.reset(player); }
}
