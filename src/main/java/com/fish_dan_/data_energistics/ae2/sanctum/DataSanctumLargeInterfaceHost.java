package com.fish_dan_.data_energistics.ae2.sanctum;

import com.fish_dan_.data_energistics.ae2.sanctum.inventory.InterfaceInventoryAccess;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.orientation.RelativeSide;
import appeng.helpers.InterfaceLogicHost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.Set;

public interface DataSanctumLargeInterfaceHost extends InterfaceLogicHost {

    DataSanctumReturnInventory getReturnInventory();

    /** Server-thread capability view: insertion enters the return bar; extraction reads stock or a live AE mapping. */
    default GenericInternalInventory getExternalInventory() {
        return new InterfaceInventoryAccess(getReturnInventory(), getInterfaceLogic().getStorage());
    }

    int getInstalledCapacityCardCount();

    Set<Direction> getActivePullSides();

    void setActivePullSideEnabled(Direction side, boolean enabled);

    default boolean hasActivePullSideSelection() {
        return true;
    }

    @Nullable
    default Direction getSingleActivePullSide() {
        return null;
    }

    default Direction getDefaultActivePullSide() {
        Direction side = getSingleActivePullSide();
        return side != null ? side : mapRelativeSide(RelativeSide.FRONT);
    }

    @Nullable
    Level getInterfaceLevel();

    BlockPos getInterfaceBlockPos();

    Direction mapRelativeSide(RelativeSide relativeSide);

    ItemStack getMainMenuIcon();

    default int getUnlockedPageCount() {
        return Math.min(
                DataSanctumInterfaceConstants.PAGE_COUNT,
                DataSanctumInterfaceConstants.BASE_PAGE_COUNT + getInstalledCapacityCardCount() * DataSanctumInterfaceConstants.PAGES_PER_CAPACITY_CARD);
    }
}
