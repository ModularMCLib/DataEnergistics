package com.fish_dan_.data_energistics.common.plane.annihilation;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.parts.automation.ItemPickupStrategy;
import appeng.util.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Reuses AE2's tool, loot and power rules without its unfiltered nearby-entity sweep. */
final class PlaneBlockHarvest extends ItemPickupStrategy {

    private final ServerLevel level;
    private final BlockPos target;
    private final @Nullable UUID owner;

    PlaneBlockHarvest(ServerLevel level, BlockPos target, Direction side, BlockEntity host, @Nullable UUID owner) {
        super(level, target, side, host, ItemEnchantments.EMPTY, owner);
        this.level = level;
        this.target = target;
        this.owner = owner;
    }

    void harvest(ObjectCollection<AEItemKey> filters, IGrid grid, IActionSource source) {
        BlockState state = level.getBlockState(target);
        if (state.isAir() || state.getDestroySpeed(level, target) < 0 || isBlockBlacklisted(state.getBlock())) return;
        boolean matches = false;
        for (AEItemKey filter : filters) {
            if (filter.getItem() instanceof BlockItem blockItem && state.is(blockItem.getBlock())) {
                matches = true;
                break;
            }
        }
        if (!matches) return;
        var player = Platform.getFakePlayer(level, owner);
        if (!level.mayInteract(player, target)) return;
        var drops = obtainBlockDrops(level, target);
        float breakingPower = calculateEnergyUsage(level, target, drops);
        long transferPower = 0;
        var totals = new Object2LongOpenHashMap<AEItemKey>();
        for (ItemStack drop : drops) {
            totals.addTo(AEItemKey.of(drop), drop.getCount());
            transferPower += drop.getCount();
        }
        double requiredPower = breakingPower + transferPower;
        if (grid.getEnergyService().extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG) < requiredPower - 0.01) return;
        var inventory = grid.getStorageService().getInventory();
        for (var entry : totals.object2LongEntrySet()) {
            if (inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, source) < entry.getLongValue()) return;
        }
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, target, state, player)).isCanceled()) return;
        if (level.getBlockState(target) != state) return;
        AABB capture = new AABB(target).inflate(0.2);
        var existingEntities = new IntOpenHashSet();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, capture)) existingEntities.add(item.getId());
        if (!level.destroyBlock(target, false, player)) return;
        grid.getEnergyService().extractAEPower(breakingPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
        for (ItemStack drop : drops) {
            int inserted = insert(drop, grid, source);
            if (inserted < drop.getCount()) {
                drop.shrink(inserted);
                ItemEntity remainder = new ItemEntity(level, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, drop);
                level.addFreshEntity(remainder);
                existingEntities.add(remainder.getId());
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, capture)) {
            if (!existingEntities.contains(item.getId())) recover(item, grid, source);
        }
    }

    static void recover(ItemEntity item, IGrid grid, IActionSource source) {
        int inserted = insert(item.getItem(), grid, source);
        if (inserted == 0) return;
        item.getItem().shrink(inserted);
        if (item.getItem().isEmpty()) item.discard();
    }

    private static int insert(ItemStack stack, IGrid grid, IActionSource source) {
        return (int) StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
                AEItemKey.of(stack), stack.getCount(), source);
    }
}
