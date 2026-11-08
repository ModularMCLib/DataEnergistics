package com.fish_dan_.data_energistics.core.mixins.normal.net.minecraft.world.phys.shapes;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Client-thread capture boundary; workers receive copied occupancy, never this mutable Minecraft object.
 */
@NullMarked
@Mixin(VoxelShape.class)
public interface VoxelShapeAccessor {

    /**
     * Returns the backing grid only for bounded, read-only capture on the client thread.
     */
    @Accessor("shape")
    DiscreteVoxelShape dataEnergistics$visionGrid();
}
