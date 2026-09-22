package com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import de.ellpeck.naturesaura.blocks.multi.Multiblocks;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** Nether-only Nature's Aura altar route; native aura and recipe ticking remain untouched. */
public final class NatureCrimsonAltarAdapter implements PackagedMachineAdapter {

    private final NatureAltarAdapter delegate = new NatureAltarAdapter();

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("naturesaura_crimson_altar");
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return delegate.recipeTypes();
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.dimension() == Level.NETHER && delegate.recognizes(level, position) && Multiblocks.ALTAR.isComplete(level, position);
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        return recognizes(level, position) ? delegate.prepare(level, position, face, recipeId, pattern, inputs) : null;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        return delegate.occupiedPositions(level, position, preparation);
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        return delegate.advance(operation);
    }

    @Override
    public boolean recoverRemoved(PackagedMachineOperation operation) {
        return delegate.recoverRemoved(operation);
    }
}
