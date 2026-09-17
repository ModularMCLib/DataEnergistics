package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Default AE-network, player-inventory, then world-drop implementation of {@link TrinityRefundDelivery}. */
public final class PlayerInventoryRefundDelivery implements TrinityRefundDelivery {

    private static final BigInteger PHYSICAL_CHUNK = BigInteger.valueOf(Long.MAX_VALUE);
    private static final int MAX_STACK_DELIVERIES = 64;

    private final Player player;
    @Nullable
    private final MEStorage networkStorage;
    @Nullable
    private final IActionSource actionSource;
    private ObjectList<TrinityItemAmount> preparedItems = ObjectList.of();
    private boolean prepared;
    private boolean delivered;

    /**
     * Creates one delivery bound to the player who requested the refund and an optional currently usable AE network.
     *
     * @param player         requesting player and final world-drop location
     * @param networkStorage lease-grid storage, or {@code null} when no AE network is available
     * @param actionSource   lease-grid permission source, or {@code null} with no AE network
     */
    public PlayerInventoryRefundDelivery(Player player,
                                         @Nullable MEStorage networkStorage,
                                         @Nullable IActionSource actionSource) {
        this.player = player;
        this.networkStorage = networkStorage;
        this.actionSource = actionSource;
    }

    @Override
    public boolean prepare(ObjectList<TrinityItemAmount> items) {
        if (this.prepared || this.player.level().isClientSide() || items.isEmpty()) {
            return false;
        }
        this.preparedItems = new ObjectImmutableList<>(items);
        this.prepared = true;
        return true;
    }

    @Override
    public ObjectList<TrinityItemAmount> deliver(ObjectList<TrinityItemAmount> items) {
        if (!this.prepared || this.delivered || !this.preparedItems.equals(items)) {
            throw new IllegalStateException("Trinity refund delivery was not prepared for this aggregate");
        }
        this.delivered = true;
        for (int index = 0; index < items.size(); index++) {
            TrinityItemAmount item = items.get(index);
            BigInteger remaining = deliverItem(item);
            if (remaining.signum() > 0) {
                ObjectArrayList<TrinityItemAmount> undelivered = new ObjectArrayList<>(items.size() - index);
                undelivered.add(item.withAmount(remaining));
                undelivered.addAll(items.subList(index + 1, items.size()));
                return new ObjectImmutableList<>(undelivered);
            }
        }
        return ObjectList.of();
    }

    private BigInteger deliverItem(TrinityItemAmount item) {
        BigInteger remaining = insertIntoNetwork(item);
        if (remaining.signum() > 0) {
            long offered = remaining.min(PHYSICAL_CHUNK).longValueExact();
            long undelivered = insertIntoPlayerInventory(item.key(), offered);
            remaining = remaining.subtract(BigInteger.valueOf(offered - undelivered));
        }
        if (remaining.signum() > 0) {
            long offered = remaining.min(PHYSICAL_CHUNK).longValueExact();
            long undelivered = dropRemainder(item.key(), offered);
            remaining = remaining.subtract(BigInteger.valueOf(offered - undelivered));
        }
        return remaining;
    }

    private BigInteger insertIntoNetwork(TrinityItemAmount item) {
        if (this.networkStorage == null || this.actionSource == null) {
            return item.exactAmount();
        }
        long offered = item.exactAmount().min(PHYSICAL_CHUNK).longValueExact();
        long inserted;
        try {
            inserted = this.networkStorage.insert(
                    item.key(),
                    offered,
                    Actionable.MODULATE,
                    this.actionSource);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to insert Trinity refund item {} into the selected AE network; trying player inventory",
                    item,
                    exception);
            return item.exactAmount();
        }
        if (inserted < 0L || inserted > offered) {
            throw new IllegalStateException("AE storage accepted invalid Trinity refund amount " + inserted +
                    " for offer " + offered);
        }
        return item.exactAmount().subtract(BigInteger.valueOf(inserted));
    }

    private long insertIntoPlayerInventory(AEItemKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        int maximumStackSize = key.toStack(1).getMaxStackSize();
        long remaining = amount;
        int stacks = 0;
        while (remaining > 0L && stacks++ < MAX_STACK_DELIVERIES) {
            int offered = (int) Math.min(remaining, maximumStackSize);
            ItemStack stack = key.toStack(offered);
            try {
                this.player.getInventory().add(stack);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to insert Trinity refund stack {} into player {} inventory; dropping its remainder",
                        stack,
                        this.player.getGameProfile().getName(),
                        exception);
                return remaining;
            }
            long inserted = offered - stack.getCount();
            remaining -= inserted;
            if (!stack.isEmpty()) {
                return remaining;
            }
        }
        return remaining;
    }

    private long dropRemainder(AEItemKey key, long amount) {
        long remaining = amount;
        int stacks = 0;
        while (remaining > 0L && stacks++ < MAX_STACK_DELIVERIES) {
            int count = (int) Math.min(remaining, Integer.MAX_VALUE);
            if (!dropStack(key.toStack(count))) {
                return remaining;
            }
            remaining -= count;
        }
        return remaining;
    }

    private boolean dropStack(ItemStack stack) {
        try {
            ItemEntity itemEntity = new ItemEntity(
                    this.player.level(),
                    this.player.getX(),
                    this.player.getY(),
                    this.player.getZ(),
                    stack.copy());
            if (this.player.level().addFreshEntity(itemEntity)) {
                return true;
            }
            Data_Energistics.LOGGER.error(
                    "World rejected Trinity refund stack {} for player {}",
                    stack,
                    this.player.getGameProfile().getName());
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to create Trinity refund world drop {} for player {}",
                    stack,
                    this.player.getGameProfile().getName(),
                    exception);
        }
        return false;
    }
}
