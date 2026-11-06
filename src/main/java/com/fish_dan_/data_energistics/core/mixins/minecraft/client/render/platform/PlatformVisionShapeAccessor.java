package com.fish_dan_.data_energistics.core.mixins.minecraft.client.render.platform;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Client-thread capture boundary; workers receive copied occupancy, never this mutable Minecraft object. */
@Mixin(VoxelShape.class)
public interface PlatformVisionShapeAccessor {

    /** Returns the backing grid only for bounded, read-only capture on the client thread. */
    @Accessor("shape")
    DiscreteVoxelShape dataEnergistics$visionGrid();
}
