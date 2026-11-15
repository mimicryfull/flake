package dev.flake.features.armorsets.client;

import java.util.List;

/** Exact baked Blockbench geometry for Barrel Armor helmet (wooden_bucket.json) & chestplate (barrel_thirdperson.json). */
final class BarrelMeshData {
	private BarrelMeshData() {}

	static List<ArmorMesh3D.Quad> buildBucket() {
		var b = new ArmorMesh3D.Builder(32f, 32f);
			b.blockbenchBox(-4f, 9.5f, 8.7f, 5f, 14.5f, 8.75f, -22.5f, 'z', -2.5f, 12f, 8.75f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 8.0f, 13.5f, 12.5f, 16.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 12.5f, 13.5f, 8.0f, 16.0f));
			b.blockbenchBox(-4f, 11.97f, 6.25f, 5f, 12f, 11.25f, -22.5f, 'z', -2.5f, 12f, 8.75f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 8.0f, 16.0f, 12.5f, 13.5f, 180), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 12.5f, 16.0f, 8.0f, 13.5f));
			b.blockbenchBox(3f, 0f, 3f, 13f, 10f, 13f, 0.0f, 'y', 8f, 0f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 0.0f, 0.0f, 5.0f, 5.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 0.0f, 5.0f, 5.0f, 10.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 5.0f, 0.0f, 10.0f, 5.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 5.0f, 5.0f, 10.0f, 10.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 5.0f, 15.0f, 0.0f, 10.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 15.0f, 0.0f, 10.0f, 5.0f));
			b.blockbenchBox(13f, 10f, 13f, 3f, 0f, 3f, 0.0f, 'y', -2f, 0f, -2f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 5.0f, 5.0f, 10.0f, 0.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 5.0f, 10.0f, 10.0f, 5.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 0.0f, 5.0f, 5.0f, 0.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 0.0f, 10.0f, 5.0f, 5.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 10.0f, 0.0f, 15.0f, 5.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 0.0f, 15.0f, 5.0f, 10.0f));
			b.blockbenchBox(2.75f, 0.25f, 2.75f, 13.25f, 9.75f, 13.25f, 0.0f, 'y', 8f, -0.5f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 10.0f, 8.5f, 15.0f, 13.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 10.0f, 8.5f, 15.0f, 13.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 10.0f, 8.5f, 15.0f, 13.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 10.0f, 8.5f, 15.0f, 13.0f));
			b.blockbenchBox(3f, -2f, 6f, 4f, 0f, 10f, 0.0f, 'y', 8f, 0f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 7.0f, 12.5f, 7.5f, 13.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 6.0f, 15.0f, 8.0f, 16.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 7.5f, 12.5f, 8.0f, 13.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 4.0f, 15.0f, 6.0f, 16.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 6.0f, 12.5f, 5.5f, 14.5f));
			b.blockbenchBox(12f, -2f, 6f, 13f, 0f, 10f, 0.0f, 'y', 8f, 0f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 8.0f, 12.5f, 8.5f, 13.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 2.0f, 15.0f, 4.0f, 16.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 8.5f, 12.5f, 9.0f, 13.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 0.0f, 15.0f, 2.0f, 16.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 7.0f, 12.5f, 6.5f, 14.5f));
			b.blockbenchBox(2.75f, -5.63582f, 7.82403f, 13.25f, -0.13582f, 9.32403f, 22.5f, 'x', 8f, 0.5f, 8f, 1.0f, 8.0f, 5.0f, 8.0f, 0.0f, -6.5f, 0.0f, 8.0f, 0.0f, 3.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 5.0f, 10.0f, 10.0f, 12.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 15.0f, 0.0f, 15.5f, 2.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 10.0f, 5.0f, 15.0f, 7.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 15.5f, 0.0f, 16.0f, 2.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 15.0f, 8.0f, 10.0f, 7.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 15.0f, 8.0f, 10.0f, 8.5f));
		return b.build();
	}

	static List<ArmorMesh3D.Quad> buildBarrelTorso() {
		var b = new ArmorMesh3D.Builder(64f, 64f);
			b.blockbenchBox(4.92417f, -4.56542f, 6.25f, 8.42417f, -3.06542f, 9.75f, 45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 9.75f, 2.5f, 11.5f, 3.25f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 9.75f, 3.25f, 11.5f, 4.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 9.75f, 4.0f, 11.5f, 4.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.75f, 4.75f, 11.5f, 5.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 5.0f, 11.0f, 3.25f, 9.25f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 11.5f, 0.0f, 9.75f, 1.75f));
			b.blockbenchBox(8.42418f, -3.06542f, 9.75f, 4.92418f, -4.56542f, 6.25f, 45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 9.75f, 4.75f, 11.5f, 4.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 9.75f, 5.5f, 11.5f, 4.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 9.75f, 3.25f, 11.5f, 2.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.75f, 4.0f, 11.5f, 3.25f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 9.75f, 0.0f, 11.5f, 1.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 3.25f, 11.0f, 5.0f, 9.25f));
			b.blockbenchBox(5.04917f, -4.69042f, 6.125f, 8.29917f, -2.94042f, 9.875f, 45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 11.5f, 0.0f, 13.0f, 0.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 11.75f, 1.0f, 13.25f, 1.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 1.5f, 12.5f, 0.0f, 10.75f));
			b.blockbenchBox(-0.4889f, -8.53189f, 8.35f, 4.0111f, -6.03189f, 8.375f, -22.5f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 11.5f, 4.75f, 13.75f, 6.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 13.75f, 4.75f, 11.5f, 6.0f));
			b.blockbenchBox(-0.4889f, -7.29689f, 7.125f, 4.0111f, -7.28189f, 9.625f, -22.5f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 11.5f, 6.0f, 13.75f, 4.75f, 180), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 13.75f, 6.0f, 11.5f, 4.75f));
			b.blockbenchBox(7.57583f, -4.56542f, 6.25f, 11.07583f, -3.06542f, 9.75f, -45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 11.5f, 2.5f, 9.75f, 3.25f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 11.5f, 4.75f, 9.75f, 5.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 11.5f, 4.0f, 9.75f, 4.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 11.5f, 3.25f, 9.75f, 4.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 3.25f, 11.0f, 5.0f, 9.25f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 9.75f, 0.0f, 11.5f, 1.75f));
			b.blockbenchBox(11.07582f, -3.06542f, 9.75f, 7.57582f, -4.56542f, 6.25f, -45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 11.5f, 4.75f, 9.75f, 4.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 11.5f, 4.0f, 9.75f, 3.25f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 11.5f, 3.25f, 9.75f, 2.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 11.5f, 5.5f, 9.75f, 4.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 11.5f, 0.0f, 9.75f, 1.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 5.0f, 11.0f, 3.25f, 9.25f));
			b.blockbenchBox(7.70083f, -4.69042f, 6.125f, 10.95083f, -2.94042f, 9.875f, -45.0f, 'z', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 13.0f, 0.0f, 11.5f, 0.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 13.25f, 1.0f, 11.75f, 1.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 0.0f, 12.5f, 1.5f, 10.75f));
			b.blockbenchBox(9.75f, -9.04349f, 11.037f, 9.775f, -6.04349f, 16.037f, -22.5f, 'x', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 16.0f, 1.5f, 13.5f, 3.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 13.5f, 1.5f, 16.0f, 3.0f));
			b.blockbenchBox(8.5f, -7.30849f, 11.037f, 11f, -7.29349f, 16.037f, -22.5f, 'x', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 13.5f, 0.0f, 16.0f, 1.25f, 90), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 16.0f, 0.0f, 13.5f, 1.25f, 90));
			b.blockbenchBox(5.5f, -12.375f, 5.5f, 10.5f, -11.375f, 10.5f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 9.75f, 8.0f, 12.25f, 8.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 9.75f, 8.5f, 12.25f, 9.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 9.75f, 9.0f, 12.25f, 9.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.75f, 9.5f, 12.25f, 10.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 2.5f, 7.75f, 0.0f, 10.25f));
			b.blockbenchBox(10.5f, -11.375f, 10.5f, 5.5f, -12.375f, 5.5f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 9.75f, 9.5f, 12.25f, 9.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 9.75f, 10.0f, 12.25f, 9.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 9.75f, 8.5f, 12.25f, 8.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.75f, 9.0f, 12.25f, 8.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 0.0f, 7.75f, 2.5f, 10.25f));
			b.blockbenchBox(4.75f, -11.375f, 4.75f, 11.25f, -5.875f, 11.25f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 0.0f, 5.0f, 3.25f, 7.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 6.5f, 0.0f, 9.75f, 2.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 6.5f, 2.75f, 9.75f, 5.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 3.25f, 6.5f, 6.5f, 9.25f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 6.5f, 3.25f, 3.25f, 0.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 6.5f, 3.25f, 3.25f, 6.5f));
			b.blockbenchBox(11.25f, -5.875f, 11.25f, 4.75f, -11.375f, 4.75f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 6.5f, 5.5f, 9.75f, 2.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 3.25f, 9.25f, 6.5f, 6.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 0.0f, 7.75f, 3.25f, 5.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 6.5f, 2.75f, 9.75f, 0.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 3.25f, 3.25f, 6.5f, 6.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 3.25f, 3.25f, 6.5f, 0.0f));
			b.blockbenchBox(4.625f, -16f, 9.625f, 11.375f, -5.75f, 11.375f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 2.5f, 7.75f, 3.25f, 12.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 0.0f, 0.0f, 3.25f, 5.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.0f, 8.0f, 9.75f, 13.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 13.0f, 2.5f, 9.75f, 1.75f));
			b.blockbenchBox(11.375f, -5.75f, 11.375f, 4.625f, -16f, 9.625f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 0.0f, 5.0f, 3.25f, 0.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 9.0f, 13.0f, 9.75f, 8.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 2.5f, 12.75f, 3.25f, 7.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 9.75f, 2.5f, 13.0f, 1.75f));
			b.blockbenchBox(5.5f, -5.875f, 5.5f, 10.5f, -4.875f, 10.5f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 9.75f, 10.0f, 12.25f, 10.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 0.0f, 10.25f, 2.5f, 10.75f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 5.0f, 10.5f, 7.5f, 11.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 9.75f, 10.5f, 12.25f, 11.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.UP, 9.0f, 10.5f, 6.5f, 8.0f));
			b.blockbenchBox(10.5f, -4.875f, 10.5f, 5.5f, -5.875f, 5.5f, 0.0f, 'y', 8f, -8f, 8f, 2.0f, 8.0f, -8.125f, 8.0f, 0.0f, 6.0f, 0.0f, -5.0f, 0.0f, 0.0f,
					ArmorMesh3D.bbFace(ArmorMesh3D.Face.NORTH, 5.0f, 11.0f, 7.5f, 10.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.EAST, 9.75f, 11.0f, 12.25f, 10.5f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.SOUTH, 9.75f, 10.5f, 12.25f, 10.0f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.WEST, 0.0f, 10.75f, 2.5f, 10.25f), ArmorMesh3D.bbFace(ArmorMesh3D.Face.DOWN, 6.5f, 10.5f, 9.0f, 8.0f));
		return b.build();
	}
}
