package dev.flake.features.concussed.client;

import dev.flake.render.vfx.RadialActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import dev.flake.features.concussed.EnergyMotes;
import dev.flake.features.concussed.Shockwave;
import dev.flake.features.concussed.ShockwaveAnimation;
import dev.flake.render.vfx.CombatVfxPayload;

public final class ShockwaveActor extends RadialActor {
	private final double[] ex = new double[EnergyMotes.COUNT], ey = new double[EnergyMotes.COUNT], ez = new double[EnergyMotes.COUNT];

	public ShockwaveActor(CombatVfxPayload payload) {
		super(payload);
		for (int i = 0; i < EnergyMotes.COUNT; i++) {
			ey[i] = EnergyMotes.directionY(i);
			double r = Math.sqrt(1 - ey[i] * ey[i]);
			double angle = EnergyMotes.angle(i, sourceId);
			ex[i] = Math.cos(angle) * r; ez[i] = Math.sin(angle) * r;
		}
	}
	@Override protected float duration() { return ShockwaveAnimation.VFX_DURATION_SECONDS; }
	@Override protected float radius(float age) { return ShockwaveAnimation.radius(age, (float) Shockwave.RADIUS); }
	@Override protected float opacity(float age) { return Math.max(ShockwaveAnimation.opacity(age), age < EnergyMotes.DURATION ? 0.001f : 0f); }
	@Override protected int style() { return 0; }
	@Override protected float styleExtra(float age) { return ShockwaveAnimation.chargeProgress(age); }

	@Override protected void details(RenderFrame frame, SlotWriter slot, float age, float ox, float oy, float oz) {
		float[] values = slot.values;
		double travel = EnergyMotes.travel(age);
		double tailTravel = travel - EnergyMotes.travel(Math.max(0, age - 0.035f));
		for (int p = 0; p < (int) values[35]; p++) {
			int seed = frame.quality == 0 ? p * 3 : frame.quality == 1 ? (p * 5) % EnergyMotes.COUNT : p;
			float opacity = EnergyMotes.opacity(seed, age);
			if (opacity <= 0) continue;
			double reach = EnergyMotes.speed(seed) * travel;
			double tail = EnergyMotes.speed(seed) * tailTravel;
			int base = slot.particle + p * 4;
			values[base] = ox + (float) (ex[seed] * reach);
			values[base + 1] = oy + (float) (ey[seed] * reach);
			values[base + 2] = oz + (float) (ez[seed] * reach);
			values[base + 3] = EnergyMotes.size(seed) * (0.7f + opacity * 0.3f);
			base = slot.tail + p * 4;
			values[base] = (float) (-ex[seed] * tail);
			values[base + 1] = (float) (-ey[seed] * tail);
			values[base + 2] = (float) (-ez[seed] * tail);
			values[base + 3] = opacity;
		}
	}
}
