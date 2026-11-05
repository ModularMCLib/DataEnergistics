package com.fish_dan_.data_energistics.client.weapon.platform.vision;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Relative surface temperatures are independent of visible-light brightness and texture color. */
final class ThermalSources {

    private ThermalSources() {}

    static boolean environmental(BlockState state, BlockGetter level, BlockPos position) {
        return state.hasDynamicLightEmission() || state.getLightEmission(level, position) > 0 || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) || state.getFluidState().is(FluidTags.WATER) || state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    static int temperature(BlockState state, Level level, BlockPos position) {
        if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)) return 255;
        if (state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) || state.getFluidState().is(FluidTags.WATER)) return 12;
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) return 235;
        int light = state.getLightEmission(level, position);
        if (light > 0) return 128 + light * 7;
        int ambient = ambient(level, position);
        if (state.is(BlockTags.LEAVES)) return ambient - 8;
        if (state.is(BlockTags.SAND)) return ambient + 6;
        return ambient;
    }

    static int ambient(Level level, BlockPos position) {
        return 86 + Math.round(Mth.clamp(level.getBiome(position).value().getBaseTemperature(), -0.5f, 2) * 22);
    }

    static int temperature(Entity entity) {
        return entity.isOnFire() ? 255 : entity instanceof LivingEntity ? 218 : ambient(entity.level(), entity.blockPosition());
    }
}
