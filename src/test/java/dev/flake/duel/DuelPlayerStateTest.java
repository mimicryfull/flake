package dev.flake.duel;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuelPlayerStateTest {
	@BeforeAll
	static void setUpAll() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
	}
	private static CompoundTag valid(UUID owner) {
		var data = new CompoundTag();
		data.putInt("version", 1); data.putString("owner", owner.toString()); data.putString("token", UUID.randomUUID().toString());
		data.putString("dimension", "minecraft:overworld"); data.putString("fallbackDimension", "minecraft:overworld");
		data.putDouble("x", 1); data.putDouble("y", 70); data.putDouble("z", 2); data.putFloat("yaw", 10); data.putFloat("pitch", 20);
		var point = new CompoundTag();
		point.putDouble("x", 0); point.putDouble("y", 64); point.putDouble("z", 0); point.putFloat("yaw", 0); point.putFloat("pitch", 0);
		data.put("fallback", point);
		data.putInt("selected", 2); data.put("items", ItemStack.OPTIONAL_CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.of(ItemStack.EMPTY)).getOrThrow());
		data.put("effects", new ListTag());
		data.put("abilities", Abilities.Packed.CODEC.encodeStart(NbtOps.INSTANCE, new Abilities.Packed(false, false, false, false, true, 0.05f, 0.1f)).getOrThrow());
		data.putInt("gameMode", 0); data.putFloat("health", 14); data.putFloat("absorption", 2); data.putFloat("progress", 0.25f);
		data.putInt("level", 7); data.putInt("experience", 120); data.putInt("air", 300); data.putInt("fire", 0); data.putInt("frozen", 0);
		var food = new CompoundTag();
		food.putInt("foodLevel", 13); food.putInt("foodTickTimer", 0); food.putFloat("foodSaturationLevel", 3); food.putFloat("foodExhaustionLevel", 2);
		data.put("food", food);
		return data;
	}

	@Test void validatesAndKeepsAnIndependentOriginal() throws IOException {
		var owner = UUID.randomUUID(); var data = valid(owner);
		var state = DuelPlayerState.read(owner, data, NbtOps.INSTANCE);
		data.putDouble("x", 500);
		assertEquals(1, state.position().x());
		assertEquals("minecraft:overworld", state.dimension());
		assertTrue(state.receipt().startsWith("flake_restored_"));
	}

	@Test void rejectsWrongOwnerAndMissingOrInvalidStateBeforeRestoration() {
		var owner = UUID.randomUUID();
		assertThrows(IOException.class, () -> DuelPlayerState.read(UUID.randomUUID(), valid(owner), NbtOps.INSTANCE));
		var invalid = valid(owner); invalid.putFloat("health", Float.NaN);
		assertThrows(IOException.class, () -> DuelPlayerState.read(owner, invalid, NbtOps.INSTANCE));
		var incomplete = valid(owner); incomplete.remove("absorption");
		assertThrows(IOException.class, () -> DuelPlayerState.read(owner, incomplete, NbtOps.INSTANCE));
		var wrongSlot = valid(owner); wrongSlot.putInt("selected", 9);
		assertThrows(IOException.class, () -> DuelPlayerState.read(owner, wrongSlot, NbtOps.INSTANCE));
	}
}
