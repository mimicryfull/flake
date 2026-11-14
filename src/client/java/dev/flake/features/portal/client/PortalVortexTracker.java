package dev.flake.features.portal.client;

import dev.flake.features.portal.PortalVortexBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Tracks loaded portal blocks on the client and merges touching portal blocks into
 * unified multi-block clusters so the vortex shader spans seamlessly across any shape.
 */
public final class PortalVortexTracker {
	public record ExposedFace(BlockPos pos, BlockPos neighbor, Direction dir) {}

	public static final class PortalCluster {
		public final float centerX, centerY, centerZ;
		public final float halfX, halfY, halfZ;
		public final float seed;
		public final List<ExposedFace> faces;

		PortalCluster(float centerX, float centerY, float centerZ,
				float halfX, float halfY, float halfZ, float seed, List<ExposedFace> faces) {
			this.centerX = centerX;
			this.centerY = centerY;
			this.centerZ = centerZ;
			this.halfX = halfX;
			this.halfY = halfY;
			this.halfZ = halfZ;
			this.seed = seed;
			this.faces = faces;
		}
	}

	private static final Set<BlockPos> TRACKED = new HashSet<>();
	private static final List<PortalCluster> CLUSTERS = new ArrayList<>();
	private static ClientLevel currentLevel;
	private static boolean dirty;

	private PortalVortexTracker() {}

	public static void onLoad(PortalVortexBlockEntity be, ClientLevel level) {
		checkWorld(level);
		if (TRACKED.add(be.getBlockPos().immutable())) {
			dirty = true;
		}
	}

	public static void onUnload(PortalVortexBlockEntity be) {
		if (TRACKED.remove(be.getBlockPos())) {
			dirty = true;
		}
	}

	public static void checkWorld(ClientLevel level) {
		if (currentLevel != level) {
			clear();
			currentLevel = level;
		}
	}

	public static void clear() {
		TRACKED.clear();
		CLUSTERS.clear();
		currentLevel = null;
		dirty = false;
	}

	public static List<PortalCluster> clusters() {
		if (dirty) {
			rebuildClusters();
			dirty = false;
		}
		return CLUSTERS;
	}

	private static void rebuildClusters() {
		CLUSTERS.clear();
		if (TRACKED.isEmpty()) return;

		Set<BlockPos> visited = new HashSet<>(TRACKED.size());
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Direction[] dirs = Direction.values();

		for (BlockPos start : TRACKED) {
			if (!visited.add(start)) continue;

			int minX = start.getX(), maxX = minX;
			int minY = start.getY(), maxY = minY;
			int minZ = start.getZ(), maxZ = minZ;

			List<ExposedFace> faces = new ArrayList<>();
			queue.clear();
			queue.add(start);

			while (!queue.isEmpty()) {
				BlockPos cur = queue.poll();
				int x = cur.getX(), y = cur.getY(), z = cur.getZ();
				if (x < minX) minX = x;
				if (x > maxX) maxX = x;
				if (y < minY) minY = y;
				if (y > maxY) maxY = y;
				if (z < minZ) minZ = z;
				if (z > maxZ) maxZ = z;

				for (Direction dir : dirs) {
					BlockPos nb = cur.relative(dir);
					if (TRACKED.contains(nb)) {
						if (visited.add(nb)) {
							queue.add(nb);
						}
					} else {
						faces.add(new ExposedFace(cur, nb, dir));
					}
				}
			}

			if (faces.isEmpty()) continue;

			float cx = (minX + maxX + 1) * 0.5f;
			float cy = (minY + maxY + 1) * 0.5f;
			float cz = (minZ + maxZ + 1) * 0.5f;
			float hx = (maxX - minX + 1) * 0.5f;
			float hy = (maxY - minY + 1) * 0.5f;
			float hz = (maxZ - minZ + 1) * 0.5f;

			long hash = (minX * 73856093L) ^ (minY * 19349663L) ^ (minZ * 83492791L);
			float seed = 0.20f + 0.78f * (((hash >>> 8) & 0xFFFF) / 65535.0f);

			CLUSTERS.add(new PortalCluster(cx, cy, cz, hx, hy, hz, seed, faces));
		}
	}
}
