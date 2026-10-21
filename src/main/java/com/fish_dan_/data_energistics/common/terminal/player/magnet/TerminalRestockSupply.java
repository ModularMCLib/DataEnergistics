package com.fish_dan_.data_energistics.common.terminal.player.magnet;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Pays only the items consumed by an interaction, leaving the physical stack's original quantity intact. */
public final class TerminalRestockSupply {

    private static final TagKey<Item> NO_RESTOCK = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("ae2wtlib", "no_restock"));

    private TerminalRestockSupply() {}

    public static boolean eligible(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() > 1 && !stack.is(NO_RESTOCK);
    }

    /** A transformed remainder belongs to the original item and must never be overwritten or duplicated. */
    public static int consumed(ItemStack previous, ItemStack current) {
        if (!eligible(previous) || !current.isEmpty() && !ItemStack.isSameItemSameComponents(previous, current)) return 0;
        return Math.max(0, previous.getCount() - current.getCount());
    }

    /** Server-thread settlement after an actual use; unavailable network stock leaves vanilla consumption in place. */
    public static ItemStack pay(ItemStack previous, ItemStack current, TerminalConnection connection) {
        int consumed = consumed(previous, current);
        if (consumed == 0) return current;
        AEItemKey key = Objects.requireNonNull(AEItemKey.of(previous));
        long paid = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, consumed, connection.source());
        TerminalNetworkStorage.checkAmount(paid, consumed);
        return paid == 0 ? current : previous.copyWithCount(current.getCount() + (int) paid);
    }
}
