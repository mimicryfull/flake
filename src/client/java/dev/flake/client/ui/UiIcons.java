package dev.flake.client.ui;

import dev.flake.build.Loadout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class UiIcons {
	private static final ItemStack IRON_ARMOR = new ItemStack(Items.IRON_CHESTPLATE);
	private static final ItemStack DIAMOND_ARMOR = new ItemStack(Items.DIAMOND_CHESTPLATE);
	private static final ItemStack IRON_SWORD = new ItemStack(Items.IRON_SWORD);
	private static final ItemStack STONE_AXE = new ItemStack(Items.STONE_AXE);
	private static final ItemStack DIAMOND_SWORD = new ItemStack(Items.DIAMOND_SWORD);
	private static final ItemStack BOW = new ItemStack(Items.BOW);
	private static final ItemStack CROSSBOW = new ItemStack(Items.CROSSBOW);
	private static final ItemStack HAMMER = dev.flake.duel.ControlHammer.stack();
	private static final ItemStack SPLASH_HEAL = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING);

	private UiIcons() {}

	public static ItemStack armorItem(String id) {
		return "DIAMOND".equals(id) ? DIAMOND_ARMOR : IRON_ARMOR;
	}

	public static ItemStack armorPieceItem(Loadout.ArmorSlot slot, Loadout.ArmorMaterial material) {
		return switch (material) {
			case NONE -> ItemStack.EMPTY;
			case CHAINMAIL -> new ItemStack(switch (slot) {
				case HELMET -> Items.CHAINMAIL_HELMET;
				case CHESTPLATE -> Items.CHAINMAIL_CHESTPLATE;
				case LEGGINGS -> Items.CHAINMAIL_LEGGINGS;
				case BOOTS -> Items.CHAINMAIL_BOOTS;
			});
			case GOLD -> new ItemStack(switch (slot) {
				case HELMET -> Items.GOLDEN_HELMET;
				case CHESTPLATE -> Items.GOLDEN_CHESTPLATE;
				case LEGGINGS -> Items.GOLDEN_LEGGINGS;
				case BOOTS -> Items.GOLDEN_BOOTS;
			});
			case IRON -> new ItemStack(switch (slot) {
				case HELMET -> Items.IRON_HELMET;
				case CHESTPLATE -> Items.IRON_CHESTPLATE;
				case LEGGINGS -> Items.IRON_LEGGINGS;
				case BOOTS -> Items.IRON_BOOTS;
			});
			case DIAMOND -> new ItemStack(switch (slot) {
				case HELMET -> Items.DIAMOND_HELMET;
				case CHESTPLATE -> Items.DIAMOND_CHESTPLATE;
				case LEGGINGS -> Items.DIAMOND_LEGGINGS;
				case BOOTS -> Items.DIAMOND_BOOTS;
			});
			case OBSIDIAN -> new ItemStack(switch (slot) {
				case HELMET -> Items.NETHERITE_HELMET;
				case CHESTPLATE -> Items.NETHERITE_CHESTPLATE;
				case LEGGINGS -> Items.NETHERITE_LEGGINGS;
				case BOOTS -> Items.NETHERITE_BOOTS;
			});
		};
	}

	public static ItemStack weaponItem(String id) {
		try { return weaponItem(Loadout.Weapon.valueOf(id)); }
		catch (IllegalArgumentException ignored) { return IRON_SWORD; }
	}

	public static ItemStack weaponItem(Loadout.Weapon weapon) {
		var spec = dev.flake.shared.SliceRegistry.weapon(weapon);
		return spec != null ? spec.createStack() : IRON_SWORD;
	}

	public static ItemStack accessoryItem(Loadout.Accessory accessory) {
		if (accessory == null) return ItemStack.EMPTY;
		return switch (accessory) {
			case VITALITY -> new ItemStack(Items.GLISTERING_MELON_SLICE);
			case MOBILITY -> new ItemStack(Items.FEATHER);
			case REGENERATION -> new ItemStack(Items.GHAST_TEAR);
			case WIND_CHARGE -> new ItemStack(Items.WIND_CHARGE);
		};
	}

	public static ItemStack passiveItem(Loadout.PassiveItem passive) {
		if (passive == null) return ItemStack.EMPTY;
		var spec = dev.flake.shared.SliceRegistry.passive(passive);
		return spec != null ? spec.createIcon() : ItemStack.EMPTY;
	}

	public static ItemStack utilityItem(Loadout.UtilityItem utility) {
		if (utility == null) return ItemStack.EMPTY;
		return activeItem(Loadout.ActiveItem.valueOf(utility.name()));
	}

	public static ItemStack activeItem(Loadout.ActiveItem item) {
		if (item == null || item == Loadout.ActiveItem.NONE) return ItemStack.EMPTY;
		var spec = dev.flake.shared.SliceRegistry.active(item);
		if (spec == null) return ItemStack.EMPTY;
		var stack = spec.createStack();
		if (stack.getCount() > 1) stack.setCount(1);
		return stack;
	}

	public static void scaledItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float scale) {
		if (stack == null || stack.isEmpty()) return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.fakeItem(stack, 0, 0);
		g.pose().popMatrix();
	}

	/** Draws schematic pixel-art silhouettes matching the build editor sketch when a slot is empty. */
	public static void slotSilhouette(GuiGraphicsExtractor g, int cx, int cy, String kind, int color) {
		switch (kind) {
			case "HELMET" -> {
				g.fill(cx - 7, cy - 6, cx + 7, cy - 4, color);
				g.fill(cx - 8, cy - 4, cx - 5, cy + 5, color);
				g.fill(cx + 5, cy - 4, cx + 8, cy + 5, color);
				g.fill(cx - 5, cy - 4, cx + 5, cy, color);
				g.fill(cx - 2, cy, cx + 2, cy + 3, color);
			}
			case "CHESTPLATE" -> {
				g.fill(cx - 9, cy - 7, cx - 4, cy - 3, color);
				g.fill(cx + 4, cy - 7, cx + 9, cy - 3, color);
				g.fill(cx - 6, cy - 5, cx + 6, cy + 7, color);
				g.fill(cx - 3, cy - 7, cx + 3, cy - 4, UiTheme.CARD);
			}
			case "LEGGINGS" -> {
				g.fill(cx - 6, cy - 7, cx + 6, cy - 3, color);
				g.fill(cx - 6, cy - 3, cx - 2, cy + 7, color);
				g.fill(cx + 2, cy - 3, cx + 6, cy + 7, color);
			}
			case "BOOTS" -> {
				g.fill(cx - 7, cy - 5, cx - 3, cy + 3, color);
				g.fill(cx - 9, cy + 3, cx - 2, cy + 6, color);
				g.fill(cx + 3, cy - 5, cx + 7, cy + 3, color);
				g.fill(cx + 2, cy + 3, cx + 9, cy + 6, color);
			}
			case "ACCESSORY" -> {
				g.fill(cx - 6, cy - 6, cx + 6, cy + 1, color);
				g.fill(cx - 4, cy + 1, cx + 4, cy + 4, color);
				g.fill(cx - 2, cy + 4, cx + 2, cy + 6, color);
				g.fill(cx - 4, cy - 4, cx + 4, cy, UiTheme.CARD);
			}
			case "PASSIVE" -> {
				g.outline(cx - 6, cy - 6, 12, 12, color);
				g.fill(cx - 2, cy - 2, cx + 2, cy + 2, color);
			}
			case "WEAPON" -> {
				for (int i = -5; i <= 4; i++) {
					g.fill(cx + i, cy - i - 1, cx + i + 2, cy - i + 1, color);
				}
				g.fill(cx - 6, cy + 2, cx - 1, cy + 4, color);
				g.fill(cx - 4, cy, cx - 2, cy + 6, color);
			}
			case "UTILITY" -> {
				g.fill(cx - 2, cy - 7, cx + 2, cy - 3, color);
				g.fill(cx - 5, cy - 3, cx + 5, cy + 5, color);
				g.fill(cx - 3, cy + 5, cx + 3, cy + 7, color);
				g.fill(cx - 3, cy - 1, cx + 3, cy + 4, UiTheme.CARD);
			}
		}
	}

	/** 22x22 framed skill icon with live animations for charging, ready and class identity. */
	public static void skillBox(GuiGraphicsExtractor g, int x, int y, String classId, int accent, boolean charging, float progress, float timeSec) {
		g.fill(x, y, x + 22, y + 22, UiTheme.CARD_ALT);
		g.outline(x, y, 22, 22, charging ? accent : UiTheme.LINE);
		int cx = x + 11, cy = y + 11;

		switch (classId) {
			case "CONCUSSED" -> {
				int r = charging ? Math.max(3, Math.round(7f - 4f * progress)) : 6;
				ring(g, cx, cy, r, accent);
				if (!charging && progress >= 0.99f) {
					int pulseR = 4 + (int) ((timeSec * 3f) % 4f);
					ring(g, cx, cy, pulseR, UiTheme.withAlpha(accent, 0.35f));
				}
				g.fill(cx - 1, cy - r - 2, cx + 1, cy - r, accent);
				g.fill(cx - 1, cy + r, cx + 1, cy + r + 2, accent);
				g.fill(cx - r - 2, cy - 1, cx - r, cy + 1, accent);
				g.fill(cx + r, cy - 1, cx + r + 2, cy + 1, accent);
				g.fill(cx - 2, cy - 2, cx + 2, cy + 2, UiTheme.TEXT);
			}
			case "CONCUSSED_QUAKE" -> drawElectricQuakeGlyph(g, x, y, accent, charging, progress, timeSec);
			case "BASTION" -> {
				g.fill(cx - 5, cy - 6, cx + 5, cy + 2, accent);
				g.fill(cx - 4, cy + 2, cx + 4, cy + 4, accent);
				g.fill(cx - 2, cy + 4, cx + 2, cy + 6, accent);
				g.fill(cx - 3, cy - 4, cx + 3, cy + 2, UiTheme.CARD_ALT);
				g.fill(cx - 1, cy - 4, cx + 1, cy + 3, UiTheme.TEXT);
				g.fill(cx - 3, cy - 1, cx + 3, cy + 1, UiTheme.TEXT);
			}
			case "VAMPIRE" -> {
				g.fill(cx - 5, cy - 5, cx - 2, cy + 1, accent);
				g.fill(cx - 4, cy + 1, cx - 3, cy + 5, UiTheme.TEXT);
				g.fill(cx + 2, cy - 5, cx + 5, cy + 1, accent);
				g.fill(cx + 3, cy + 1, cx + 4, cy + 5, UiTheme.TEXT);
				g.fill(cx - 1, cy - 2, cx + 1, cy + 3, UiTheme.RED);
			}
			case "DEMOLITIONIST" -> {
				g.fill(cx - 4, cy - 3, cx + 4, cy + 5, accent);
				g.fill(cx - 5, cy - 2, cx + 5, cy + 4, accent);
				g.fill(cx - 2, cy - 5, cx + 2, cy - 3, UiTheme.MUTED);
				int spark = ((int) (timeSec * 10f) % 2 == 0) ? UiTheme.AMBER : UiTheme.TEXT;
				g.fill(cx + 1, cy - 7, cx + 4, cy - 5, spark);
				g.fill(cx - 2, cy - 1, cx, cy + 1, UiTheme.TEXT);
			}
			default -> g.fill(cx - 3, cy - 3, cx + 3, cy + 3, accent);
		}
	}

	/** Compact 10x10 class emblem for HUD cards, lobby and Tab overlay. */
	public static void classEmblem(GuiGraphicsExtractor g, int x, int y, String classId, int color) {
		switch (classId) {
			case "CONCUSSED" -> {
				g.outline(x + 1, y + 1, 8, 8, color);
				g.fill(x + 4, y + 4, x + 6, y + 6, UiTheme.TEXT);
			}
			case "BASTION" -> {
				g.fill(x + 1, y + 1, x + 9, y + 6, color);
				g.fill(x + 2, y + 6, x + 8, y + 8, color);
				g.fill(x + 4, y + 8, x + 6, y + 9, color);
				g.fill(x + 4, y + 2, x + 6, y + 7, UiTheme.TEXT);
			}
			case "VAMPIRE" -> {
				g.fill(x + 1, y + 1, x + 4, y + 5, color);
				g.fill(x + 2, y + 5, x + 3, y + 9, UiTheme.TEXT);
				g.fill(x + 6, y + 1, x + 9, y + 5, color);
				g.fill(x + 7, y + 5, x + 8, y + 9, UiTheme.TEXT);
			}
			case "DEMOLITIONIST" -> {
				g.fill(x + 2, y + 3, x + 8, y + 9, color);
				g.fill(x + 4, y + 1, x + 6, y + 3, UiTheme.MUTED);
				g.fill(x + 6, y, x + 8, y + 2, UiTheme.AMBER);
			}
			default -> g.fill(x + 2, y + 2, x + 8, y + 8, color);
		}
	}

	/** 7x7 pixel heart icon. */
	public static void heart(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x + 1, y, x + 3, y + 1, color);
		g.fill(x + 4, y, x + 6, y + 1, color);
		g.fill(x, y + 1, x + 7, y + 4, color);
		g.fill(x + 1, y + 4, x + 6, y + 5, color);
		g.fill(x + 2, y + 5, x + 5, y + 6, color);
		g.fill(x + 3, y + 6, x + 4, y + 7, color);
	}

	/** 7x7 animated pixel clock icon. */
	public static void clock(GuiGraphicsExtractor g, int x, int y, int color, float timeSec) {
		g.outline(x, y, 7, 7, color);
		g.fill(x + 3, y + 1, x + 4, y + 4, UiTheme.TEXT);
		int phase = ((int) (timeSec * 2f)) & 3;
		switch (phase) {
			case 0 -> g.fill(x + 4, y + 3, x + 6, y + 4, color);
			case 1 -> g.fill(x + 3, y + 4, x + 4, y + 6, color);
			case 2 -> g.fill(x + 1, y + 3, x + 3, y + 4, color);
			default -> g.fill(x + 3, y + 1, x + 4, y + 3, color);
		}
	}

	private static void drawElectricQuakeGlyph(GuiGraphicsExtractor g, int x, int y, int accent, boolean active, float progress, float timeSec) {
		int cobalt = 0xff2979ff;
		int cyan = 0xff64f5ff;
		int core = UiTheme.TEXT;
		int mainColor = active || progress >= 0.99f ? cyan : accent;

		g.fill(x + 2, y + 15, x + 20, y + 20, UiTheme.withAlpha(cobalt, active ? 0.38f : 0.18f));

		g.fill(x + 2, y + 17, x + 8, y + 18, cobalt);
		g.fill(x + 14, y + 17, x + 20, y + 18, cobalt);
		g.fill(x + 4, y + 18, x + 18, y + 19, mainColor);
		g.fill(x + 7, y + 16, x + 15, y + 18, cyan);
		g.fill(x + 9, y + 17, x + 13, y + 19, core);

		g.fill(x + 12, y + 3, x + 15, y + 5, mainColor);
		g.fill(x + 10, y + 5, x + 13, y + 8, mainColor);
		g.fill(x + 8, y + 8, x + 14, y + 10, cyan);
		g.fill(x + 11, y + 10, x + 14, y + 13, mainColor);
		g.fill(x + 9, y + 13, x + 12, y + 17, core);

		g.fill(x + 13, y + 4, x + 14, y + 6, core);
		g.fill(x + 11, y + 6, x + 12, y + 9, core);
		g.fill(x + 10, y + 9, x + 13, y + 10, core);
		g.fill(x + 11, y + 10, x + 12, y + 14, core);

		int sparkPhase = ((int) (timeSec * (active ? 14f : 6f))) % 3;
		if (sparkPhase == 0 || active) {
			g.fill(x + 4, y + 13, x + 6, y + 15, cyan);
			g.fill(x + 3, y + 15, x + 5, y + 16, core);
		}
		if (sparkPhase == 1 || active) {
			g.fill(x + 16, y + 12, x + 18, y + 14, cyan);
			g.fill(x + 17, y + 14, x + 19, y + 16, core);
		}
		if (sparkPhase == 2 || active) {
			g.fill(x + 5, y + 9, x + 7, y + 11, cobalt);
			g.fill(x + 15, y + 7, x + 17, y + 9, cyan);
		}
	}

	private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		g.fill(cx - r + 1, cy - r, cx + r - 1, cy - r + 1, color);
		g.fill(cx - r + 1, cy + r - 1, cx + r - 1, cy + r, color);
		g.fill(cx - r, cy - r + 1, cx - r + 1, cy + r - 1, color);
		g.fill(cx + r - 1, cy - r + 1, cx + r, cy + r - 1, color);
	}
}
