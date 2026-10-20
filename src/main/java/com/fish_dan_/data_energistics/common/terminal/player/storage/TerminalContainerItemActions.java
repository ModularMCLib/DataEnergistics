package com.fish_dan_.data_energistics.common.terminal.player.storage;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageHelper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.NullMarked;

/** Container filling and emptying use the actual cursor, including registered non-fluid resource types. */
@NullMarked
final class TerminalContainerItemActions {

    private static final int MAX_TRANSFER_ITERATIONS = 256;

    private TerminalContainerItemActions() {}

    static void fill(ServerPlayer player, TerminalPlayerState state, TerminalConnection connection, AEKey key, boolean moveToPlayer, boolean all) {
        var menu = player.containerMenu;
        boolean borrowedBucket = false;
        if (menu.getCarried().isEmpty() && key instanceof AEFluidKey fluid && fluid.getFluid().getBucket() != Items.AIR) {
            long extracted = connection.storage().extract(AEItemKey.of(Items.BUCKET), 1, Actionable.MODULATE, connection.source());
            TerminalNetworkStorage.checkAmount(extracted, 1);
            if (extracted > 0) {
                menu.setCarried(Items.BUCKET.getDefaultInstance());
                borrowedBucket = true;
            }
        }

        var previousItem = menu.getCarried().getItem();
        var context = ContainerItemStrategies.findCarriedContextForKey(key, player, menu);
        boolean filled = false;
        if (context != null) {
            long amount = all ? Long.MAX_VALUE : key.getAmountPerUnit();
            for (int iteration = 0; iteration < (all ? MAX_TRANSFER_ITERATIONS : 1); iteration++) {
                long available = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, amount, connection.source(), Actionable.SIMULATE);
                TerminalNetworkStorage.checkAmount(available, amount);
                if (available == 0) break;
                long allowed = context.insert(key, available, Actionable.SIMULATE);
                TerminalNetworkStorage.checkAmount(allowed, available);
                if (allowed == 0) break;
                long extracted = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, allowed, connection.source());
                TerminalNetworkStorage.checkAmount(extracted, allowed);
                if (extracted == 0) break;
                state.returns().extracted(connection.frequency(), key, extracted);
                long inserted = context.insert(key, extracted, Actionable.MODULATE);
                TerminalNetworkStorage.checkAmount(inserted, extracted);
                state.returns().delivered(connection.frequency(), key, inserted);
                filled |= inserted > 0;
                if (inserted < extracted) {
                    state.returns().refund(connection);
                    break;
                }
            }
            if (filled) context.playFillSound(player, key);
        }

        if (borrowedBucket && menu.getCarried().is(Items.BUCKET)) {
            var carried = menu.getCarried();
            long inserted = connection.storage().insert(AEItemKey.of(Items.BUCKET), carried.getCount(), Actionable.MODULATE, connection.source());
            TerminalNetworkStorage.checkAmount(inserted, carried.getCount());
            menu.setCarried(carried.copyWithCount(carried.getCount() - (int) inserted));
        }
        if (moveToPlayer && !menu.getCarried().is(previousItem) && player.addItem(menu.getCarried()))
            menu.setCarried(ItemStack.EMPTY);
    }

    static void empty(ServerPlayer player, TerminalPlayerState state, TerminalConnection connection, boolean all) {
        var context = ContainerItemStrategies.findCarriedContext(null, player, player.containerMenu);
        if (context == null) return;
        var content = context.getExtractableContent();
        if (content == null || content.amount() == 0) return;
        AEKey key = content.what();
        long amount = all ? Long.MAX_VALUE : key.getAmountPerUnit();
        boolean emptied = false;
        for (int iteration = 0; iteration < (all ? MAX_TRANSFER_ITERATIONS : 1); iteration++) {
            long available = context.extract(key, amount, Actionable.SIMULATE);
            TerminalNetworkStorage.checkAmount(available, amount);
            if (available == 0) break;
            long allowed = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), key, available, connection.source(), Actionable.SIMULATE);
            TerminalNetworkStorage.checkAmount(allowed, available);
            if (allowed == 0) break;
            long extracted = context.extract(key, allowed, Actionable.MODULATE);
            TerminalNetworkStorage.checkAmount(extracted, allowed);
            if (extracted == 0) break;
            state.returns().extracted(connection.frequency(), key, extracted);
            long inserted = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), key, extracted, connection.source());
            TerminalNetworkStorage.checkAmount(inserted, extracted);
            state.returns().delivered(connection.frequency(), key, inserted);
            emptied |= inserted > 0;
            long remainder = extracted - inserted;
            if (remainder > 0) {
                long restored = context.insert(key, remainder, Actionable.MODULATE);
                TerminalNetworkStorage.checkAmount(restored, remainder);
                state.returns().delivered(connection.frequency(), key, restored);
                break;
            }
        }
        if (emptied) context.playEmptySound(player, key);
    }
}
