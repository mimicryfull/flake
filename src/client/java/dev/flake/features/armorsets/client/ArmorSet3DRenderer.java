package dev.flake.features.armorsets.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.flake.Flake;
import dev.flake.features.armorsets.BarrelArmor;
import dev.flake.features.armorsets.CustomArmorSet;
import dev.flake.features.armorsets.HydraArmor;
import dev.flake.features.armorsets.PhoenixArmor;
import dev.flake.features.armorsets.WizardArmor;
import dev.flake.features.loadout.Loadout.ArmorSlot;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Renders true 3D custom armor geometry (Blockbench Barrel Knight models, sculpted 64x64
 * Phoenix/Hydra armor with crests & horns, and pointed Battle-Mage Wizard armor) directly
 * bound to HumanoidModel bones so all player animations & Loadout Editor mannequins stay synced.
 */
public final class ArmorSet3DRenderer {
	private static final Identifier TEX_PHOENIX_1 = tex("phoenix_layer_1.png");
	private static final Identifier TEX_PHOENIX_2 = tex("phoenix_layer_2.png");
	private static final Identifier TEX_HYDRA_1 = tex("hydra_layer_1.png");
	private static final Identifier TEX_HYDRA_2 = tex("hydra_layer_2.png");
	private static final Identifier TEX_BARREL = tex("barrel.png");
	private static final Identifier TEX_BUCKET = tex("wooden_bucket.png");
	private static final Identifier TEX_WIZARD_HAT = tex("wizard_hat.png");
	private static final Identifier TEX_WIZARD_ROBE = tex("wizard_robe.png");

	// Barrel Knight meshes (exact Blockbench models + oak/iron sleeves, greaves, boots)
	private static final List<ArmorMesh3D.Quad> BARREL_BUCKET = BarrelMeshData.buildBucket();
	private static final List<ArmorMesh3D.Quad> BARREL_TORSO = BarrelMeshData.buildBarrelTorso();
	private static final List<ArmorMesh3D.Quad> BARREL_ARM_R = buildBarrelArm(true);
	private static final List<ArmorMesh3D.Quad> BARREL_ARM_L = buildBarrelArm(false);
	private static final List<ArmorMesh3D.Quad> BARREL_BELT = buildBarrelBelt();
	private static final List<ArmorMesh3D.Quad> BARREL_LEG = buildBarrelLeg();
	private static final List<ArmorMesh3D.Quad> BARREL_BOOT = buildBarrelBoot();

	// Phoenix & Hydra shared 64x64 armor plates + unique crowns/horns/plumes
	private static final List<ArmorMesh3D.Quad> PLATE_HELM_BASE = buildPlateHelmBase();
	private static final List<ArmorMesh3D.Quad> PHOENIX_PLUME = buildPhoenixPlume();
	private static final List<ArmorMesh3D.Quad> HYDRA_HORNS = buildHydraVisorAndHorns();
	private static final List<ArmorMesh3D.Quad> PLATE_CUIRASS = buildPlateCuirass();
	private static final List<ArmorMesh3D.Quad> PLATE_ARM_R = buildPlateArm(true);
	private static final List<ArmorMesh3D.Quad> PLATE_ARM_L = buildPlateArm(false);
	private static final List<ArmorMesh3D.Quad> PLATE_BELT = buildPlateBelt();
	private static final List<ArmorMesh3D.Quad> PLATE_THIGH_R = buildPlateThigh(false);
	private static final List<ArmorMesh3D.Quad> PLATE_THIGH_L = buildPlateThigh(true);
	private static final List<ArmorMesh3D.Quad> PLATE_BOOT = buildPlateBoot();

	// Wizard pointed hat + battle-mage mantle/robe
	private static final List<ArmorMesh3D.Quad> WIZARD_HAT = buildWizardHat();
	private static final List<ArmorMesh3D.Quad> WIZARD_ROBE_BODY = buildWizardBody();
	private static final List<ArmorMesh3D.Quad> WIZARD_SLEEVE_R = buildWizardSleeve(true);
	private static final List<ArmorMesh3D.Quad> WIZARD_SLEEVE_L = buildWizardSleeve(false);
	private static final List<ArmorMesh3D.Quad> WIZARD_SKIRT = buildWizardSkirt();
	private static final List<ArmorMesh3D.Quad> WIZARD_BOOT = buildWizardBoot();

	private ArmorSet3DRenderer() {}

	private static Identifier tex(String file) {
		return Identifier.fromNamespaceAndPath(Flake.MOD_ID, "textures/entity/armorsets/" + file);
	}

	/**
	 * Attempts to render a custom 3D armor piece. Returns true if the item belongs to one
	 * of the four custom sets (so vanilla flat HumanoidArmorLayer rendering should be cancelled).
	 */
	public static boolean tryRender(
			PoseStack poseStack, SubmitNodeCollector collector,
			HumanoidModel<?> parentModel, ItemStack stack, EquipmentSlot slot, int packedLight) {
		if (stack.isEmpty() || parentModel == null) return false;
		CustomArmorSet set = identifySet(stack, slot);
		if (set == null) return false;

		if (set == BarrelArmor.SET) {
			renderBarrel(poseStack, collector, parentModel, slot, packedLight);
		} else if (set == PhoenixArmor.SET) {
			renderPlateSet(poseStack, collector, parentModel, slot, packedLight, TEX_PHOENIX_1, TEX_PHOENIX_2, true);
		} else if (set == HydraArmor.SET) {
			renderPlateSet(poseStack, collector, parentModel, slot, packedLight, TEX_HYDRA_1, TEX_HYDRA_2, false);
		} else if (set == WizardArmor.SET) {
			renderWizard(poseStack, collector, parentModel, slot, packedLight);
		}
		return true;
	}

	private static CustomArmorSet identifySet(ItemStack stack, EquipmentSlot slot) {
		ArmorSlot armorSlot = switch (slot) {
			case HEAD -> ArmorSlot.HELMET;
			case CHEST -> ArmorSlot.CHESTPLATE;
			case LEGS -> ArmorSlot.LEGGINGS;
			case FEET -> ArmorSlot.BOOTS;
			default -> null;
		};
		if (armorSlot == null) return null;
		if (stack.is(PhoenixArmor.SET.itemFor(armorSlot))) return PhoenixArmor.SET;
		if (stack.is(BarrelArmor.SET.itemFor(armorSlot))) return BarrelArmor.SET;
		if (stack.is(WizardArmor.SET.itemFor(armorSlot))) return WizardArmor.SET;
		if (stack.is(HydraArmor.SET.itemFor(armorSlot))) return HydraArmor.SET;
		return null;
	}

	private static void submitOnBone(PoseStack poseStack, SubmitNodeCollector collector, ModelPart bone,
			Identifier texture, int packedLight, List<ArmorMesh3D.Quad> quads) {
		poseStack.pushPose();
		bone.translateAndRotate(poseStack);
		ArmorMesh3D.submit(poseStack, collector, texture, packedLight, quads);
		poseStack.popPose();
	}

	private static void renderBarrel(PoseStack ps, SubmitNodeCollector col, HumanoidModel<?> m, EquipmentSlot slot, int light) {
		switch (slot) {
			case HEAD -> submitOnBone(ps, col, m.head, TEX_BUCKET, light, BARREL_BUCKET);
			case CHEST -> {
				submitOnBone(ps, col, m.body, TEX_BARREL, light, BARREL_TORSO);
				submitOnBone(ps, col, m.rightArm, TEX_BARREL, light, BARREL_ARM_R);
				submitOnBone(ps, col, m.leftArm, TEX_BARREL, light, BARREL_ARM_L);
			}
			case LEGS -> {
				submitOnBone(ps, col, m.body, TEX_BARREL, light, BARREL_BELT);
				submitOnBone(ps, col, m.rightLeg, TEX_BARREL, light, BARREL_LEG);
				submitOnBone(ps, col, m.leftLeg, TEX_BARREL, light, BARREL_LEG);
			}
			case FEET -> {
				submitOnBone(ps, col, m.rightLeg, TEX_BARREL, light, BARREL_BOOT);
				submitOnBone(ps, col, m.leftLeg, TEX_BARREL, light, BARREL_BOOT);
			}
			default -> {}
		}
	}

	private static void renderPlateSet(PoseStack ps, SubmitNodeCollector col, HumanoidModel<?> m, EquipmentSlot slot, int light,
			Identifier layer1, Identifier layer2, boolean phoenix) {
		switch (slot) {
			case HEAD -> {
				submitOnBone(ps, col, m.head, layer1, light, PLATE_HELM_BASE);
				submitOnBone(ps, col, m.head, layer2, light, phoenix ? PHOENIX_PLUME : HYDRA_HORNS);
			}
			case CHEST -> {
				submitOnBone(ps, col, m.body, layer1, light, PLATE_CUIRASS);
				submitOnBone(ps, col, m.rightArm, layer1, light, PLATE_ARM_R);
				submitOnBone(ps, col, m.leftArm, layer1, light, PLATE_ARM_L);
			}
			case LEGS -> {
				submitOnBone(ps, col, m.body, layer2, light, PLATE_BELT);
				submitOnBone(ps, col, m.rightLeg, layer2, light, PLATE_THIGH_R);
				submitOnBone(ps, col, m.leftLeg, layer2, light, PLATE_THIGH_L);
			}
			case FEET -> {
				submitOnBone(ps, col, m.rightLeg, layer1, light, PLATE_BOOT);
				submitOnBone(ps, col, m.leftLeg, layer1, light, PLATE_BOOT);
			}
			default -> {}
		}
	}

	private static void renderWizard(PoseStack ps, SubmitNodeCollector col, HumanoidModel<?> m, EquipmentSlot slot, int light) {
		switch (slot) {
			case HEAD -> submitOnBone(ps, col, m.head, TEX_WIZARD_HAT, light, WIZARD_HAT);
			case CHEST -> {
				submitOnBone(ps, col, m.body, TEX_WIZARD_ROBE, light, WIZARD_ROBE_BODY);
				submitOnBone(ps, col, m.rightArm, TEX_WIZARD_ROBE, light, WIZARD_SLEEVE_R);
				submitOnBone(ps, col, m.leftArm, TEX_WIZARD_ROBE, light, WIZARD_SLEEVE_L);
			}
			case LEGS -> {
				submitOnBone(ps, col, m.rightLeg, TEX_WIZARD_ROBE, light, WIZARD_SKIRT);
				submitOnBone(ps, col, m.leftLeg, TEX_WIZARD_ROBE, light, WIZARD_SKIRT);
			}
			case FEET -> {
				submitOnBone(ps, col, m.rightLeg, TEX_WIZARD_HAT, light, WIZARD_BOOT);
				submitOnBone(ps, col, m.leftLeg, TEX_WIZARD_HAT, light, WIZARD_BOOT);
			}
			default -> {}
		}
	}

	// ---- Phoenix & Hydra 64x64 3D plates ----

	private static List<ArmorMesh3D.Quad> buildPlateHelmBase() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		// Base helmet CubeUV [0..32, 0..16]
		b.cubeUv(0, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.55f, false);
		// Sculpted brow visor [32..48, 8..16]
		float[] brow = {33, 9, 47, 15};
		b.faceUvRot(-4.7f, -8.3f, -5.0f, 9.4f, 2.2f, 2.5f, 0f, 0f, 0f, 0f, -7f, -4f,
				brow, null, brow, brow, new float[]{40, 0, 44, 4}, brow);
		// Crown ridge ornament [40..44, 0..4]
		float[] gem = {40, 0, 44, 4};
		b.faceUvRot(-1.1f, -10.0f, -4.2f, 2.2f, 2.0f, 7.6f, 0f, 0f, 0f, 0f, -9f, 0f,
				gem, gem, gem, gem, gem, gem);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPhoenixPlume() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		// Swept flame wing-fins on helmet sides + back crest from phoenix_layer_2 [40..54, 42..51]
		float[] plume = {40, 43, 54, 50};
		b.faceUvRot(-5.3f, -10.8f, -1.5f, 1.0f, 5.2f, 7.5f, 18f, -14f, -10f, -4.8f, -8f, 1f,
				plume, plume, plume, plume, plume, plume);
		b.faceUvRot(4.3f, -10.8f, -1.5f, 1.0f, 5.2f, 7.5f, 18f, 14f, 10f, 4.8f, -8f, 1f,
				plume, plume, plume, plume, plume, plume);
		b.faceUvRot(-0.7f, -11.8f, -1.5f, 1.4f, 4.0f, 7.2f, 20f, 0f, 0f, 0f, -9f, 1f,
				plume, plume, plume, plume, plume, plume);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildHydraVisorAndHorns() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		// Sculpted adamantite helm visor CubeUV [32..64, 0..14]
		b.cubeUv(32, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.95f, false);
		// Twin curved crimson Hydra horns angled out & up on both temples
		float[] horn = {40, 2, 48, 7};
		b.faceUvRot(-6.2f, -10.0f, -3.2f, 2.0f, 4.2f, 2.0f, -14f, 0f, -24f, -4.8f, -6.5f, -2f,
				horn, horn, horn, horn, horn, horn);
		b.faceUvRot(-6.9f, -12.6f, -1.8f, 1.4f, 3.4f, 1.6f, 24f, 0f, -12f, -5.8f, -9.8f, -1.5f,
				horn, horn, horn, horn, horn, horn);
		b.faceUvRot(4.2f, -10.0f, -3.2f, 2.0f, 4.2f, 2.0f, -14f, 0f, 24f, 4.8f, -6.5f, -2f,
				horn, horn, horn, horn, horn, horn);
		b.faceUvRot(5.5f, -12.6f, -1.8f, 1.4f, 3.4f, 1.6f, 24f, 0f, 12f, 5.8f, -9.8f, -1.5f,
				horn, horn, horn, horn, horn, horn);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPlateCuirass() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		// Main 3D cuirass around torso using exact layer_1 [0..23, 16..29]
		float[] top = {7, 16, 17, 22};
		b.faceUvRot(-4.65f, 0.1f, -2.65f, 9.3f, 11.2f, 5.3f, 0f, 0f, 0f, 0f, 6f, 0f,
				new float[]{6, 23, 17, 29}, new float[]{6, 23, 17, 29},
				new float[]{0, 23, 7, 29}, new float[]{16, 23, 23, 29}, top, top);
		// Layered sternum breastplate relief
		b.faceUvRot(-3.2f, 0.9f, -3.3f, 6.4f, 5.8f, 0.9f, 0f, 0f, 0f, 0f, 3f, -2.8f,
				new float[]{7, 23, 16, 29}, null,
				new float[]{1, 23, 6, 29}, new float[]{17, 23, 22, 29},
				new float[]{8, 17, 15, 21}, new float[]{8, 27, 15, 29});
		// Gorget collar ring
		b.faceUvRot(-3.8f, -0.7f, -2.5f, 7.6f, 1.4f, 5.0f, 0f, 0f, 0f, 0f, 0f, 0f,
				new float[]{7, 17, 17, 21}, new float[]{7, 17, 17, 21},
				new float[]{1, 24, 6, 28}, new float[]{17, 24, 22, 28}, top, top);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPlateArm(boolean rightArm) {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float ax = rightArm ? -3.0f : -1.0f;
		// Layered 3D shoulder pauldron mapped to exact [28..52, 25..35]
		float pTilt = rightArm ? -8f : 8f;
		float px = rightArm ? -3.7f : -1.5f;
		b.faceUvRot(px, -2.8f, -2.6f, 5.2f, 5.4f, 5.2f, 0f, 0f, pTilt, px + 2.6f, 0f, 0f,
				new float[]{32, 29, 40, 35}, new float[]{44, 29, 52, 35},
				new float[]{28, 29, 32, 35}, new float[]{40, 29, 44, 35},
				new float[]{32, 25, 40, 29}, new float[]{32, 25, 40, 29});
		// Secondary upper pauldron ridge for layered silhouette
		float rx2 = rightArm ? -4.1f : -0.6f;
		b.faceUvRot(rx2, -3.3f, -2.3f, 4.7f, 2.0f, 4.6f, 0f, 0f, rightArm ? -16f : 16f, rx2 + 2.3f, -2.2f, 0f,
				new float[]{33, 29, 39, 32}, new float[]{45, 29, 51, 32},
				new float[]{28, 29, 32, 32}, new float[]{40, 29, 44, 32},
				new float[]{32, 25, 40, 29}, new float[]{32, 25, 40, 29});
		// Forearm gauntlet CubeUV [19..35, 41..49]
		b.cubeUv(19, 41, ax, 5.2f, -2.0f, 4f, 4f, 4f, 0.45f, !rightArm);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPlateBelt() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		// Waist belt [0..24, 34..41]
		b.faceUvRot(-4.4f, 8.8f, -2.4f, 8.8f, 3.6f, 4.8f, 0f, 0f, 0f, 0f, 10f, 0f,
				new float[]{6, 34, 16, 40}, new float[]{18, 34, 24, 40},
				new float[]{0, 34, 6, 40}, new float[]{16, 34, 22, 40},
				new float[]{6, 34, 16, 36}, new float[]{6, 38, 16, 40});
		// 3D front crotch/fauld buckle crest CubeUV (u=0, v=13, W=2, H=8, D=4)
		b.faceUvRot(-1.4f, 8.2f, -3.1f, 2.8f, 4.8f, 1.3f, 0f, 0f, 0f, 0f, 10f, -2.5f,
				new float[]{4, 17, 6, 25}, new float[]{4, 17, 6, 25},
				new float[]{0, 17, 4, 25}, new float[]{6, 17, 10, 25},
				new float[]{4, 13, 6, 17}, new float[]{4, 13, 6, 17});
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPlateThigh(boolean mirror) {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		// Thigh cuisses CubeUV [24..40, 29..43]
		b.cubeUv(24, 29, -2f, -0.2f, -2f, 4f, 9.2f, 4f, 0.52f, mirror);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPlateBoot() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		// Greave ankle cuff [8..18, 43..53]
		float[] panel = {9, 45, 17, 52};
		b.faceUvRot(-2.45f, 7.1f, -2.45f, 4.9f, 5.1f, 4.9f, 0f, 0f, 0f, 0f, 9f, 0f,
				panel, panel, panel, panel, new float[]{10, 46, 16, 49}, new float[]{9, 55, 17, 62});
		// Armored projecting sabaton toe cap
		float[] toe = {9, 57, 17, 62};
		b.faceUvRot(-2.35f, 9.8f, -3.45f, 4.7f, 2.4f, 1.5f, 0f, 0f, 0f, 0f, 11f, -2.5f,
				toe, toe, new float[]{19, 50, 26, 56}, new float[]{19, 50, 26, 56},
				new float[]{19, 54, 26, 57}, toe);
		return b.build();
	}

	// ---- Barrel Knight auxiliary pieces (arm bracers, greaves, oak boots) ----

	private static List<ArmorMesh3D.Quad> buildBarrelArm(boolean rightArm) {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float x = rightArm ? -3.4f : -1.4f;
		float[] plank = {1, 2, 12, 18};
		float[] hoop = {39, 32, 49, 44};
		// Iron-banded oak wrist bracer
		b.faceUvRot(x, 5.0f, -2.4f, 4.8f, 4.4f, 4.8f, 0f, 0f, 0f, 0f, 7f, 0f,
				plank, plank, hoop, hoop, hoop, hoop);
		// Small shoulder iron cap
		b.faceUvRot(x, -2.3f, -2.4f, 4.8f, 2.2f, 4.8f, 0f, 0f, rightArm ? -8f : 8f, x + 2.4f, -1f, 0f,
				hoop, hoop, hoop, hoop, plank, hoop);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildBarrelBelt() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] hoop = {39, 32, 49, 44};
		float[] leather = {13, 37, 20, 44};
		b.faceUvRot(-4.4f, 9.2f, -2.4f, 8.8f, 3.0f, 4.8f, 0f, 0f, 0f, 0f, 10f, 0f,
				leather, leather, hoop, hoop, leather, hoop);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildBarrelLeg() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] plank = {1, 2, 12, 18};
		float[] leather = {13, 37, 20, 44};
		b.faceUvRot(-2.45f, 0.2f, -2.45f, 4.9f, 7.6f, 4.9f, 0f, 0f, 0f, 0f, 4f, 0f,
				plank, plank, leather, leather, plank, leather);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildBarrelBoot() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] iron = {39, 32, 49, 44};
		float[] wood = {2, 20, 12, 30};
		b.faceUvRot(-2.5f, 7.4f, -2.5f, 5.0f, 4.8f, 5.0f, 0f, 0f, 0f, 0f, 9f, 0f,
				iron, iron, wood, wood, iron, iron);
		b.faceUvRot(-2.4f, 9.8f, -3.4f, 4.8f, 2.4f, 1.4f, 0f, 0f, 0f, 0f, 11f, -2.5f,
				iron, iron, iron, iron, iron, iron);
		return b.build();
	}

	// ---- Wizard Battle-Mage 3D Hat & Robe ----

	private static List<ArmorMesh3D.Quad> buildWizardHat() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] brimTop = {13, 1, 25, 13};
		float[] brimBot = {25, 1, 37, 13};
		float[] brimEdge = {13, 1, 25, 4};
		// Wide angled hat brim
		b.faceUvRot(-7.4f, -6.6f, -7.4f, 14.8f, 1.2f, 14.8f, -4f, 0f, 0f, 0f, -6f, 0f,
				brimEdge, brimEdge, brimEdge, brimEdge, brimTop, brimBot);
		// Lower crown tier
		float[] crown1 = {12, 16, 24, 24};
		b.faceUvRot(-4.3f, -10.4f, -4.3f, 8.6f, 4.0f, 8.6f, -4f, 0f, 0f, 0f, -6f, 0f,
				crown1, crown1, crown1, crown1, brimTop, brimBot);
		// Gold wizard buckle on hat band
		float[] buckle = {41, 25, 50, 31};
		b.faceUvRot(-1.6f, -8.3f, -4.9f, 3.2f, 1.8f, 0.9f, -4f, 0f, 0f, 0f, -6f, 0f,
				buckle, buckle, buckle, buckle, buckle, buckle);
		// Upper tilted cone spire
		float[] crown2 = {24, 17, 36, 28};
		b.faceUvRot(-3.0f, -14.2f, -2.6f, 6.0f, 4.4f, 6.0f, -16f, 0f, 0f, 0f, -10.2f, 0f,
				crown2, crown2, crown2, crown2, brimTop, brimBot);
		// Bent pointed hat tip
		float[] tip = {1, 24, 11, 38};
		b.faceUvRot(-1.7f, -17.3f, -0.3f, 3.4f, 4.0f, 3.4f, -34f, 0f, 6f, 0f, -13.5f, 1f,
				tip, tip, tip, tip, new float[]{40, 42, 48, 52}, tip);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardBody() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] robeFront = {1, 2, 15, 22};
		float[] mantle = {18, 2, 34, 18};
		float[] clasp = {18, 19, 26, 24};
		// Inner battle-mage tunic
		b.faceUvRot(-4.4f, 0.0f, -2.4f, 8.8f, 12.2f, 4.8f, 0f, 0f, 0f, 0f, 6f, 0f,
				robeFront, robeFront, robeFront, robeFront, robeFront, robeFront);
		// Wide draped shoulder mantle + high arcane collar
		b.faceUvRot(-5.7f, -0.9f, -3.0f, 11.4f, 4.6f, 6.0f, 0f, 0f, 0f, 0f, 1f, 0f,
				mantle, mantle, mantle, mantle, mantle, mantle);
		// Gold collar clasp on chest
		b.faceUvRot(-1.8f, 0.8f, -3.2f, 3.6f, 2.2f, 0.8f, 0f, 0f, 0f, 0f, 2f, -3f,
				clasp, clasp, clasp, clasp, clasp, clasp);
		// Flowing rear battle-mage cape panel
		b.faceUvRot(-5.0f, 0.2f, 2.2f, 10.0f, 14.5f, 1.1f, 7f, 0f, 0f, 0f, 0.2f, 2.4f,
				mantle, new float[]{24, 26, 40, 44}, robeFront, robeFront, mantle, robeFront);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardSleeve(boolean rightArm) {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float x = rightArm ? -3.45f : -1.45f;
		float[] sleeve = {1, 26, 14, 42};
		float[] shoulder = {20, 4, 34, 18};
		// Puffed mantle shoulder pad
		b.faceUvRot(rightArm ? -3.8f : -1.5f, -2.6f, -2.6f, 5.3f, 4.2f, 5.2f, 0f, 0f, rightArm ? -8f : 8f, x + 2.5f, -1f, 0f,
				shoulder, shoulder, shoulder, shoulder, shoulder, shoulder);
		// Flared bell sleeve cuff
		b.faceUvRot(x, 1.4f, -2.5f, 4.9f, 7.6f, 5.0f, 0f, 0f, 0f, 0f, 5f, 0f,
				sleeve, sleeve, sleeve, sleeve, shoulder, sleeve);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardSkirt() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] coat = {1, 26, 14, 42};
		float[] trim = {22, 26, 38, 43};
		b.faceUvRot(-2.55f, -0.2f, -2.55f, 5.1f, 9.2f, 5.1f, 0f, 0f, 0f, 0f, 4f, 0f,
				trim, trim, coat, coat, coat, coat);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardBoot() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] leather = {39, 39, 49, 55};
		b.faceUvRot(-2.45f, 7.2f, -2.45f, 4.9f, 5.0f, 4.9f, 0f, 0f, 0f, 0f, 9f, 0f,
				leather, leather, leather, leather, leather, leather);
		b.faceUvRot(-2.3f, 9.8f, -3.4f, 4.6f, 2.4f, 1.5f, 0f, 0f, 0f, 0f, 11f, -2.5f,
				leather, leather, leather, leather, leather, leather);
		return b.build();
	}
}
