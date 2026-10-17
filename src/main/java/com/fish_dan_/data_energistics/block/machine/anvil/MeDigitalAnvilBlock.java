package com.fish_dan_.data_energistics.block.machine.anvil;

import com.fish_dan_.data_energistics.blockentity.machine.anvil.MeDigitalAnvilBlockEntity;
import com.fish_dan_.data_energistics.entity.anvil.FallingDigitalAnvilEntity;
import com.fish_dan_.data_energistics.menu.machine.anvil.MeDigitalAnvilMenu;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MeDigitalAnvilBlock extends AEBaseEntityBlock<MeDigitalAnvilBlockEntity> implements Fallable {

    private static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // Match the eight elements in models/block/me_digital_anvil.json.
    private static final VoxelShape NORTH_SOUTH_SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 4, 14),
            Block.box(6, 5, 6, 10, 10, 10),
            Block.box(10, 5, 10, 12, 10, 12),
            Block.box(10, 5, 4, 12, 10, 6),
            Block.box(4, 5, 10, 6, 10, 12),
            Block.box(4, 5, 4, 6, 10, 6),
            Block.box(3, 4, 3, 13, 5, 13),
            Block.box(3, 10, 0, 13, 16, 16));
    // The geometry is symmetric under a half turn; only the two horizontal axes need distinct shapes.
    private static final VoxelShape EAST_WEST_SHAPE = NORTH_SOUTH_SHAPE.toAabbs().stream()
            .map(box -> Shapes.create(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX))
            .reduce(Shapes.empty(), Shapes::or);

    public MeDigitalAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 2);
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (pos.getY() >= level.getMinBuildHeight() && FallingBlock.isFree(level.getBlockState(pos.below())) &&
                level.getBlockEntity(pos) instanceof MeDigitalAnvilBlockEntity host) {
            FallingDigitalAnvilEntity.fall(level, pos, state, host);
        }
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replacedState, FallingBlockEntity falling) {
        if (!falling.isSilent()) level.levelEvent(1031, pos, 0);
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity falling) {
        if (!falling.isSilent()) level.levelEvent(1029, pos, 0);
    }

    @Override
    public DamageSource getFallDamageSource(Entity entity) {
        return entity.damageSources().anvil(entity);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Blockstate model rotations add 90 degrees so the long side is the front.
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? EAST_WEST_SHAPE : NORTH_SOUTH_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MeDigitalAnvilBlockEntity host) {
            MeDigitalAnvilMenu.open(serverPlayer, host);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
