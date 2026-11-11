package dev.flake.features.duel.recovery;

import com.mojang.serialization.DynamicOps;
import dev.flake.features.arena.Arena;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Personal state, not duel gear. Restoration replaces slots; it never adds/merges items. */
public final class DuelPlayerState {
	private final CompoundTag data;
	private final InventorySnapshot inventory;

	private DuelPlayerState(CompoundTag data, InventorySnapshot inventory) {
		this.data = data.copy();
		this.inventory = inventory;
	}

	public static CompoundTag capture(ServerPlayer player, Arena arena) throws IOException {
		var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		var data = new CompoundTag();
		data.putInt("version", 1);
		data.putString("owner", player.getUUID().toString());
		data.putString("token", UUID.randomUUID().toString());
		data.putString("dimension", player.level().dimension().identifier().toString());
		writePosition(data, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
		data.putString("fallbackDimension", arena.dimension());
		var fallback = new CompoundTag();
		var point = arena.lobby();
		writePosition(fallback, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
		data.put("fallback", fallback);
		var snapshot = InventorySnapshot.capture(player);
		data.putInt("selected", snapshot.selectedSlot());
		var items = new ArrayList<ItemStack>(snapshot.size());
		for (int slot = 0; slot < snapshot.size(); slot++) items.add(snapshot.item(slot));
		data.put("items", encode(ops, ItemStack.OPTIONAL_CODEC.listOf(), items));
		data.put("effects", encode(ops, MobEffectInstance.CODEC.listOf(),
				player.getActiveEffects().stream().map(MobEffectInstance::new).toList()));
		data.put("abilities", encode(ops, Abilities.Packed.CODEC, player.getAbilities().pack()));
		var food = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
		player.getFoodData().addAdditionalSaveData(food);
		data.put("food", food.buildResult());
		data.putInt("gameMode", player.gameMode.getGameModeForPlayer().getId());
		data.putFloat("health", player.getHealth());
		data.putFloat("absorption", player.getAbsorptionAmount());
		data.putInt("level", player.experienceLevel);
		data.putInt("experience", player.totalExperience);
		data.putFloat("progress", player.experienceProgress);
		data.putInt("fire", player.getRemainingFireTicks());
		data.putInt("air", player.getAirSupply());
		data.putInt("frozen", player.getTicksFrozen());
		return data;
	}

	public static DuelPlayerState read(UUID owner, CompoundTag data, DynamicOps<Tag> ops) throws IOException {
		try {
			if (data.getInt("version").orElseThrow() != 1) throw new IllegalArgumentException("Unsupported version");
			if (!owner.toString().equals(data.getString("owner").orElseThrow())) throw new IllegalArgumentException("Wrong owner");
			UUID.fromString(data.getString("token").orElseThrow());
			position(data);
			position(data.getCompound("fallback").orElseThrow());
			if (data.getString("dimension").orElseThrow().isBlank()) throw new IllegalArgumentException("Missing dimension");
			if (data.getString("fallbackDimension").orElseThrow().isBlank()) throw new IllegalArgumentException("Missing fallback dimension");
			int mode = data.getInt("gameMode").orElseThrow();
			if (mode < 0 || mode > 3) throw new IllegalArgumentException("Invalid game mode");
			float health = data.getFloat("health").orElseThrow(), progress = data.getFloat("progress").orElseThrow();
			if (!Float.isFinite(health) || health <= 0 || !Float.isFinite(progress) || progress < 0 || progress > 1)
				throw new IllegalArgumentException("Invalid personal state");
			float absorption = data.getFloat("absorption").orElseThrow();
			if (!Float.isFinite(absorption) || absorption < 0 || data.getInt("level").orElseThrow() < 0
					|| data.getInt("experience").orElseThrow() < 0) throw new IllegalArgumentException("Invalid experience / absorption");
			data.getInt("fire").orElseThrow(); data.getInt("air").orElseThrow(); data.getInt("frozen").orElseThrow();
			var items = ItemStack.OPTIONAL_CODEC.listOf().parse(ops, data.get("items")).getOrThrow();
			if (items.size() > 128) throw new IllegalArgumentException("Too many inventory slots");
			// Validate all codec-backed fields before mutating a live player.
			MobEffectInstance.CODEC.listOf().parse(ops, data.get("effects")).getOrThrow();
			Abilities.Packed.CODEC.parse(ops, data.get("abilities")).getOrThrow();
			var food = data.getCompound("food").orElseThrow();
			int foodLevel = food.getInt("foodLevel").orElseThrow();
			float saturation = food.getFloat("foodSaturationLevel").orElseThrow(), exhaustion = food.getFloat("foodExhaustionLevel").orElseThrow();
			food.getInt("foodTickTimer").orElseThrow();
			if (foodLevel < 0 || foodLevel > 20 || !Float.isFinite(saturation) || saturation < 0
					|| !Float.isFinite(exhaustion) || exhaustion < 0) throw new IllegalArgumentException("Invalid food data");
			return new DuelPlayerState(data, new InventorySnapshot(owner, items, data.getInt("selected").orElseThrow()));
		} catch (RuntimeException exception) {
			throw new IOException("Invalid personal state for " + owner, exception);
		}
	}

	public UUID token() { return UUID.fromString(data.getString("token").orElseThrow()); }
	public String receipt() { return "flake_restored_" + token(); }
	public String dimension() { return data.getString("dimension").orElseThrow(); }
	public Arena.Position position() { return position(data); }
	public String fallbackDimension() { return data.getString("fallbackDimension").orElseThrow(); }
	public Arena.Position fallback() { return position(data.getCompound("fallback").orElseThrow()); }

	public void restore(ServerPlayer player) {
		if (!inventory.owner().equals(player.getUUID())) throw new IllegalArgumentException("Wrong player");
		if (inventory.size() != player.getInventory().getContainerSize()) throw new IllegalStateException("Inventory size changed");
		var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		player.stopUsingItem();
		player.closeContainer();
		player.inventoryMenu.setCarried(ItemStack.EMPTY);
		player.getInventory().clearContent();
		player.removeAllEffects();
		inventory.restoreTo(player);
		player.setGameMode(GameType.byId(data.getInt("gameMode").orElseThrow()));
		player.getAbilities().apply(Abilities.Packed.CODEC.parse(ops, data.get("abilities")).getOrThrow());
		for (var effect : MobEffectInstance.CODEC.listOf().parse(ops, data.get("effects")).getOrThrow()) player.addEffect(effect);
		player.setHealth(data.getFloat("health").orElseThrow());
		player.setAbsorptionAmount(data.getFloat("absorption").orElseThrow());
		player.getFoodData().readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING,
				player.registryAccess(), data.getCompound("food").orElseThrow()));
		player.experienceLevel = data.getInt("level").orElseThrow();
		player.totalExperience = data.getInt("experience").orElseThrow();
		player.experienceProgress = data.getFloat("progress").orElseThrow();
		player.setRemainingFireTicks(data.getInt("fire").orElseThrow());
		player.setAirSupply(data.getInt("air").orElseThrow());
		player.setTicksFrozen(data.getInt("frozen").orElseThrow());
		player.fallDistance = 0;
		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = true;
		player.onUpdateAbilities();
		player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
	}

	private static <T> Tag encode(DynamicOps<Tag> ops, com.mojang.serialization.Codec<T> codec, T value) throws IOException {
		return codec.encodeStart(ops, value).result().orElseThrow(() -> new IOException("Cannot encode personal state"));
	}

	private static void writePosition(CompoundTag data, double x, double y, double z, float yaw, float pitch) {
		data.putDouble("x", x); data.putDouble("y", y); data.putDouble("z", z);
		data.putFloat("yaw", yaw); data.putFloat("pitch", pitch);
	}

	private static Arena.Position position(CompoundTag data) {
		return new Arena.Position(data.getDouble("x").orElseThrow(), data.getDouble("y").orElseThrow(),
				data.getDouble("z").orElseThrow(), data.getFloat("yaw").orElseThrow(), data.getFloat("pitch").orElseThrow());
	}
}
