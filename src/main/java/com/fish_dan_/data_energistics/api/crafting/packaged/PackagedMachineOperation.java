package com.fish_dan_.data_energistics.api.crafting.packaged;

import appeng.api.stacks.AEKey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Server-thread, single-operation ownership boundary for real packaged crafting.
 * World-side material transfers and their matching accounting must happen synchronously in one callback.
 * Adapters must never retain this view between ticks, refund delivered materials, or collect unrelated items.
 */
public interface PackagedMachineOperation {

    /** Identity retained across saves and used to claim machine and dropped-item ownership. */
    UUID id();

    /** The loaded provider's dimension; all targets are in this same level. */
    ServerLevel level();

    /** Immutable main-machine position, fixed when this operation accepted its inputs. */
    BlockPos position();

    /** Configured capability face on the main machine. */
    Direction face();

    /** Exact recipe identity captured when accepting the operation. */
    ResourceLocation recipeId();

    /** Adapter-owned persisted progress, including preparation data; changes require changed(). */
    CompoundTag progress();

    /** Remaining provider-owned input of this exact key; always non-negative. */
    BigInteger available(AEKey key);

    /** Records a strictly positive amount actually transferred to the machine; rejects overdrafts. */
    void delivered(AEKey key, long amount);

    /** Takes ownership of a strictly positive amount actually extracted from this operation's machine. */
    void returned(AEKey key, long amount);

    /** Persists progress modified by the adapter during this server tick. */
    void changed();

    /** Marks completion only after the machine has completed and all operation-owned values are settled. */
    void complete();
}
