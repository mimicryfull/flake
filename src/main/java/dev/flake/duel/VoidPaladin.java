package dev.flake.duel;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomData;

/**
 * «Порожній Паладин»: меч між діамантовим і незеритовим рівнем.
 * Кожен 4-й удар — критичний (+50% шкоди), з ефектами та плаваючим написом «CRIT!».
 * Коли наступний удар буде критичним, меч «заряджається» (блиск + тремтіння в хотбарі).
 */
public final class VoidPaladin {
	public static final String CRIT_KEY = "flake_crit_ready";
	public static final int CRIT_EVERY = CritCounter.EVERY;
	public static final double CRIT_BONUS = 0.5;
	private static final int LABEL_TICKS = 20;

	private static final String MODEL_PATH = "void_paladin";
	private static final Identifier MODEL = Identifier.fromNamespaceAndPath("flake", MODEL_PATH);
	private static final Identifier CRIT_MOD = Identifier.fromNamespaceAndPath("flake", "void_paladin_crit_damage");

	/** Confirmed hits only: incremented in AFTER_DAMAGE, never on the attack attempt. */
	private static final Map<UUID, Integer> HITS = new HashMap<>();
	private static final Set<UUID> PENDING_CRIT_MODIFIER = new HashSet<>();
	/** Attack in flight for this tick; value says whether that swing carries the crit bonus. */
	private static final Map<UUID, Boolean> PENDING_SWING = new HashMap<>();
	private static final List<Label> LABELS = new ArrayList<>();

	private static final Method SET_TEXT_OPACITY = opacitySetter();

	private VoidPaladin() {}

	public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, MODEL);
	public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, KEY,
			new Item(new Item.Properties().setId(KEY).sword(ToolMaterial.DIAMOND, 3.5f, -2.4f)));

	public static ItemStack stack() {
		return new ItemStack(ITEM);
	}

	public static boolean isVoidPaladin(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ITEM);
	}

	/** Ensures the item class is initialized during SliceRegistry bootstrap (item registration). */
	public static void install() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer attacker)) return InteractionResult.PASS;
			if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (!isVoidPaladin(attacker.getMainHandItem())) return InteractionResult.PASS;
			if (!(entity instanceof LivingEntity target) || !validTarget(attacker, target)) return InteractionResult.PASS;
			swing(attacker, target);
			return InteractionResult.PASS;
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			// Only a server-confirmed hit advances the counter; blocked or zero-damage swings are ignored.
			if (blocked || damageTaken <= 0.0f) return;
			if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker) return;
			Boolean crit = PENDING_SWING.remove(attacker.getUUID());
			if (crit == null) return;
			// Target legality was already checked on the attempt; re-checking here would drop a lethal
			// hit because the duel leaves FIGHTING during the death handling inside hurtServer.
			confirmHit(attacker, entity, crit);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// A swing that produced no damage never confirms; its crit modifier must not leak into later hits.
			PENDING_SWING.clear();
			clearCritModifiers(server);
			tickLabels(server);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cleanup(handler.player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (var label : LABELS) label.entity.discard();
			LABELS.clear();
			HITS.clear();
			PENDING_CRIT_MODIFIER.clear();
			PENDING_SWING.clear();
		});
	}

	public static void reset(ServerPlayer player) {
		cleanup(player);
	}

	private static void cleanup(ServerPlayer player) {
		var id = player.getUUID();
		HITS.remove(id);
		PENDING_CRIT_MODIFIER.remove(id);
		PENDING_SWING.remove(id);
		removeCritModifier(player);
		clearCharge(player);
		for (Iterator<Label> it = LABELS.iterator(); it.hasNext(); ) {
			var label = it.next();
			if (!id.equals(label.owner)) continue;
			label.entity.discard();
			it.remove();
		}
	}

	/**
	 * Attack attempt: the counter is NOT touched here. If this would be the 4th confirmed hit, the crit
	 * damage modifier is applied so the hit itself is boosted; {@link #confirmHit} then commits the count.
	 */
	private static void swing(ServerPlayer attacker, LivingEntity target) {
		var id = attacker.getUUID();
		boolean crit = CritCounter.nextIsCrit(HITS.getOrDefault(id, 0));
		if (crit) applyCritDamage(attacker);
		PENDING_SWING.put(id, crit);
	}

	private static void confirmHit(ServerPlayer attacker, LivingEntity target, boolean crit) {
		var id = attacker.getUUID();
		int hits = CritCounter.afterConfirmedHit(HITS.getOrDefault(id, 0));
		HITS.put(id, hits);
		if (crit) {
			clearCharge(attacker);
			effects(target);
			spawnLabel(attacker, target);
			ShockwaveVisuals.crit(attacker, target);
		}
		var stack = attacker.getMainHandItem();
		if (CritCounter.showsCharge(hits)) setCharge(attacker, stack);
		else if (clearChargeIfSet(attacker, stack)) syncInventory(attacker);
	}

	private static boolean validTarget(ServerPlayer player, LivingEntity living) {
		if (!living.isAlive() || living == player) return false;
		if (DuelRuntime.fighting(player.getUUID())) {
			return living instanceof ServerPlayer other && DuelRuntime.opponents(player.getUUID(), other.getUUID());
		}
		return living instanceof Player || living instanceof Mob mob && mob.entityTags().contains("flake_test_dummy");
	}

	private static void applyCritDamage(ServerPlayer player) {
		var damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage == null) return;
		damage.addTransientModifier(new AttributeModifier(CRIT_MOD, CRIT_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		PENDING_CRIT_MODIFIER.add(player.getUUID());
	}

	private static void clearCritModifiers(net.minecraft.server.MinecraftServer server) {
		if (PENDING_CRIT_MODIFIER.isEmpty()) return;
		for (Iterator<UUID> it = PENDING_CRIT_MODIFIER.iterator(); it.hasNext(); ) {
			var id = it.next();
			var player = server.getPlayerList().getPlayer(id);
			if (player != null) removeCritModifier(player);
			it.remove();
		}
	}

	private static void removeCritModifier(ServerPlayer player) {
		var damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) damage.removeModifier(CRIT_MOD);
	}

	private static void setCharge(ServerPlayer player, ItemStack stack) {
		var data = stack.get(DataComponents.CUSTOM_DATA);
		boolean charged = data != null && data.copyTag().getBooleanOr(CRIT_KEY, false);
		boolean glint = Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		if (charged && glint) return;
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		tag.putBoolean(CRIT_KEY, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		syncInventory(player);
	}

	private static void clearCharge(ServerPlayer player) {
		boolean changed = clearChargeIfSet(player, player.getMainHandItem());
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			var stack = inventory.getItem(slot);
			if (isVoidPaladin(stack)) changed |= clearChargeIfSet(player, stack);
		}
		if (changed) syncInventory(player);
	}

	private static boolean clearChargeIfSet(ServerPlayer player, ItemStack stack) {
		var data = stack.get(DataComponents.CUSTOM_DATA);
		boolean charged = data != null && data.copyTag().getBooleanOr(CRIT_KEY, false);
		boolean glint = Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		if (!charged && !glint) return false;
		if (data != null) {
			CompoundTag tag = data.copyTag();
			tag.remove(CRIT_KEY);
			if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
			else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
		stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		return true;
	}

	private static void syncInventory(ServerPlayer player) {
		player.inventoryMenu.broadcastChanges();
		if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
	}

	private static void effects(LivingEntity target) {
		if (!(target.level() instanceof ServerLevel level)) return;
		double x = target.getX(), y = target.getY() + target.getBbHeight() * 0.6, z = target.getZ();
		level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.65f, 1.35f);
	}

	private static void spawnLabel(ServerPlayer owner, LivingEntity target) {
		if (!(target.level() instanceof ServerLevel level)) return;
		double x = target.getX();
		double y = target.getY() + target.getBbHeight() * 1.35;
		double z = target.getZ();

		var tag = new CompoundTag();
		tag.putString("id", "minecraft:text_display");
		tag.put("text", componentTag(Component.translatable("flake.crit.text")));
		tag.putString("billboard", "center");
		tag.putBoolean("shadow", true);
		tag.putInt("background", 0);
		tag.putByte("text_opacity", (byte) -1);

		var entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(
				EntityTypes.TEXT_DISPLAY, tag, level, EntitySpawnReason.COMMAND, EntityProcessor.NOP);
		if (!(entity instanceof Display.TextDisplay display)) {
			if (entity != null) entity.discard();
			return;
		}
		display.setPos(x, y, z);
		if (!level.addFreshEntity(display)) {
			display.discard();
			return;
		}
		LABELS.add(new Label(display, owner.getUUID(), x, y, z));
	}

	private static Tag componentTag(Component component) {
		return ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component)
				.result().orElseGet(() -> net.minecraft.nbt.StringTag.valueOf("CRIT!"));
	}

	private static void tickLabels(net.minecraft.server.MinecraftServer server) {
		if (LABELS.isEmpty()) return;
		for (Iterator<Label> it = LABELS.iterator(); it.hasNext(); ) {
			var label = it.next();
			if (label.entity.isRemoved()) {
				it.remove();
				continue;
			}
			var owner = server.getPlayerList().getPlayer(label.owner);
			if (owner == null) {
				label.entity.discard();
				it.remove();
				continue;
			}
			int age = ++label.age;
			label.entity.setPos(label.x, label.y + age * 0.045, label.z);
			setOpacity(label.entity, (byte) Math.max(0, 255 - age * 12));
			if (age >= LABEL_TICKS) {
				label.entity.discard();
				it.remove();
			}
		}
	}

	private static Method opacitySetter() {
		try {
			var method = Display.TextDisplay.class.getDeclaredMethod("setTextOpacity", byte.class);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}

	private static void setOpacity(Display.TextDisplay display, byte opacity) {
		if (SET_TEXT_OPACITY == null) return;
		try {
			SET_TEXT_OPACITY.invoke(display, opacity);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
		}
	}

	private static final class Label {
		final Display.TextDisplay entity;
		final UUID owner;
		final double x, y, z;
		int age;

		Label(Display.TextDisplay entity, UUID owner, double x, double y, double z) {
			this.entity = entity;
			this.owner = owner;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
}
