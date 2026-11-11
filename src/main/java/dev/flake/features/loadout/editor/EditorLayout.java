package dev.flake.features.loadout.editor;

/** GUI-unit layout, independent of physical resolution and Minecraft's GUI scale. */
public record EditorLayout(int x, int y, int width, int height, int header, int sideWidth,
		int gap, int bodyY, int bodyHeight, int slotHeight, int statsY, int statsHeight) {
	public static EditorLayout fit(int screenWidth, int screenHeight) {
		int margin = screenWidth < 420 || screenHeight < 280 ? 4 : 16;
		int w = Math.min(540, screenWidth - margin * 2);
		int h = Math.min(340, screenHeight - margin * 2);
		int x = (screenWidth - w) / 2, y = (screenHeight - h) / 2;
		int header = h < 270 ? 30 : 38, stats = h < 270 ? 56 : 66;
		int gap = w < 440 ? 5 : 8;
		int body = h - header - stats - gap * 2;
		int side = Math.min(156, (w - gap * 4) * 32 / 100);
		int slotH = Math.clamp((body - gap * 3) / 4, 24, 42);
		return new EditorLayout(x, y, w, h, header, side, gap, y + header,
				body, slotH, y + h - stats - gap, stats);
	}
	public int leftX() { return x + gap; }
	public int rightX() { return x + width - gap - sideWidth; }
	public int centerX() { return leftX() + sideWidth + gap; }
	public int centerWidth() { return rightX() - gap - centerX(); }
	public int slotY(int index) { return bodyY + index * (slotHeight + gap); }
}
