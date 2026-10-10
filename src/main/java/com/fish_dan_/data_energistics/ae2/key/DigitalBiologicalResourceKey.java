package com.fish_dan_.data_energistics.ae2.key;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** Common stable AE identity for Data Energistics-owned world resources. */
public abstract sealed class DigitalBiologicalResourceKey extends AEKey permits BloodKey, ExperienceKey {

    @Override
    public final DigitalBiologicalResourceKeyType getType() {
        return DigitalBiologicalResourceKeyType.TYPE;
    }

    @Override
    public final AEKey dropSecondary() {
        return this;
    }

    @Override
    public final CompoundTag toTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DigitalBiologicalResourceKeyType.RESOURCE_FIELD, getId().toString());
        return tag;
    }

    @Override
    public final Object getPrimaryKey() {
        return getId();
    }

    @Override
    public final void writeToPacket(RegistryFriendlyByteBuf buffer) {
        DigitalBiologicalResourceKeyType.writeToPacket(buffer, this);
    }

    @Override
    public final void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        if (amount > 0) {
            drops.add(GenericStack.wrapInItemStack(this, amount));
        }
    }

    @Override
    public final boolean hasComponents() {
        return false;
    }

    @Override
    public final ItemStack wrapForDisplayOrFilter() {
        return GenericStack.wrapInItemStack(this, 1);
    }
}
