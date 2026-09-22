package com.fish_dan_.data_energistics.mixin.magic.naturesaura;

import net.minecraft.world.item.ItemStack;

import de.ellpeck.naturesaura.blocks.tiles.BlockEntityOfferingTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;

/**
 * Exposes the actual native output queue for durable recovery, without reconstructing recipe output.
 * Only the server thread may inspect it; removing entries transfers ownership to the operation.
 */
@Mixin(value = BlockEntityOfferingTable.class, remap = false)
public interface OfferingTableQueueAccessor {

    /** Non-null live queue, containing outputs whose native inputs have already been consumed. */
    @Accessor("itemsToSpawn")
    Queue<ItemStack> dataEnergistics$queuedOutputs();
}
