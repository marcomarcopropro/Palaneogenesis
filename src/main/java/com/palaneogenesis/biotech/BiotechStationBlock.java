package com.palaneogenesis.biotech;

import com.palaneogenesis.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Estación de Trabajo Bio-Tech: lo que queda cuando la Llave Inglesa transforma una mesa de crafteo.
 * Ya no es una mesa vanilla (no abre la cuadrícula 3x3): su clic derecho abre solo el menú Bio-Tech.
 * Al romperla suelta todo lo que tenga adentro y se dropea a sí misma (loot table).
 */
public class BiotechStationBlock extends BaseEntityBlock {

	public BiotechStationBlock(Properties properties) {
		super(properties);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BiotechStationBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.BIOTECH_STATION.get(), BiotechStationBlockEntity::serverTick);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof BiotechStationBlockEntity station && player instanceof ServerPlayer serverPlayer) {
			NetworkHooks.openScreen(serverPlayer, station, pos);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity instanceof BiotechStationBlockEntity station) {
				station.dropContents(level, pos);
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
