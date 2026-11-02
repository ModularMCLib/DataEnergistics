package com.fish_dan_.data_energistics.common.blockentity.plane;

import com.fish_dan_.data_energistics.common.block.plane.AreaPlaneBlock;
import com.fish_dan_.data_energistics.common.data.DEBlockEntities;
import com.fish_dan_.data_energistics.common.plane.structure.AreaPlaneStructure;

import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public final class AreaPlaneBlockEntity extends AENetworkedBlockEntity {

    private int structureTicks;

    public AreaPlaneBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.AREA_PLANE.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1);
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(getBlockState().getValue(AreaPlaneBlock.FACING).getOpposite());
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return direction == getBlockState().getValue(AreaPlaneBlock.FACING).getOpposite() ? AECableType.SMART : AECableType.NONE;
    }

    public void serverTick() {
        if (++structureTicks < 20) return;
        structureTicks = 0;
        var plane = (AreaPlaneBlock) getBlockState().getBlock();
        if (AreaPlaneStructure.integrity(getLevel(), worldPosition, plane.layout(getBlockState())) == AreaPlaneStructure.Integrity.BROKEN) {
            getLevel().destroyBlock(worldPosition, true);
        }
    }
}
