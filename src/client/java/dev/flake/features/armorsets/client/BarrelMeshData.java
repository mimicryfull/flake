package dev.flake.features.armorsets.client;

import java.util.List;

/**
 * Baked Blockbench geometry for Barrel Armor helmet (wooden_bucket) & chestplate (barrel_thirdperson)
 * with coplanar inside-out shell duplicates removed and overlapping rims slightly separated to eliminate z-fighting.
 */
final class BarrelMeshData {
	private BarrelMeshData() {}

	static List<ArmorMesh3D.Quad> buildBucket() {
		var b = new ArmorMesh3D.Builder(32f, 32f);
		// Arrow planes stuck into bucket (single-quad each; noCull renders both sides without z-fighting)
		b.blockbenchBox(-4f, 9.5f, 8.725f, 5f, 14.5f, 8.725f, -22.5f, 'z', -2.5f, 12f, 8.75f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 8.0f, 13.5f, 12.5f, 16.0f));
		b.blockbenchBox(-4f, 11.985f, 6.25f, 5f, 11.985f, 11.25f, -22.5f, 'z', -2.5f, 12f, 8.75f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 8.0f, 16.0f, 12.5f, 13.5f, 180));
		// Main bucket body (double-sided via armorCutoutNoCull; no duplicate coplanar shell)
		b.blockbenchBox(3f, 0f, 3f, 13f, 10f, 13f, 0.0f, 'y', 8f, 0f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 0.0f, 0.0f, 5.0f, 5.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 0.0f, 5.0f, 5.0f, 10.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 5.0f, 0.0f, 10.0f, 5.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 5.0f, 5.0f, 10.0f, 10.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 5.0f, 15.0f, 0.0f, 10.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 15.0f, 0.0f, 10.0f, 5.0f));
		// Outer iron hoops
		b.blockbenchBox(2.75f, 0.25f, 2.75f, 13.25f, 9.75f, 13.25f, 0.0f, 'y', 8f, -0.5f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 10.0f, 8.5f, 15.0f, 13.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 10.0f, 8.5f, 15.0f, 13.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 10.0f, 8.5f, 15.0f, 13.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 10.0f, 8.5f, 15.0f, 13.0f));
		// Cheek handle lugs (slightly offset from x=3 and x=13 to prevent coplanar z-fighting)
		b.blockbenchBox(2.92f, -2f, 6f, 4f, -0.02f, 10f, 0.0f, 'y', 8f, 0f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 7.0f, 12.5f, 7.5f, 13.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 6.0f, 15.0f, 8.0f, 16.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 7.5f, 12.5f, 8.0f, 13.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 4.0f, 15.0f, 6.0f, 16.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 6.0f, 12.5f, 5.5f, 14.5f));
		b.blockbenchBox(12f, -2f, 6f, 13.08f, -0.02f, 10f, 0.0f, 'y', 8f, 0f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 8.0f, 12.5f, 8.5f, 13.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 2.0f, 15.0f, 4.0f, 16.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 8.5f, 12.5f, 9.0f, 13.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 0.0f, 15.0f, 2.0f, 16.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 7.0f, 12.5f, 6.5f, 14.5f));
		// Wooden chin handle bar
		b.blockbenchBox(2.75f, -5.63582f, 7.82403f, 13.25f, -0.13582f, 9.32403f, 22.5f, 'x', 8f, 0.5f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 5.0f, 10.0f, 10.0f, 12.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 15.0f, 0.0f, 15.5f, 2.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 10.0f, 5.0f, 15.0f, 7.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 15.5f, 0.0f, 16.0f, 2.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 15.0f, 8.0f, 10.0f, 7.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 15.0f, 8.0f, 10.0f, 8.5f));
		return b.build();
	}

	static List<ArmorMesh3D.Quad> buildBarrelTorso() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
		// Left shoulder strap + cuff
		b.blockbenchBox(4.92417f, -4.56542f, 6.25f, 8.42417f, -3.06542f, 9.75f, 45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 9.75f, 2.5f, 11.5f, 3.25f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 9.75f, 3.25f, 11.5f, 4.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 9.75f, 4.0f, 11.5f, 4.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.75f, 4.75f, 11.5f, 5.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 5.0f, 11.0f, 3.25f, 9.25f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 11.5f, 0.0f, 9.75f, 1.75f));
		b.blockbenchBox(5.04917f, -4.69042f, 6.125f, 8.29917f, -2.94042f, 9.875f, 45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 11.5f, 0.0f, 13.0f, 0.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 11.75f, 1.0f, 13.25f, 1.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 1.5f, 12.5f, 0.0f, 10.75f));
		// Shoulder arrow (single-quad cross planes)
		b.blockbenchBox(-0.4889f, -8.53189f, 8.362f, 4.0111f, -6.03189f, 8.362f, -22.5f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 11.5f, 4.75f, 13.75f, 6.0f));
		b.blockbenchBox(-0.4889f, -7.289f, 7.125f, 4.0111f, -7.289f, 9.625f, -22.5f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 11.5f, 6.0f, 13.75f, 4.75f, 180));
		// Right shoulder strap + cuff
		b.blockbenchBox(7.57583f, -4.56542f, 6.25f, 11.07583f, -3.06542f, 9.75f, -45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 11.5f, 2.5f, 9.75f, 3.25f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 11.5f, 4.75f, 9.75f, 5.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 11.5f, 4.0f, 9.75f, 4.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 11.5f, 3.25f, 9.75f, 4.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 3.25f, 11.0f, 5.0f, 9.25f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 9.75f, 0.0f, 11.5f, 1.75f));
		b.blockbenchBox(7.70083f, -4.69042f, 6.125f, 10.95083f, -2.94042f, 9.875f, -45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 13.0f, 0.0f, 11.5f, 0.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 13.25f, 1.0f, 11.75f, 1.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 0.0f, 12.5f, 1.5f, 10.75f));
		// Back arrow (single-quad cross planes)
		b.blockbenchBox(9.762f, -9.04349f, 11.037f, 9.762f, -6.04349f, 16.037f, -22.5f, 'x', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 13.5f, 1.5f, 16.0f, 3.0f));
		b.blockbenchBox(8.5f, -7.301f, 11.037f, 11f, -7.301f, 16.037f, -22.5f, 'x', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 13.5f, 0.0f, 16.0f, 1.25f, 90));
		// Bottom rim (slightly separated from main barrel y=-11.375)
		b.blockbenchBox(5.5f, -12.40f, 5.5f, 10.5f, -11.40f, 10.5f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 9.75f, 8.0f, 12.25f, 8.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 9.75f, 8.5f, 12.25f, 9.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 9.75f, 9.0f, 12.25f, 9.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.75f, 9.5f, 12.25f, 10.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 2.5f, 7.75f, 0.0f, 10.25f));
		// Main oak barrel body (single shell; no inverted duplicate)
		b.blockbenchBox(4.75f, -11.375f, 4.75f, 11.25f, -5.875f, 11.25f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 0.0f, 5.0f, 3.25f, 7.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 6.5f, 0.0f, 9.75f, 2.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 6.5f, 2.75f, 9.75f, 5.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 3.25f, 6.5f, 6.5f, 9.25f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 6.5f, 3.25f, 3.25f, 0.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 6.5f, 3.25f, 3.25f, 6.5f));
		// Back wooden board / plank
		b.blockbenchBox(4.625f, -16f, 9.625f, 11.375f, -5.75f, 11.375f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 0.0f, 0.0f, 3.25f, 5.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 2.5f, 7.75f, 3.25f, 12.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 0.0f, 0.0f, 3.25f, 5.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.0f, 8.0f, 9.75f, 13.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 13.0f, 2.5f, 9.75f, 1.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 9.75f, 2.5f, 13.0f, 1.75f));
		// Top rim (slightly separated from main barrel y=-5.875)
		b.blockbenchBox(5.5f, -5.85f, 5.5f, 10.5f, -4.85f, 10.5f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 9.75f, 10.0f, 12.25f, 10.5f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 0.0f, 10.25f, 2.5f, 10.75f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 5.0f, 10.5f, 7.5f, 11.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.75f, 10.5f, 12.25f, 11.0f),
				ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 9.0f, 10.5f, 6.5f, 8.0f));
		return b.build();
	}
}
