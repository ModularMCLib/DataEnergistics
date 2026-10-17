package com.fish_dan_.data_energistics.blockentity.research;

import com.fish_dan_.data_energistics.block.research.ResearchStationBlock;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/** Passive network port for the left housing; its adjacent connection feeds the main station's single channel. */
public final class ResearchStationPartBlockEntity extends AENetworkedBlockEntity {

    public ResearchStationPartBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.RESEARCH_STATION_PART.get(), pos, state);
        getMainNode().setIdlePowerUsage(0);
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        Direction facing = getBlockState().getValue(ResearchStationBlock.FACING);
        // The outer side is the model's cable port; the inner side feeds the main station.
        return EnumSet.of(Direction.DOWN, facing.getCounterClockWise(), facing.getClockWise());
    }
}
