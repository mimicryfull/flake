package dev.flake.combat.ability;

/**
 * Stackable cooldown tracker for a single skill slot.
 * Supports both single-charge skills (maxCharges = 1) and stacked charges (maxCharges > 1):
 * unused ready skills continue charging additional stacks up to the cap and can be cast back-to-back.
 */
public final class SkillChargePool {
	private int charges = 1;
	private int nextChargeAt;
	private int cooldownTotal;

	public void sync(int now, int maxCharges, int cooldownTicks) {
		int cap = Math.max(1, maxCharges);
		int cd = Math.max(1, cooldownTicks);
		if (charges > cap) {
			charges = cap;
		}
		if (charges >= cap) {
			nextChargeAt = 0;
			return;
		}
		while (charges < cap && nextChargeAt > 0 && now >= nextChargeAt) {
			charges++;
			if (charges < cap) {
				nextChargeAt += cd;
				cooldownTotal = cd;
			} else {
				nextChargeAt = 0;
			}
		}
		if (charges < cap && nextChargeAt == 0) {
			nextChargeAt = now + cd;
			cooldownTotal = cd;
		}
	}

	public boolean consume(int now, int maxCharges, int cooldownTicks) {
		sync(now, maxCharges, cooldownTicks);
		if (charges <= 0) return false;
		charges--;
		int cd = Math.max(1, cooldownTicks);
		if (nextChargeAt == 0) {
			nextChargeAt = now + cd;
			cooldownTotal = cd;
		}
		return true;
	}

	public int charges(int now, int maxCharges, int cooldownTicks) {
		sync(now, maxCharges, cooldownTicks);
		return charges;
	}

	public int remaining(int now, int maxCharges, int cooldownTicks) {
		sync(now, maxCharges, cooldownTicks);
		return nextChargeAt == 0 ? 0 : Math.max(0, nextChargeAt - now);
	}

	public int total() { return cooldownTotal; }

	public void reset() {
		charges = 1;
		nextChargeAt = 0;
		cooldownTotal = 0;
	}
}
