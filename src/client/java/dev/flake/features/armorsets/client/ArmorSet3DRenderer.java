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
 * Phoenix/Hydra armor with crests & horns, and custom Royal-Blue + Glowing-Green-Crystal
 * Battle-Mage Wizard armor) bound to HumanoidModel bones so all player animations and
 * Loadout Editor mannequins stay synced.
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

	// Barrel Knight meshes (exact Blockbench models w/o z-fighting + forearm bracers, greaves, boots)
	private static final List<ArmorMesh3D.Quad> BARREL_BUCKET = BarrelMeshData.buildBucket();
	private static final List<ArmorMesh3D.Quad> BARREL_TORSO = BarrelMeshData.buildBarrelTorso();
	private static final List<ArmorMesh3D.Quad> BARREL_ARM_R = buildBarrelArm(true);
	private static final List<ArmorMesh3D.Quad> BARREL_ARM_L = buildBarrelArm(false);
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

	// Custom Original Wizard Armor: Blue cloth + Glowing Emerald Green Crystals
	private static final List<ArmorMesh3D.Quad> WIZARD_HAT_CLOTH = buildWizardHatCloth();
	private static final List<ArmorMesh3D.Quad> WIZARD_HAT_CRYSTALS = buildWizardHatCrystals();
	private static final List<ArmorMesh3D.Quad> WIZARD_ROBE_CLOTH = buildWizardBodyCloth();
	private static final List<ArmorMesh3D.Quad> WIZARD_ROBE_CRYSTALS = buildWizardBodyCrystals();
	private static final List<ArmorMesh3D.Quad> WIZARD_SLEEVE_R = buildWizardSleeve(true);
	private static final List<ArmorMesh3D.Quad> WIZARD_SLEEVE_L = buildWizardSleeve(false);
	private static final List<ArmorMesh3D.Quad> WIZARD_SLEEVE_CRYSTAL_R = buildWizardSleeveCrystals(true);
	private static final List<ArmorMesh3D.Quad> WIZARD_SLEEVE_CRYSTAL_L = buildWizardSleeveCrystals(false);
	private static final List<ArmorMesh3D.Quad> WIZARD_SKIRT_CLOTH = buildWizardSkirtCloth();
	private static final List<ArmorMesh3D.Quad> WIZARD_SKIRT_CRYSTALS = buildWizardSkirtCrystals();
	private static final List<ArmorMesh3D.Quad> WIZARD_BOOT_CLOTH = buildWizardBootCloth();
	private static final List<ArmorMesh3D.Quad> WIZARD_BOOT_CRYSTALS = buildWizardBootCrystals();

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

	private static void submitOnBoneWithCrystals(PoseStack ps, SubmitNodeCollector col, ModelPart bone,
			Identifier tex, int light, List<ArmorMesh3D.Quad> cloth, List<ArmorMesh3D.Quad> crystals) {
		ps.pushPose();
		bone.translateAndRotate(ps);
		ArmorMesh3D.submit(ps, col, tex, light, cloth);
		ArmorMesh3D.submit(ps, col, tex, ArmorMesh3D.FULL_BRIGHT, crystals);
		ps.popPose();
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
			case HEAD -> submitOnBoneWithCrystals(ps, col, m.head, TEX_WIZARD_HAT, light, WIZARD_HAT_CLOTH, WIZARD_HAT_CRYSTALS);
			case CHEST -> {
				submitOnBoneWithCrystals(ps, col, m.body, TEX_WIZARD_ROBE, light, WIZARD_ROBE_CLOTH, WIZARD_ROBE_CRYSTALS);
				submitOnBoneWithCrystals(ps, col, m.rightArm, TEX_WIZARD_ROBE, light, WIZARD_SLEEVE_R, WIZARD_SLEEVE_CRYSTAL_R);
				submitOnBoneWithCrystals(ps, col, m.leftArm, TEX_WIZARD_ROBE, light, WIZARD_SLEEVE_L, WIZARD_SLEEVE_CRYSTAL_L);
			}
			case LEGS -> {
				submitOnBoneWithCrystals(ps, col, m.rightLeg, TEX_WIZARD_ROBE, light, WIZARD_SKIRT_CLOTH, WIZARD_SKIRT_CRYSTALS);
				submitOnBoneWithCrystals(ps, col, m.leftLeg, TEX_WIZARD_ROBE, light, WIZARD_SKIRT_CLOTH, WIZARD_SKIRT_CRYSTALS);
			}
			case FEET -> {
				submitOnBoneWithCrystals(ps, col, m.rightLeg, TEX_WIZARD_ROBE, light, WIZARD_BOOT_CLOTH, WIZARD_BOOT_CRYSTALS);
				submitOnBoneWithCrystals(ps, col, m.leftLeg, TEX_WIZARD_ROBE, light, WIZARD_BOOT_CLOTH, WIZARD_BOOT_CRYSTALS);
			}
			default -> {}
		}
	}

	// ---- Phoenix & Hydra 64x64 3D plates ----

	private static List<ArmorMesh3D.Quad> buildPlateHelmBase() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		b.cubeUv(0, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.55f, false);
		float[] brow = {33, 9, 47, 15};
		b.faceUvRot(-4.7f, -8.3f, -5.0f, 9.4f, 2.2f, 2.5f, 0f, 0f, 0f, 0f, -7f, -4f,
				brow, null, brow, brow, new float[]{40, 0, 44, 4}, brow);
		float[] gem = {40, 0, 44, 4};
		b.faceUvRot(-1.1f, -10.0f, -4.2f, 2.2f, 2.0f, 7.6f, 0f, 0f, 0f, 0f, -9f, 0f,
				gem, gem, gem, gem, gem, gem);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPhoenixPlume() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
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
		b.cubeUv(32, 0, -4f, -8f, -4f, 8f, 8f, 8f, 0.95f, false);
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
		float[] top = {7, 16, 17, 22};
		b.faceUvRot(-4.65f, 0.1f, -2.65f, 9.3f, 11.2f, 5.3f, 0f, 0f, 0f, 0f, 6f, 0f,
				new float[]{6, 23, 17, 29}, new float[]{6, 23, 17, 29},
				new float[]{0, 23, 7, 29}, new float[]{16, 23, 23, 29}, top, top);
		b.faceUvRot(-3.2f, 0.9f, -3.3f, 6.4f, 5.8f, 0.9f, 0f, 0f, 0f, 0f, 3f, -2.8f,
				new float[]{7, 23, 16, 29}, null,
				new float[]{1, 23, 6, 29}, new float[]{17, 23, 22, 29},
				new float[]{8, 17, 15, 21}, new float[]{8, 27, 15, 29});
		b.faceUvRot(-3.8f, -0.7f, -2.5f, 7.6f, 1.4f, 5.0f, 0f, 0f, 0f, 0f, 0f, 0f,
				new float[]{7, 17, 17, 21}, new float[]{7, 17, 17, 21},
				new float[]{1, 24, 6, 28}, new float[]{17, 24, 22, 28}, top, top);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPlateArm(boolean rightArm) {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float ax = rightArm ? -3.0f : -1.0f;
		float pTilt = rightArm ? -8f : 8f;
		float px = rightArm ? -3.7f : -1.5f;
		b.faceUvRot(px, -2.8f, -2.6f, 5.2f, 5.4f, 5.2f, 0f, 0f, pTilt, px + 2.6f, 0f, 0f,
				new float[]{32, 29, 40, 35}, new float[]{44, 29, 52, 35},
				new float[]{28, 29, 32, 35}, new float[]{40, 29, 44, 35},
				new float[]{32, 25, 40, 29}, new float[]{32, 25, 40, 29});
		float rx2 = rightArm ? -4.1f : -0.6f;
		b.faceUvRot(rx2, -3.3f, -2.3f, 4.7f, 2.0f, 4.6f, 0f, 0f, rightArm ? -16f : 16f, rx2 + 2.3f, -2.2f, 0f,
				new float[]{33, 29, 39, 32}, new float[]{45, 29, 51, 32},
				new float[]{28, 29, 32, 32}, new float[]{40, 29, 44, 32},
				new float[]{32, 25, 40, 29}, new float[]{32, 25, 40, 29});
		b.cubeUv(19, 41, ax, 5.2f, -2.0f, 4f, 4f, 4f, 0.45f, !rightArm);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPlateBelt() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		b.faceUvRot(-4.4f, 8.8f, -2.4f, 8.8f, 3.6f, 4.8f, 0f, 0f, 0f, 0f, 10f, 0f,
				new float[]{6, 34, 16, 40}, new float[]{18, 34, 24, 40},
				new float[]{0, 34, 6, 40}, new float[]{16, 34, 22, 40},
				new float[]{6, 34, 16, 36}, new float[]{6, 38, 16, 40});
		b.faceUvRot(-1.4f, 8.2f, -3.1f, 2.8f, 4.8f, 1.3f, 0f, 0f, 0f, 0f, 10f, -2.5f,
				new float[]{4, 17, 6, 25}, new float[]{4, 17, 6, 25},
				new float[]{0, 17, 4, 25}, new float[]{6, 17, 10, 25},
				new float[]{4, 13, 6, 17}, new float[]{4, 13, 6, 17});
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPlateThigh(boolean mirror) {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		b.cubeUv(24, 29, -2f, -0.2f, -2f, 4f, 9.2f, 4f, 0.52f, mirror);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildPlateBoot() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] panel = {9, 45, 17, 52};
		b.faceUvRot(-2.45f, 7.1f, -2.45f, 4.9f, 5.1f, 4.9f, 0f, 0f, 0f, 0f, 9f, 0f,
				panel, panel, panel, panel, new float[]{10, 46, 16, 49}, new float[]{9, 55, 17, 62});
		float[] toe = {9, 57, 17, 62};
		b.faceUvRot(-2.35f, 9.8f, -3.45f, 4.7f, 2.4f, 1.5f, 0f, 0f, 0f, 0f, 11f, -2.5f,
				toe, toe, new float[]{19, 50, 26, 56}, new float[]{19, 50, 26, 56},
				new float[]{19, 54, 26, 57}, toe);
		return b.build();
	}

	// ---- Barrel Knight auxiliary pieces (kept clear of barrel torso to avoid any z-fighting) ----

	private static List<ArmorMesh3D.Quad> buildBarrelArm(boolean rightArm) {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float x = rightArm ? -3.45f : -1.45f;
		float[] plank = {1, 2, 12, 18};
		float[] hoop = {39, 32, 49, 44};
		// Forearm oak & iron bracer positioned below barrel torso straps
		b.faceUvRot(x, 4.9f, -2.45f, 4.9f, 4.4f, 4.9f, 0f, 0f, 0f, 0f, 7f, 0f,
				plank, plank, hoop, hoop, hoop, hoop);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildBarrelLeg() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] plank = {1, 2, 12, 18};
		float[] leather = {13, 37, 20, 44};
		// Lower-thigh / knee greaves kept slightly below barrel lower rim
		b.faceUvRot(-2.4f, 2.2f, -2.4f, 4.8f, 5.2f, 4.8f, 0f, 0f, 0f, 0f, 5f, 0f,
				plank, plank, leather, leather, plank, leather);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildBarrelBoot() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] iron = {39, 32, 49, 44};
		float[] wood = {2, 20, 12, 30};
		b.faceUvRot(-2.5f, 7.6f, -2.5f, 5.0f, 4.6f, 5.0f, 0f, 0f, 0f, 0f, 9f, 0f,
				iron, iron, wood, wood, iron, iron);
		b.faceUvRot(-2.4f, 9.9f, -3.5f, 4.8f, 2.3f, 1.3f, 0f, 0f, 0f, 0f, 11f, -2.5f,
				iron, iron, iron, iron, iron, iron);
		return b.build();
	}

	// ---- Custom Royal-Blue & Emerald-Crystal Wizard Armor ----

	private static List<ArmorMesh3D.Quad> buildWizardHatCloth() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] brimTop = {0, 0, 16, 16};
		float[] brimBot = {48, 0, 64, 16};
		float[] hatBand = {16, 0, 32, 16};
		float[] spire = {32, 0, 48, 16};

		// Wide outer archmage brim (tilted back 5 deg for clear sightline)
		b.faceUvRot(-7.6f, -6.5f, -7.6f, 15.2f, 1.0f, 15.2f, -5f, 0f, 0f, 0f, -6f, 0f,
				hatBand, hatBand, hatBand, hatBand, brimTop, brimBot);
		// Beveled upper brim ring stepping into crown
		b.faceUvRot(-5.6f, -7.2f, -5.6f, 11.2f, 0.7f, 11.2f, -5f, 0f, 0f, 0f, -6f, 0f,
				spire, spire, spire, spire, brimTop, brimBot);
		// Lower crown base with emerald ribbon band
		b.faceUvRot(-4.3f, -10.8f, -4.3f, 8.6f, 3.8f, 8.6f, -5f, 0f, 0f, 0f, -6f, 0f,
				hatBand, hatBand, hatBand, hatBand, brimBot, brimBot);
		// Middle cone tier tilting backward
		b.faceUvRot(-3.1f, -14.3f, -2.6f, 6.2f, 4.1f, 6.2f, -16f, 0f, 4f, 0f, -10.5f, 0.4f,
				spire, spire, spire, spire, brimBot, brimBot);
		// Upper curved spire tier
		b.faceUvRot(-2.0f, -17.5f, -0.5f, 4.0f, 3.9f, 4.0f, -30f, 0f, 10f, 0f, -13.8f, 1.3f,
				spire, spire, spire, spire, brimBot, brimBot);
		// Pointed hat tip
		b.faceUvRot(-1.1f, -19.8f, 1.3f, 2.2f, 3.0f, 2.2f, -44f, 0f, 14f, 0f, -16.8f, 2.2f,
				spire, spire, spire, spire, brimBot, brimBot);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardHatCrystals() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] cry = {0, 16, 16, 32};
		// Center diamond emerald crystal mounted on front of hatband
		b.faceUvRot(-1.35f, -8.95f, -4.95f, 2.7f, 2.7f, 1.0f, -5f, 0f, 45f, 0f, -7.6f, -4.4f,
				cry, cry, cry, cry, cry, cry);
		// Left & right hat brim arcane crystal shards
		b.faceUvRot(-6.3f, -8.3f, -1.0f, 1.3f, 2.4f, 1.3f, -5f, 0f, -26f, -5.6f, -6.8f, -0.3f,
				cry, cry, cry, cry, cry, cry);
		b.faceUvRot(5.0f, -8.3f, -1.0f, 1.3f, 2.4f, 1.3f, -5f, 0f, 26f, 5.6f, -6.8f, -0.3f,
				cry, cry, cry, cry, cry, cry);
		// Glowing green spire crystal floating/capping the bent tip
		b.faceUvRot(-0.75f, -21.9f, 2.3f, 1.5f, 2.4f, 1.5f, -44f, 45f, 14f, 0f, -19.5f, 3.0f,
				cry, cry, cry, cry, cry, cry);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardBodyCloth() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] front = {0, 0, 16, 24};
		float[] back = {16, 0, 32, 24};
		float[] mantle = {32, 0, 48, 16};
		// Inner tailored blue mage tunic
		b.faceUvRot(-4.4f, 0.0f, -2.4f, 8.8f, 12.2f, 4.8f, 0f, 0f, 0f, 0f, 6f, 0f,
				front, back, back, back, mantle, mantle);
		// Waist belt sash
		b.faceUvRot(-4.65f, 9.6f, -2.65f, 9.3f, 2.3f, 5.3f, 0f, 0f, 0f, 0f, 10.7f, 0f,
				new float[]{0, 18, 16, 22}, new float[]{0, 18, 16, 22},
				new float[]{0, 18, 16, 22}, new float[]{0, 18, 16, 22}, mantle, mantle);
		// Wide shoulder mantle + arcane breast wrap
		b.faceUvRot(-5.6f, -0.5f, -2.85f, 11.2f, 3.8f, 5.7f, 0f, 0f, 0f, 0f, 1.4f, 0f,
				mantle, mantle, mantle, mantle, mantle, mantle);
		// High standing wizard collar around neck/sides
		b.faceUvRot(-4.9f, -2.3f, -1.8f, 9.8f, 2.1f, 4.7f, -10f, 0f, 0f, 0f, -0.5f, 1.5f,
				null, mantle, mantle, mantle, mantle, mantle);
		// Flowing rear royal-blue archmage cape
		b.faceUvRot(-5.0f, 0.3f, 2.35f, 10.0f, 14.2f, 0.9f, 9f, 0f, 0f, 0f, 0.3f, 2.5f,
				back, back, mantle, mantle, mantle, back);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardBodyCrystals() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] cry = {48, 16, 64, 32};
		// Chest sternum diamond arcane crystal focus
		b.faceUvRot(-1.35f, 2.2f, -3.05f, 2.7f, 2.7f, 0.9f, 0f, 0f, 45f, 0f, 3.55f, -2.6f,
				cry, cry, cry, cry, cry, cry);
		// Waist belt buckle crystal
		b.faceUvRot(-0.95f, 9.75f, -3.15f, 1.9f, 1.9f, 0.8f, 0f, 0f, 45f, 0f, 10.7f, -2.75f,
				cry, cry, cry, cry, cry, cry);
		// Collar side rune crystals
		b.faceUvRot(-4.6f, 0.2f, -3.1f, 1.3f, 1.9f, 0.9f, 0f, 0f, -18f, -4.0f, 1.1f, -2.65f,
				cry, cry, cry, cry, cry, cry);
		b.faceUvRot(3.3f, 0.2f, -3.1f, 1.3f, 1.9f, 0.9f, 0f, 0f, 18f, 4.0f, 1.1f, -2.65f,
				cry, cry, cry, cry, cry, cry);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardSleeve(boolean rightArm) {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] sleeve = {0, 24, 16, 40};
		float[] mantle = {32, 0, 48, 16};
		float mx = rightArm ? -3.7f : -1.5f;
		// Layered shoulder pad
		b.faceUvRot(mx, -2.6f, -2.6f, 5.2f, 4.2f, 5.2f, 0f, 0f, rightArm ? -8f : 8f, mx + 2.6f, -0.5f, 0f,
				mantle, mantle, mantle, mantle, mantle, mantle);
		// Flared bell mage sleeve with emerald cuff trim
		float sx = rightArm ? -3.55f : -1.55f;
		b.faceUvRot(sx, 1.8f, -2.55f, 5.1f, 7.2f, 5.1f, 0f, 0f, 0f, sx + 2.55f, 5.4f, 0f,
				sleeve, sleeve, sleeve, sleeve, mantle, sleeve);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardSleeveCrystals(boolean rightArm) {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] cry = {48, 16, 64, 32};
		float cx = rightArm ? -3.4f : 1.7f;
		float sx = rightArm ? -4.3f : 2.9f;
		// Tall primary shoulder crystal spire
		b.faceUvRot(cx, -5.6f, -0.85f, 1.7f, 3.6f, 1.7f, 0f, 0f, rightArm ? -22f : 22f, cx + 0.85f, -2.3f, 0f,
				cry, cry, cry, cry, cry, cry);
		// Secondary outer shoulder crystal shard
		b.faceUvRot(sx, -4.3f, -0.65f, 1.3f, 2.5f, 1.3f, 0f, 0f, rightArm ? -42f : 42f, sx + 0.65f, -2.0f, 0f,
				cry, cry, cry, cry, cry, cry);
		// Wrist cuff gem
		float wx = rightArm ? -3.9f : 2.7f;
		b.faceUvRot(wx, 6.2f, -0.6f, 1.2f, 1.5f, 1.2f, 0f, 0f, 0f, wx + 0.6f, 7f, 0f,
				cry, cry, cry, cry, cry, cry);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardSkirtCloth() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] skirt = {16, 24, 32, 40};
		// Independent battle-kilt tasset skirt around each leg for clean sprint/jump motion
		b.faceUvRot(-2.45f, -0.1f, -2.45f, 4.9f, 9.4f, 4.9f, 0f, 0f, 0f, 0f, 4.5f, 0f,
				skirt, skirt, skirt, skirt, skirt, skirt);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardSkirtCrystals() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] cry = {48, 16, 64, 32};
		// Faceted emerald knee-guard crystal
		b.faceUvRot(-0.9f, 5.2f, -2.95f, 1.8f, 1.8f, 0.85f, 0f, 0f, 45f, 0f, 6.1f, -2.5f,
				cry, cry, cry, cry, cry, cry);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardBootCloth() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] boot = {32, 24, 48, 40};
		float[] cuff = {32, 24, 48, 28};
		// Folded emerald-trimmed boot cuff
		b.faceUvRot(-2.55f, 6.8f, -2.55f, 5.1f, 2.3f, 5.1f, 0f, 0f, 0f, 0f, 8f, 0f,
				cuff, cuff, cuff, cuff, cuff, cuff);
		// Deep blue lower boot shaft & sole
		b.faceUvRot(-2.4f, 8.9f, -2.4f, 4.8f, 3.3f, 4.8f, 0f, 0f, 0f, 0f, 10.5f, 0f,
				boot, boot, boot, boot, boot, boot);
		// Upturned pointed wizard toe cap
		b.faceUvRot(-1.9f, 9.9f, -4.1f, 3.8f, 2.0f, 2.1f, -10f, 0f, 0f, 0f, 11.2f, -2.2f,
				boot, boot, boot, boot, boot, boot);
		return b.build();
	}

	private static List<ArmorMesh3D.Quad> buildWizardBootCrystals() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		float[] cry = {48, 16, 64, 32};
		// Glowing emerald toe gem
		b.faceUvRot(-0.6f, 9.75f, -4.45f, 1.2f, 1.2f, 0.8f, -10f, 0f, 45f, 0f, 10.35f, -4.05f,
				cry, cry, cry, cry, cry, cry);
		// Emerald heel crystal spur
		b.faceUvRot(-0.55f, 9.8f, 2.15f, 1.1f, 1.5f, 0.9f, 18f, 0f, 0f, 0f, 10.5f, 2.4f,
				cry, cry, cry, cry, cry, cry);
		return b.build();
	}
}
