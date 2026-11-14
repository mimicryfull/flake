package dev.flake.features.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Lightweight non-ticking block entity used on the client to discover portal blocks without chunk scans. */
public final class PortalVortexBlockEntity extends BlockEntity {
	public PortalVortexBlockEntity(BlockPos pos, BlockState state) {
		super(PortalVortexFeature.blockEntityType(), pos, state);
	}
}
