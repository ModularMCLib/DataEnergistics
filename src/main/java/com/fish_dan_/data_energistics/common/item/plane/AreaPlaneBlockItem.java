package com.fish_dan_.data_energistics.common.item.plane;

import com.fish_dan_.data_energistics.common.block.plane.AreaPlaneBlock;
import com.fish_dan_.data_energistics.common.plane.structure.AreaPlaneStructure;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

public final class AreaPlaneBlockItem extends BlockItem {

    public AreaPlaneBlockItem(AreaPlaneBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return AreaPlaneStructure.place(context, state, () -> super.placeBlock(context, state));
    }
}
