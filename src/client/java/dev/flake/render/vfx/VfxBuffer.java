package dev.flake.render.vfx;

/** The single 15,136-byte std140 post-pass ABI, below the minimum 16 KiB block limit. */
public final class VfxBuffer {
	public static final int MAX_WAVES = 4;
	public static final int MOTES = 32, GROUPS = 8, SEGMENTS_PER_SLOT = 64;
	public static final int PARTICLES = MAX_WAVES * MOTES;
	public static final int IMPACT_OFFSET = 36 + MAX_WAVES * 8;
	public static final int PARTICLE_OFFSET = IMPACT_OFFSET + 4;
	public static final int MATERIAL_OFFSET = PARTICLE_OFFSET + PARTICLES * 8;
	public static final int SEGMENT_OFFSET = MATERIAL_OFFSET + PARTICLES * 4;
	public static final int SEGMENTS = MAX_WAVES * SEGMENTS_PER_SLOT;
	public static final int BOUNDS_OFFSET = SEGMENT_OFFSET + SEGMENTS * 8;
	public static final int UNIFORM_BYTES = (BOUNDS_OFFSET + MAX_WAVES * GROUPS * 4) * Float.BYTES;

	private VfxBuffer() {}
}
