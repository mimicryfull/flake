package dev.flake.render.vfx;

/** Reused view of one slot in the shared CPU buffer; no allocation while rendering. */
public final class SlotWriter {
	public final float[] values;
	public final int index, position, style, particle, tail, material, segment, segmentEnd, bounds;

	public SlotWriter(float[] values, int index) {
		this.values = values;
		this.index = index;
		position = 36 + index * 4;
		style = 36 + VfxBuffer.MAX_WAVES * 4 + index * 4;
		particle = VfxBuffer.PARTICLE_OFFSET + index * VfxBuffer.MOTES * 4;
		tail = particle + VfxBuffer.PARTICLES * 4;
		material = VfxBuffer.MATERIAL_OFFSET + index * VfxBuffer.MOTES * 4;
		segment = VfxBuffer.SEGMENT_OFFSET + index * VfxBuffer.SEGMENTS_PER_SLOT * 4;
		segmentEnd = segment + VfxBuffer.SEGMENTS * 4;
		bounds = VfxBuffer.BOUNDS_OFFSET + index * VfxBuffer.GROUPS * 4;
	}
}
