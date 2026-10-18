package com.fish_dan_.data_energistics.common.terminal.player.session;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import org.jspecify.annotations.Nullable;

/**
 * Magnet attraction delegates to vanilla pickup so other mods retain their pickup veto and ownership rules.
 */
final class TerminalMagnetTicker {

    private TerminalMagnetTicker() {}

    static void tick(ServerPlayer player, TerminalPlayerState state, @Nullable TerminalConnection connection) {
        if (player.isShiftKeyDown() || player.isSpectator() || !player.isAlive()) return;
        if (state.magnet().mode().attract() && (!state.magnet().mode().insert() || connection != null) && player.tickCount % 5 == 0) {
            for (ItemEntity entity : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16), ItemEntity::isAlive)) {
                if (entity.hasPickUpDelay() || entity.getTarget() != null && !entity.getTarget().equals(player.getUUID()) || entity.getPersistentData().contains("PreventRemoteMovement"))
                    continue;
                AEItemKey key = AEItemKey.of(entity.getItem());
                if (key != null && state.magnet().pickupAllows(key)) entity.playerTouch(player);
            }
        }
        if (state.magnet().restock() && connection != null && player.tickCount % 10 == 0) {
            for (int index = 0; index < player.getInventory().items.size(); index++) {
                ItemStack stack = player.getInventory().getItem(index);
                AEItemKey key = AEItemKey.of(stack);
                if (key == null || !state.magnet().insertAllows(key) || stack.getCount() >= stack.getMaxStackSize())
                    continue;
                int wanted = stack.getMaxStackSize() - stack.getCount();
                long count = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(), key, wanted, connection.source());
                TerminalNetworkStorage.checkAmount(count, wanted);
                stack.grow((int) count);
                if (count > 0) player.getInventory().setChanged();
            }
        }
    }

    static void pickup(ItemEntityPickupEvent.Pre event, ServerPlayer player, TerminalPlayerRuntime runtime) {
        if (player.isShiftKeyDown() || event.canPickup() == TriState.FALSE || player.isSpectator())
            return;
        var state = runtime.state();
        if (!state.magnet().mode().insert()) return;
        ItemEntity entity = event.getItemEntity();
        if (entity.hasPickUpDelay() || entity.getTarget() != null && !entity.getTarget().equals(player.getUUID()) || entity.getPersistentData().contains("PreventRemoteMovement"))
            return;
        ItemStack stack = entity.getItem();
        AEItemKey key = AEItemKey.of(stack);
        if (key == null || !state.magnet().pickupAllows(key) || !state.magnet().insertAllows(key)) return;
        TerminalConnection connection = runtime.connection();
        if (connection == null) return;
        int original = stack.getCount();
        Item item = stack.getItem();
        long accepted = StorageHelper.poweredInsert(connection.grid().getEnergyService(), connection.storage(), key, original, connection.source());
        TerminalNetworkStorage.checkAmount(accepted, original);
        if (accepted > 0) {
            stack.shrink((int) accepted);
            player.take(entity, (int) accepted);
            player.awardStat(Stats.ITEM_PICKED_UP.get(item), (int) accepted);
            player.onItemPickup(entity);
        }
        if (stack.isEmpty()) {
            entity.discard();
        }
        // Network modes leave any amount the network could not accept in the item entity.
        event.setCanPickup(TriState.FALSE);
    }
}
