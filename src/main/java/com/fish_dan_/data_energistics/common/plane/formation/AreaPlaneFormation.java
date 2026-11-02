package com.fish_dan_.data_energistics.common.plane.formation;

import com.fish_dan_.data_energistics.common.blockentity.plane.AreaPlaneBlockEntity;
import com.fish_dan_.data_energistics.common.plane.configuration.PlaneConfiguration;
import com.fish_dan_.data_energistics.common.plane.configuration.PlanePlacementMode;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;

import appeng.api.behaviors.PlacementStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.core.AEConfig;
import appeng.parts.automation.StackWorldBehaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** One extracted item stays owned by this operation until placement succeeds or it is refunded. */
public final class AreaPlaneFormation {

    private final AreaPlaneBlockEntity host;
    private final Int2ObjectMap<PlacementStrategy> placements = new Int2ObjectOpenHashMap<>();
    private @Nullable Direction facing;
    private @Nullable UUID owner;
    private ItemStack pending = ItemStack.EMPTY;

    public AreaPlaneFormation(AreaPlaneBlockEntity host) {
        this.host = host;
    }

    public void tick(ServerLevel level, PlaneLayout layout, PlaneConfiguration configuration,
                     IGrid grid, IActionSource source, @Nullable UUID owner) {
        if (!refund(grid, source)) return;
        if (facing != layout.facing() || !Objects.equals(this.owner, owner)) {
            placements.clear();
            facing = layout.facing();
            this.owner = owner;
        }
        int entityBudget = -1;
        for (var entry : configuration.filters().int2ObjectEntrySet()) {
            int slot = entry.getIntKey();
            AEItemKey key = entry.getValue();
            BlockPos target = layout.targetPosition(host.getBlockPos(), slot);
            if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target) ||
                    !level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4))
                continue;
            boolean asItem = configuration.mode(slot) == PlanePlacementMode.ITEM;
            if (asItem) {
                if (entityBudget < 0) {
                    entityBudget = Math.max(0, AEConfig.instance().getFormationPlaneEntityLimit() -
                            level.getEntitiesOfClass(Entity.class, new AABB(host.getBlockPos()).inflate(8 + layout.variant().getWidth() / 2)).size());
                }
                if (entityBudget == 0 || !level.getBlockState(target).canBeReplaced()) continue;
            } else {
                if (!(key.getItem() instanceof BlockItem)) continue;
                PlacementStrategy strategy = placements.computeIfAbsent(slot, ignored -> StackWorldBehaviors.createPlacementStrategies(
                        level, target, layout.facing().getOpposite(), host, owner));
                // Changes at the outer cells do not necessarily notify the center block.
                strategy.clearBlocked();
                if (strategy.placeInWorld(key, 1, Actionable.SIMULATE, false) == 0) continue;
            }
            long extracted = StorageHelper.poweredExtraction(grid.getEnergyService(), grid.getStorageService().getInventory(), key, 1, source);
            if (extracted == 0) continue;
            pending = key.toStack(1);
            host.saveChanges();
            boolean placed;
            if (asItem) {
                Direction normal = layout.facing();
                ItemEntity item = new ItemEntity(level, target.getX() + 0.5 - normal.getStepX() * 0.25,
                        target.getY() + 0.375 - normal.getStepY() * 0.25, target.getZ() + 0.5 - normal.getStepZ() * 0.25, pending.copy());
                item.setDeltaMovement(normal.getStepX() * 0.1, normal.getStepY() * 0.1, normal.getStepZ() * 0.1);
                placed = level.addFreshEntity(item);
                if (placed) entityBudget--;
            } else {
                placed = placements.get(slot).placeInWorld(key, 1, Actionable.MODULATE, false) == 1;
            }
            if (placed) {
                pending = ItemStack.EMPTY;
                host.saveChanges();
            } else if (!refund(grid, source)) {
                return;
            }
        }
    }

    private boolean refund(IGrid grid, IActionSource source) {
        if (pending.isEmpty()) return true;
        long inserted = grid.getStorageService().getInventory().insert(AEItemKey.of(pending), pending.getCount(), Actionable.MODULATE, source);
        if (inserted > 0) {
            pending.shrink((int) inserted);
            host.saveChanges();
        }
        return pending.isEmpty();
    }

    public void restorePendingItem(ItemStack stack) {
        pending = stack.copy();
    }

    public ItemStack copyPendingItem() {
        return pending.copy();
    }

    public void drainPendingItem(Consumer<ItemStack> drop) {
        if (!pending.isEmpty()) drop.accept(pending.copy());
        pending = ItemStack.EMPTY;
    }
}
