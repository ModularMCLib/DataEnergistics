package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Maps visible game state to relative heat; zero means the ordinary scene supplies the ambient background. */
final class ThermalSources {

    private ThermalSources() {}

    static boolean environmental(BlockState state, BlockGetter level, BlockPos position) {
        return state.hasDynamicLightEmission() || state.getLightEmission(level, position) > 0 || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) || state.getFluidState().is(FluidTags.WATER) || state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    static int temperature(BlockState state, BlockGetter level, BlockPos position) {
        if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)) return 255;
        if (state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) || state.getFluidState().is(FluidTags.WATER)) return 12;
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) return 235;
        int light = state.getLightEmission(level, position);
        return light > 0 ? 128 + light * 7 : 0;
    }

    static int temperature(Entity entity) {
        return entity.isOnFire() ? 255 : entity instanceof LivingEntity ? 218 : 0;
    }
}
