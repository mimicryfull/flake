package dev.flake.features.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pass-through luminous portal block whose exposed exterior faces merge seamlessly into
 * a multi-block 3D parallax vortex rendered on the client.
 */
public final class PortalVortexBlock extends Block implements EntityBlock {
	public PortalVortexBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PortalVortexBlockEntity(pos, state);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
		return adjacentState.is(this) || super.skipRendering(state, adjacentState, side);
	}
}
