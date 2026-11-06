package com.fish_dan_.data_energistics.integration.magic.naturesaura.packaged;

import net.minecraft.world.item.ItemStack;

import java.util.Queue;

/**
 * Exposes the actual native output queue for durable recovery, without reconstructing recipe output.
 * Only the server thread may inspect it; removing entries transfers ownership to the operation.
 */
public interface BlockEntityOfferingTableAccessor {

    /** Non-null live queue, containing outputs whose native inputs have already been consumed. */
    Queue<ItemStack> dataEnergistics$queuedOutputs();
}
