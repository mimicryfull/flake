package dev.flake.build;

import dev.flake.ExampleMod;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Player builds, cached in memory and persisted per world once {@link #bind(Path)} is called. */
public final class LoadoutManager {
	private final Map<UUID, Loadout> loadouts = new HashMap<>();
	private final Map<UUID, LegacyImport> pendingMigrations = new HashMap<>();
	private final Map<UUID, Long> revisions = new HashMap<>();
	private final Map<UUID, LoadoutStore.State> states = new HashMap<>();
	private LoadoutStore store;

	/** Points persistence at a world directory; switching worlds drops only the memory cache. */
	public void bind(Path directory) {
		store = directory == null ? null : new LoadoutStore(directory);
		loadouts.clear();
		pendingMigrations.clear();
		revisions.clear();
		states.clear();
	}

	public Loadout get(UUID player) {
		Loadout cached = loadouts.get(player);
		if (cached != null) return cached;
		if (store != null) {
			var result = store.loadResult(player);
			states.put(player, result.state());
			if (result.build() != null) {
				loadouts.put(player, result.build());
				revisions.putIfAbsent(player, 1L);
				return result.build();
			}
		} else {
			states.putIfAbsent(player, LoadoutStore.State.MISSING);
		}
		return Loadout.defaultLoadout();
	}

	/** Storage truth for the UI: absent build, successfully loaded build, or unreadable file. */
	public LoadoutStore.State state(UUID player) {
		get(player);
		return states.getOrDefault(player, LoadoutStore.State.MISSING);
	}

	/** Monotonic revision used for optimistic concurrency between client and server. */
	public long revision(UUID player) {
		get(player);
		return revisions.getOrDefault(player, 0L);
	}

	/**
	 * Incremental change with an explicit outcome. On a failed disk write the previous build stays
	 * authoritative, so no caller can present an unsaved build as if it were saved.
	 */
	public UpdateResult updateChecked(UUID player, UnaryOperator<Loadout> change) {
		Loadout current = get(player);
		Loadout candidate = change.apply(current);
		if (!candidate.errors().isEmpty()) return new UpdateResult(ApplyStatus.INVALID, candidate, candidate.errors());
		if (candidate.equals(current)) return new UpdateResult(ApplyStatus.OK, current, List.of());
		if (!store(player, candidate)) return new UpdateResult(ApplyStatus.SAVE_FAILED, current, List.of());
		return new UpdateResult(ApplyStatus.OK, candidate, List.of());
	}

	/** Compatibility wrapper: returns the invalid candidate for error text, else the stored build. */
	public Loadout update(UUID player, UnaryOperator<Loadout> change) {
		return updateChecked(player, change).build();
	}

	public enum ApplyStatus { OK, INVALID, STALE, SAVE_FAILED }

	public record UpdateResult(ApplyStatus status, Loadout build, List<String> errors) {
		public boolean ok() { return status == ApplyStatus.OK; }
	}

	public record ApplyResult(ApplyStatus status, long revision, Loadout build, List<String> errors) {
		public boolean ok() { return status == ApplyStatus.OK; }
	}

	/**
	 * Atomic whole-build replacement: either every slot changes together or nothing changes.
	 * A stale revision is rejected so a second client/tab cannot silently overwrite edits.
	 */
	public ApplyResult apply(UUID player, long expectedRevision, Loadout desired) {
		Loadout current = get(player);
		if (desired == null) return new ApplyResult(ApplyStatus.INVALID, revision(player), current, List.of("Білд відсутній"));
		if (!desired.errors().isEmpty()) return new ApplyResult(ApplyStatus.INVALID, revision(player), current, desired.errors());
		if (expectedRevision >= 0 && revision(player) != expectedRevision)
			return new ApplyResult(ApplyStatus.STALE, revision(player), current, List.of());
		if (!store(player, desired)) return new ApplyResult(ApplyStatus.SAVE_FAILED, revision(player), current, List.of());
		return new ApplyResult(ApplyStatus.OK, revision(player), desired, List.of());
	}

	/** Explicit v1 compatibility boundary. Failed/lossy imports never replace the current build. */
	public LegacyImport importLegacy(UUID player, LegacyBuild source) {
		var candidate = new Loadout(source.fighterClass(), source.helmet(), source.chestplate(), source.leggings(),
				source.boots(), source.weapon(), source.accessories(), source.passiveItem(), source.utilityItem());
		var result = new LegacyImport(source, candidate);
		if (result.accepted()) { store(player, candidate); pendingMigrations.remove(player); }
		else pendingMigrations.put(player, result);
		return result;
	}

	public Optional<LegacyImport> pendingMigration(UUID player) { return Optional.ofNullable(pendingMigrations.get(player)); }

	/** Only explicit conflict resolution may discard the retained v1 source. */
	public boolean resolveMigration(UUID player, Loadout replacement) {
		if (!replacement.errors().isEmpty()) return false;
		if (!store(player, replacement)) return false;
		pendingMigrations.remove(player);
		return true;
	}

	public record LegacyBuild(Loadout.FighterClass fighterClass, Loadout.ArmorMaterial helmet,
			Loadout.ArmorMaterial chestplate, Loadout.ArmorMaterial leggings, Loadout.ArmorMaterial boots,
			Loadout.Weapon weapon, List<Loadout.Accessory> accessories, Loadout.PassiveItem passiveItem,
			Loadout.UtilityItem utilityItem) {
		public LegacyBuild { accessories = List.copyOf(accessories); }
	}
	public record LegacyImport(LegacyBuild original, Loadout candidate) {
		public boolean accepted() { return candidate.errors().isEmpty(); }
	}

	/** Persists first, then publishes; a failed write must not become authoritative in memory. */
	private boolean store(UUID player, Loadout build) {
		if (store != null) {
			try {
				store.save(player, build);
			} catch (IOException failure) {
				ExampleMod.LOGGER.warn("Could not save flake build for {}", player, failure);
				return false;
			}
			states.put(player, LoadoutStore.State.LOADED);
		}
		loadouts.put(player, build);
		revisions.merge(player, 1L, Long::sum);
		return true;
	}

	public void remove(UUID player) {
		loadouts.remove(player);
		pendingMigrations.remove(player);
		revisions.remove(player);
		states.remove(player);
	}

	public void clear() {
		loadouts.clear();
		pendingMigrations.clear();
		revisions.clear();
		states.clear();
	}
}