package dev.flake.client;

import dev.flake.duel.ElectricQuake;
import java.util.ArrayList;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One cast's cached terrain supports and seeded channels. No world queries during animation. */
final class GroundLightning {
	static final int GROUPS = 8, PER_GROUP = 8, SEGMENTS = GROUPS * PER_GROUP;
	static final int MOTES = 32;
	static final float DURATION = 0.96f;
	final Segment[] segments = new Segment[SEGMENTS];
	final Mote[] motes = new Mote[MOTES];
	final Vec3[] centres = new Vec3[GROUPS];
	final float[] radii = new float[GROUPS];
	private final ClientLevel level;
	private final Vec3 origin;
	private final float yaw;
	private final double dx, dz;
	private final Random random;
	private int group, used;

	GroundLightning(ClientLevel level, Vec3 origin, float yaw, long seed) {
		this.level = level; this.origin = origin; this.yaw = yaw;
		dx = Math.cos(yaw); dz = Math.sin(yaw); random = new Random(seed);
		Vec3 start = support(0.12, 0, 0);
		if (start == null) return;
		Vec3 prevLeftTip = null, prevRightTip = null;
		for (group = 0; group < GROUPS; group++) {
			used = 0;
			Vec3 first = start;
			for (int j = 0; j < 2; j++) {
				double forward = 0.12 + (group * 2 + j + 1) * (8.88 / 16);
				double side = (random.nextDouble() - 0.5) * (0.34 + forward * 0.085);
				Vec3 next = support(forward, side, start.y);
				if (next == null || !channel(start, next, 0, 0, 0)) { bounds(); return; }
				start = next;
			}
			float power = ElectricQuake.powerAtDistance(forward(start));
			// Asymmetric lateral branches widen and intensify along the corridor (~3.7 blocks full width).
			int primarySide = (group & 1) == 0 ? 1 : -1;
			Vec3 primaryBranch = fork(first, power, primarySide, 0, 1.0f);
			Vec3 secondaryBranch = fork(start, power, -primarySide, group == 0 ? 1 : 0, group == 0 ? 0.68f : 0.92f);
			Vec3 leftTip = primarySide > 0 ? primaryBranch : secondaryBranch;
			Vec3 rightTip = primarySide > 0 ? secondaryBranch : primaryBranch;
			if (leftTip == null) leftTip = first;
			if (rightTip == null) rightTip = start;

			// Short fast-flickering cross-jumps between adjacent branches and previous group tips.
			if (group >= 1 && used < PER_GROUP) {
				Vec3 bridgeFrom = (group & 1) == 0 ? leftTip : rightTip;
				Vec3 bridgeTo = (group & 1) == 0
						? (prevLeftTip != null && random.nextBoolean() ? prevLeftTip : start)
						: (prevRightTip != null && random.nextBoolean() ? prevRightTip : first);
				crossJump(bridgeFrom, bridgeTo, power, group >= 2 ? 1 : 2);
			}
			if (group >= 2 && used < PER_GROUP && leftTip != first && rightTip != start) {
				Vec3 midSpan = leftTip.lerp(rightTip, 0.35 + random.nextDouble() * 0.30)
						.add(0, 0.08 + power * 0.18, 0);
				crossJump((group & 1) == 0 ? rightTip : leftTip, midSpan, power, 1);
			}
			// Pronounced 3D vertical/arched discharges in the high-damage mid-to-far zone.
			if (group >= 3 && used <= PER_GROUP - 2) {
				Vec3 anchor = (group & 1) == 0 && rightTip != start ? rightTip : start;
				double crest = 0.24 + power * (0.48 + random.nextDouble() * 0.46);
				Vec3 peak = anchor.add(
						dx * (0.12 + random.nextDouble() * 0.18) - dz * (random.nextDouble() - 0.5) * 0.38,
						crest,
						dz * (0.12 + random.nextDouble() * 0.18) + dx * (random.nextDouble() - 0.5) * 0.38);
				Vec3 touch = support(Math.min(9.0, forward(anchor) + 0.28 + random.nextDouble() * 0.26),
						Math.clamp(lateral(anchor) + (random.nextDouble() - 0.5) * 0.65, -1.72, 1.72), anchor.y);
				float arcDelay = 0.014f + random.nextFloat() * 0.020f;
				add(anchor, peak, group >= 5 ? 1 : 2, arcDelay, 0.095f + power * 0.045f);
				add(peak, touch != null ? touch : anchor.add(dx * 0.24, 0.04, dz * 0.24),
						group >= 5 ? 1 : 2, arcDelay + 0.010f, 0.095f + power * 0.045f);
			}
			while (used < PER_GROUP && group >= 1) {
				Vec3 base = (used & 1) == 0 ? leftTip : rightTip;
				Vec3 spur = base.add(
						dx * (0.14 + random.nextDouble() * 0.26) - dz * (random.nextDouble() - 0.5) * 0.72,
						0.06 + (0.08 + power * 0.28) * random.nextDouble(),
						dz * (0.14 + random.nextDouble() * 0.26) + dx * (random.nextDouble() - 0.5) * 0.72);
				crossJump(base, spur, power, used >= PER_GROUP - 1 ? 2 : 1);
			}
			prevLeftTip = leftTip;
			prevRightTip = rightTip;

			for (int j = 0; j < 4; j++) {
				if (j == 3 && group < 2) continue;
				Vec3 p = switch (j) {
					case 0 -> first;
					case 1 -> leftTip;
					case 2 -> rightTip;
					default -> start;
				};
				int color = level.getBlockState(BlockPos.containing(origin.add(p).add(0, -0.09, 0)))
						.getMapColor(level, BlockPos.containing(origin.add(p).add(0, -0.09, 0))).col;
				if (color == 0) color = 0x77736b;
				float spread = 1.6f + power * 1.45f;
				motes[group * 4 + j] = new Mote(p, arrival(forward(p)) + j * 0.006f,
						(j == 3 ? 0.19f : 0.16f + power * 0.09f) + random.nextFloat() * 0.07f,
						(random.nextFloat() - 0.5f) * spread + (float) (dx * (0.35f + power * 0.95f)),
						(j == 3 ? 2.2f + power * 0.7f : 2.3f + 3.9f * power) + random.nextFloat() * 1.25f,
						(random.nextFloat() - 0.5f) * spread + (float) (dz * (0.35f + power * 0.95f)),
						j == 3 ? 0.045f + power * 0.035f : 0.015f + power * 0.019f,
						color, random.nextFloat() * 6.28f, j == 3);
			}
			bounds();
		}
	}

	private Vec3 fork(Vec3 root, float power, int side, int detail, float widthScale) {
		if (used > PER_GROUP - 2) return null;
		double reach = (0.68 + power * 1.16) * widthScale * (0.76 + random.nextDouble() * 0.28);
		double forward = forward(root);
		double lat = lateral(root);
		Vec3 bend = support(Math.min(9, forward + 0.14 + random.nextDouble() * 0.28),
				Math.clamp(lat + side * reach * (0.48 + random.nextDouble() * 0.16), -1.78, 1.78), root.y);
		if (bend == null) return null;
		Vec3 tip = support(Math.min(9, forward + 0.34 + random.nextDouble() * 0.42),
				Math.clamp(lat + side * reach, -1.78, 1.78), bend.y);
		if (tip == null) tip = bend;
		double arch = 0.045 + power * (0.14 + random.nextDouble() * 0.12);
		float delay = 0.008f + random.nextFloat() * 0.020f;
		if (!channel(root, bend, detail, delay, arch)) return null;
		if (tip != bend) channel(bend, tip, detail, delay + 0.008f, arch * 0.65);
		return tip;
	}

	private void crossJump(Vec3 a, Vec3 b, float power, int detail) {
		if (used >= PER_GROUP || a == null || b == null) return;
		Vec3 raisedA = a.add(0, 0.03 + power * 0.08 * random.nextDouble(), 0);
		Vec3 raisedB = b.add(0, 0.04 + power * 0.11 * random.nextDouble(), 0);
		float delay = 0.015f + random.nextFloat() * 0.032f;
		float life = 0.065f + random.nextFloat() * 0.045f;
		add(raisedA, raisedB, detail, delay, life);
	}

	float yaw() { return yaw; }

	private double forward(Vec3 p) { return p.x * dx + p.z * dz; }

	private double lateral(Vec3 p) { return -p.x * dz + p.z * dx; }

	private Vec3 support(double forward, double lateral, double referenceY) {
		double x = origin.x + dx * forward - dz * lateral, z = origin.z + dz * forward + dx * lateral;
		var hit = level.clip(new ClipContext(new Vec3(x, origin.y + referenceY + 1.05, z),
				new Vec3(x, origin.y + referenceY - 1.5, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != net.minecraft.core.Direction.UP) return null;
		return hit.getLocation().subtract(origin).add(0, 0.065, 0);
	}

	/** Preserve horizontal runs, add subtle 3D elevation arcs, and keep face-hugging risers at step boundaries. */
	private boolean channel(Vec3 a, Vec3 b, int detail, float delay, double archHeight) {
		var points = new ArrayList<Vec3>();
		points.add(a);
		Vec3 previous = a;
		int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.23));
		for (int i = 1; i <= steps; i++) {
			double t = i / (double) steps;
			Vec3 at = a.lerp(b, t);
			Vec3 next = support(forward(at), lateral(at), previous.y);
			if (next == null || Math.abs(next.y - previous.y) > 1.06) return false;
			if (Math.abs(next.y - previous.y) > 0.10) {
				Vec3 low = previous, high = next;
				for (int k = 0; k < 6; k++) {
					Vec3 mid = low.lerp(high, 0.5);
					Vec3 ground = support(forward(mid), lateral(mid), previous.y);
					if (ground == null) return false;
					if (Math.abs(ground.y - previous.y) < 0.1) low = mid; else high = mid;
				}
				// Bias the riser toward the lower side of the face, avoiding clipping into the block.
				Vec3 edge = next.y > previous.y ? low : high;
				points.add(new Vec3(edge.x, previous.y, edge.z));
				points.add(new Vec3(edge.x, next.y, edge.z));
			} else if (archHeight > 0.001 && i < steps) {
				next = next.add(0, archHeight * Math.sin(t * Math.PI), 0);
			}
			previous = next;
		}
		if (archHeight > 0.001 && points.size() == 1 && Math.abs(b.y - a.y) < 0.12 && used + 2 <= PER_GROUP) {
			Vec3 mid = a.lerp(b, 0.5).add(0, archHeight, 0);
			points.add(mid);
		}
		points.add(b);
		if (used + points.size() - 1 > PER_GROUP) return false;
		float life = 0.13f + random.nextFloat() * 0.075f;
		for (int i = 1; i < points.size(); i++) add(points.get(i - 1), points.get(i), detail, delay, life);
		return true;
	}

	private void add(Vec3 a, Vec3 b, int detail, float delay, float life) {
		float power = ElectricQuake.powerAtDistance((forward(a) + forward(b)) * 0.5);
		float birth = arrival(forward(a)) + delay;
		float end = Math.max(birth + 0.008f, arrival(forward(b)) + delay);
		float widthScale = detail == 2 ? 0.58f : detail == 1 ? 0.68f : delay > 0 ? 0.80f : 1.0f;
		segments[group * PER_GROUP + used++] = new Segment(a, b, birth, end, life, power,
				(0.025f + power * 0.052f) * widthScale,
				random.nextFloat() * 6.28f, detail);
	}

	private void bounds() {
		Vec3 min = new Vec3(1e6, 1e6, 1e6), max = min.scale(-1);
		double groundY = 0;
		int count = 0;
		for (int i = 0; i < used; i++) {
			var s = segments[group * PER_GROUP + i];
			for (var p : new Vec3[]{s.a, s.b}) {
				min = new Vec3(Math.min(min.x, p.x), Math.min(min.y, p.y), Math.min(min.z, p.z));
				max = new Vec3(Math.max(max.x, p.x), Math.max(max.y, p.y), Math.max(max.z, p.z));
			}
			if (s.detail == 0) {
				groundY += (s.a.y + s.b.y) * 0.5;
				count++;
			}
		}
		if (used == 0) return;
		double baseGroundY = count > 0 ? groundY / count : min.y;
		min = min.add(-0.95, -0.35, -0.95); max = max.add(0.95, 1.85, 0.95);
		Vec3 mid = min.lerp(max, 0.5);
		centres[group] = new Vec3(mid.x, baseGroundY, mid.z);
		radii[group] = (float) Math.max(min.distanceTo(max) * 0.54, 2.35);
	}

	// Invert the EXISTING 10-tick server smoothstep; visual propagation never races ahead of hits.
	static float arrival(double forward) {
		double target = Math.clamp((forward - ElectricQuake.MIN_FORWARD) / (9 - ElectricQuake.MIN_FORWARD), 0, 1);
		double low = 0, high = 1;
		for (int i = 0; i < 16; i++) {
			double t = (low + high) * 0.5;
			if (t * t * (3 - 2 * t) < target) low = t; else high = t;
		}
		return (float) ((low + high) * 0.5 * ElectricQuake.PROPAGATION_TICKS / 20.0);
	}

	record Segment(Vec3 a, Vec3 b, float birth, float end, float life, float power, float width, float phase, int detail) {}
	record Mote(Vec3 origin, float birth, float life, float vx, float vy, float vz, float size, int color, float spin, boolean debris) {}
}
