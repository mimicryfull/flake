package dev.flake.features.armorsets;

import dev.flake.Flake;
import dev.flake.content.ItemRegistration;
import dev.flake.features.duel.DuelRuntime;
import dev.flake.features.loadout.Loadout;
import dev.flake.features.loadout.Loadout.ArmorMaterial;
import dev.flake.features.loadout.Loadout.ArmorSlot;
import dev.flake.features.training.TrainingDummies;
import dev.flake.render.vfx.CombatVfxPayload;
import dev.flake.render.vfx.CombatVisuals;
import dev.flake.runtime.ServerState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared infrastructure for Iron-tier custom armor sets (Phoenix, Barrel, Wizard, Hydra):
 * item registration, duel & worn full-set detection, attribute syncing, and opponent queries.
 */
public final class CustomArmorSet {
	private final String id;
	private final ArmorMaterial loadoutMaterial;
	private final EnumMap<ArmorSlot, Item> pieces = new EnumMap<>(ArmorSlot.class);
	private final ServerState<Set<UUID>> duelFullSets = new ServerState<>(HashSet::new);

	public CustomArmorSet(String id, ArmorMaterial loadoutMaterial) {
		this.id = id;
		this.loadoutMaterial = loadoutMaterial;
	}

	public ArmorMaterial material() { return loadoutMaterial; }

	public void registerItems() {
		var asset = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Flake.MOD_ID, id));
		var mcMaterial = new net.minecraft.world.item.equipment.ArmorMaterial(
				15, ArmorMaterials.makeDefense(2, 5, 6, 2, 15), 9,
				SoundEvents.ARMOR_EQUIP_IRON, 0.0f, 0.0f, ItemTags.REPAIRS_IRON_ARMOR, asset);
		pieces.put(ArmorSlot.HELMET, registerPiece(id + "_helmet", mcMaterial, ArmorType.HELMET));
		pieces.put(ArmorSlot.CHESTPLATE, registerPiece(id + "_chestplate", mcMaterial, ArmorType.CHESTPLATE));
		pieces.put(ArmorSlot.LEGGINGS, registerPiece(id + "_leggings", mcMaterial, ArmorType.LEGGINGS));
		pieces.put(ArmorSlot.BOOTS, registerPiece(id + "_boots", mcMaterial, ArmorType.BOOTS));
	}

	private static Item registerPiece(String path, net.minecraft.world.item.equipment.ArmorMaterial material, ArmorType type) {
		return ItemRegistration.register(Identifier.fromNamespaceAndPath(Flake.MOD_ID, path),
				key -> new Item(new Item.Properties().setId(key).humanoidArmor(material, type)));
	}

	public Item itemFor(ArmorSlot slot) {
		Item item = pieces.get(slot);
		if (item != null) return item;
		return switch (slot) {
			case HELMET -> Items.IRON_HELMET;
			case CHESTPLATE -> Items.IRON_CHESTPLATE;
			case LEGGINGS -> Items.IRON_LEGGINGS;
			case BOOTS -> Items.IRON_BOOTS;
		};
	}

	public boolean isWearingFullSet(ServerPlayer player) {
		if (player == null || pieces.isEmpty()) return false;
		return player.getItemBySlot(EquipmentSlot.HEAD).is(pieces.get(ArmorSlot.HELMET))
				&& player.getItemBySlot(EquipmentSlot.CHEST).is(pieces.get(ArmorSlot.CHESTPLATE))
				&& player.getItemBySlot(EquipmentSlot.LEGS).is(pieces.get(ArmorSlot.LEGGINGS))
				&& player.getItemBySlot(EquipmentSlot.FEET).is(pieces.get(ArmorSlot.BOOTS));
	}

	public void applyKit(ServerPlayer player, Loadout loadout) {
		var set = duelFullSets.of(player);
		if (loadout.hasFullSet(loadoutMaterial)) set.add(player.getUUID());
		else set.remove(player.getUUID());
	}

	public void resetPlayer(ServerPlayer player) {
		duelFullSets.of(player).remove(player.getUUID());
	}

	public void clearServer(net.minecraft.server.MinecraftServer server) {
		duelFullSets.remove(server);
	}

	public boolean hasActiveFullSet(ServerPlayer player) {
		if (player == null || !player.isAlive() || player.isSpectator()) return false;
		UUID playerId = player.getUUID();
		if (duelFullSets.of(player).contains(playerId)) return true;
		return !DuelRuntime.fighting(playerId) && isWearingFullSet(player);
	}

	public static void syncAttribute(
			ServerPlayer player,
			Holder<Attribute> attribute,
			Identifier modifierId,
			double amount,
			AttributeModifier.Operation operation,
			boolean active
	) {
		var instance = player.getAttribute(attribute);
		if (instance == null) return;
		var existing = instance.getModifier(modifierId);
		if (!active) {
			if (existing != null) instance.removeModifier(modifierId);
			return;
		}
		if (existing != null && Double.compare(existing.amount(), amount) == 0 && existing.operation() == operation) {
			return;
		}
		if (existing != null) instance.removeModifier(modifierId);
		instance.addTransientModifier(new AttributeModifier(modifierId, amount, operation));
	}

	public static List<LivingEntity> targetsAround(ServerPlayer wearer, ServerLevel level, double radius) {
		var result = new ArrayList<LivingEntity>();
		Vec3 origin = wearer.position();
		double radiusSq = radius * radius;
		UUID wearerId = wearer.getUUID();
		boolean inDuel = DuelRuntime.fighting(wearerId);
		for (var other : level.players()) {
			if (other == wearer || !other.isAlive() || other.isSpectator()) continue;
			if (inDuel && !DuelRuntime.opponents(wearerId, other.getUUID())) continue;
			if (!inDuel && DuelRuntime.fighting(other.getUUID())) continue;
			if (other.position().distanceToSqr(origin) <= radiusSq) result.add(other);
		}
		if (!inDuel) {
			var box = new AABB(origin.x - radius - 1, origin.y - radius - 1, origin.z - radius - 1,
					origin.x + radius + 1, origin.y + radius + 1, origin.z + radius + 1);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, box, Mob::isAlive)) {
				if (mob.position().distanceToSqr(origin) <= radiusSq) {
					if (mob.entityTags().contains(TrainingDummies.TAG) && mob.isNoAi()) TrainingDummies.prepare(mob);
					result.add(mob);
				}
			}
		}
		return result;
	}

	public static void knockAway(Vec3 origin, LivingEntity target, double horizontalStrength, double verticalBoost) {
		Vec3 diff = target.position().subtract(origin).multiply(1, 0, 1);
		Vec3 dir = diff.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : diff.normalize();
		Vec3 velocity = target.getDeltaMovement().add(dir.scale(horizontalStrength).add(0, verticalBoost, 0));
		target.setDeltaMovement(velocity);
		if (target instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
		else target.syncVelocity = true;
	}

	public static void broadcastAuraState(ServerPlayer player, CombatVfxPayload.Phase phase, float intensity, int leaseTicks) {
		Vec3 pos = player.position();
		CombatVisuals.broadcast(player, new CombatVfxPayload(pos, player.getId(), phase, intensity, -1, pos), pos, leaseTicks);
	}
}
