package com.fish_dan_.data_energistics.ae2.sanctum;

import com.fish_dan_.data_energistics.ae2.sanctum.inventory.InterfaceInventoryAccess;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.helpers.InterfaceLogicHost;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

public interface DataSanctumLargeInterfaceHost extends InterfaceLogicHost {

    DataSanctumReturnInventory getReturnInventory();

    /** Server-thread capability view: insertion enters the return bar; extraction reads stock or a live AE mapping. */
    default GenericInternalInventory getExternalInventory() {
        return new InterfaceInventoryAccess(getReturnInventory(), getInterfaceLogic().getStorage());
    }

    int getInstalledCapacityCardCount();

    @Nullable
    Level getInterfaceLevel();

    BlockPos getInterfaceBlockPos();

    ItemStack getMainMenuIcon();

    default int getUnlockedPageCount() {
        return Math.min(
                DataSanctumInterfaceConstants.PAGE_COUNT,
                DataSanctumInterfaceConstants.BASE_PAGE_COUNT + getInstalledCapacityCardCount() * DataSanctumInterfaceConstants.PAGES_PER_CAPACITY_CARD);
    }
}
