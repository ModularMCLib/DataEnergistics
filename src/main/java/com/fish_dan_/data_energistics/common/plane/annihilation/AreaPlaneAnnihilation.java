package com.fish_dan_.data_energistics.common.plane.annihilation;

import com.fish_dan_.data_energistics.common.blockentity.plane.AreaPlaneBlockEntity;
import com.fish_dan_.data_energistics.common.plane.configuration.PlaneConfiguration;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;

import appeng.api.behaviors.PickupStrategy;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.parts.automation.FluidPickupStrategy;
import appeng.parts.automation.ItemPickupStrategy;
import appeng.util.Platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class AreaPlaneAnnihilation {

    private static final int SKY_DUST_INTERVAL = 200;
    private final AreaPlaneBlockEntity host;
    private final Int2ObjectMap<PlaneBlockHarvest> harvests = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<FluidPickupStrategy> fluidHarvests = new Int2ObjectOpenHashMap<>();
    private @Nullable Direction facing;
    private @Nullable UUID owner;
    private int generationTicks;

    public AreaPlaneAnnihilation(AreaPlaneBlockEntity host) {
        this.host = host;
    }

    public void tick(ServerLevel level, PlaneLayout layout, PlaneConfiguration configuration,
                     IGrid grid, IActionSource source, @Nullable UUID owner, int elapsedTicks) {
        if (facing != layout.facing() || !Objects.equals(this.owner, owner)) {
            harvests.clear();
            fluidHarvests.clear();
            facing = layout.facing();
            this.owner = owner;
        }
        // Like AE2's plane, the topmost upward-facing device generates dust instead of harvesting.
        if (AEConfig.instance().isAnnihilationPlaneSkyDustGenerationEnabled() && layout.facing() == Direction.UP &&
                host.getBlockPos().getY() + 1 >= level.getMaxBuildHeight()) {
            generateSkyDust(configuration, grid, source, elapsedTicks);
            return;
        }
        generationTicks = 0;
        // Every configured filter applies to every cell in the working volume.
        for (int slot = 0; slot < layout.variant().getWorkingCellCount(); slot++) {
            BlockPos target = layout.targetPosition(host.getBlockPos(), slot);
            if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target) ||
                    !level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4))
                continue;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(target))) {
                // An entity overlapping a neighboring cell belongs only to the cell containing its center.
                if (item.isAlive() && BlockPos.containing(item.position()).equals(target) && configuration.matches(item.getItem()) &&
                        !ItemPickupStrategy.isItemBlacklisted(item.getItem().getItem()))
                    PlaneBlockHarvest.recover(item, grid, source);
            }
            var fluid = level.getFluidState(target);
            if (fluid.isSource() && configuration.matches(AEFluidKey.of(fluid.getType())) &&
                    level.mayInteract(Platform.getFakePlayer(level, owner), target)) {
                FluidPickupStrategy pickup = fluidHarvests.computeIfAbsent(slot, ignored -> new FluidPickupStrategy(
                        level, target, layout.facing().getOpposite(), host, ItemEnchantments.EMPTY, owner));
                // The sink also checks the actual fluid returned by modded bucket-pickup behaviour.
                var result = pickup.tryPickup(grid.getEnergyService(), (key, amount, mode) -> configuration.matches(key) ?
                        StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(), key, amount, source, mode) : 0);
                if (result != PickupStrategy.Result.CANT_PICKUP) continue;
            }
            PlaneBlockHarvest harvest = harvests.computeIfAbsent(slot, ignored -> new PlaneBlockHarvest(
                    level, target, layout.facing().getOpposite(), host, owner));
            harvest.harvest(configuration, grid, source);
        }
    }

    private void generateSkyDust(PlaneConfiguration configuration, IGrid grid, IActionSource source, int elapsedTicks) {
        generationTicks += elapsedTicks;
        if (generationTicks < SKY_DUST_INTERVAL) return;
        int amount = generationTicks / SKY_DUST_INTERVAL;
        generationTicks %= SKY_DUST_INTERVAL;
        AEItemKey dust = AEItemKey.of(AEItems.SKY_DUST);
        if (configuration.matches(dust)) {
            StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(), dust, amount, source);
        }
    }
}
