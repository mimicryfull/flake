package dev.flake.client.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.flake.build.Loadout;
import dev.flake.client.ui.UiIcons;
import dev.flake.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Detached mannequin: never inserted into the level and never touches the live inventory. */
final class BuildPreview extends AbstractWidget {
	private final Minecraft client = Minecraft.getInstance();
	private RemotePlayer mannequin;
	private Loadout shown;
	private float yaw = 155f;
	private final Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateX(0.08f);
	private final Quaternionf camera = new Quaternionf().rotateX(0.08f);
	private final Vector3f translation = new Vector3f(0f, 0.95f, 0f);
	private final long opened = System.nanoTime();

	BuildPreview(int x, int y, int w, int h) {
		super(x, y, w, h, Component.literal(dev.flake.shared.FlakeI18n.tr("flake.preview.narrator")));
	}

	void show(Loadout build) {
		if (client.player == null || client.level == null) return;
		if (mannequin == null || mannequin.level() != client.level) {
			mannequin = new RemotePlayer(client.level, client.player.getGameProfile()) {
				@Override public PlayerSkin getSkin() { return client.player == null ? super.getSkin() : client.player.getSkin(); }
			};
			// 26.3 assigns IDs on world insertion; this entity is deliberately never inserted.
			mannequin.setId(-2_000_000);
			shown = null;
		}
		if (build.equals(shown)) return;
		mannequin.setItemSlot(EquipmentSlot.HEAD, UiIcons.armorPieceItem(Loadout.ArmorSlot.HELMET, build.helmet()).copy());
		mannequin.setItemSlot(EquipmentSlot.CHEST, UiIcons.armorPieceItem(Loadout.ArmorSlot.CHESTPLATE, build.chestplate()).copy());
		mannequin.setItemSlot(EquipmentSlot.LEGS, UiIcons.armorPieceItem(Loadout.ArmorSlot.LEGGINGS, build.leggings()).copy());
		mannequin.setItemSlot(EquipmentSlot.FEET, UiIcons.armorPieceItem(Loadout.ArmorSlot.BOOTS, build.boots()).copy());
		mannequin.setItemSlot(EquipmentSlot.MAINHAND, UiIcons.weaponItem(build.weapon()).copy());
		mannequin.setItemSlot(EquipmentSlot.OFFHAND,
				build.activeItem1() == Loadout.ActiveItem.SHIELD || build.activeItem2() == Loadout.ActiveItem.SHIELD ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
		shown = build;
	}

	@Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		if (mannequin == null) {
			UiTheme.centeredText(g, dev.flake.shared.FlakeI18n.tr("flake.preview.enter_world"), getX() + width / 2, getY() + height / 2, width, UiTheme.MUTED);
			return;
		}
		float seconds = (System.nanoTime() - opened) / 1_000_000_000f;
		mannequin.tickCount = (int) (seconds * 20f);
		var state = client.getEntityRenderDispatcher().extractEntity(mannequin, 1f);
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = yaw + (float) Math.sin(seconds * 0.7f) * 1.3f;
			living.yRot = 0f; living.xRot = -2f; living.walkAnimationSpeed = 0f;
			living.scale = 1f; living.isInvisibleToPlayer = false;
		}
		if (state instanceof AvatarRenderState avatar) {
			avatar.showHat = avatar.showJacket = avatar.showLeftSleeve = avatar.showRightSleeve = true;
			avatar.showLeftPants = avatar.showRightPants = true; avatar.showCape = false;
		}
		int scale = Math.max(16, Math.min((int) (height / 2.05f), (int) (width / 1.45f)));
		g.entity(state, scale, translation, rotation, camera, getX(), getY(), getRight(), getBottom());
		if (isFocused()) g.outline(getX(), getY(), width, height, UiTheme.CYAN);
	}
	@Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { yaw -= (float) dx * 1.1f; }
	@Override public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_LEFT || event.key() == InputConstants.KEY_RIGHT) {
			yaw += event.key() == InputConstants.KEY_LEFT ? -15f : 15f; return true;
		}
		return super.keyPressed(event);
	}
	@Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
	void release() { mannequin = null; shown = null; }
}
