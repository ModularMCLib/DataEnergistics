package com.fish_dan_.data_energistics.utils.vision;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Relative surface temperatures are independent of visible-light brightness and texture color. */
@OnlyIn(Dist.CLIENT)
public final class ThermalSources {

    private ThermalSources() {}

    public static int temperature(BlockState state, Level level, BlockPos position) {
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

    public static int ambient(Level level, BlockPos position) {
        return 86 + Math.round(Mth.clamp(level.getBiome(position).value().getBaseTemperature(), -0.5f, 2) * 22);
    }

    public static int temperature(Entity entity) {
        return entity.isOnFire() ? 255 : entity instanceof LivingEntity ? 218 : ambient(entity.level(), entity.blockPosition());
    }

    /** Ambient objects are already represented by the scene-depth thermal image. */
    public static boolean hasHeat(Entity entity) {
        return entity instanceof LivingEntity || entity.isOnFire();
    }
}
