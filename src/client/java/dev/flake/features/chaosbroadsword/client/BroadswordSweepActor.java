package dev.flake.features.chaosbroadsword.client;

import dev.flake.features.chaosbroadsword.ChaosBroadswordAnimation;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.RadialActor;
import dev.flake.render.vfx.RenderFrame;
import dev.flake.render.vfx.SlotWriter;
import net.minecraft.world.phys.Vec3;

/** Expanding orange blade ring around the caster for the broadsword sweep. */
public final class BroadswordSweepActor extends RadialActor {
	private static final Vec3 ORANGE = new Vec3(1.0, 0.42, 0.10);

	public BroadswordSweepActor(CombatVfxPayload payload) {
		super(payload);
	}

	@Override protected float duration() { return ChaosBroadswordAnimation.DURATION; }
	@Override protected float radius(float age) { return ChaosBroadswordAnimation.radius(age); }
	@Override protected float opacity(float age) { return ChaosBroadswordAnimation.opacity(age); }
	@Override protected int style() { return 14; }
	@Override protected float styleExtra(float age) { return ChaosBroadswordAnimation.progress(age); }

	@Override protected void details(RenderFrame frame, SlotWriter slot, float age, float ox, float oy, float oz) {
		slot.values[slot.material + 4] = (float) ORANGE.x;
		slot.values[slot.material + 5] = (float) ORANGE.y;
		slot.values[slot.material + 6] = (float) ORANGE.z;
	}
}
