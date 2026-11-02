package com.fish_dan_.data_energistics.common.plane.annihilation;

import com.fish_dan_.data_energistics.common.blockentity.plane.AreaPlaneBlockEntity;
import com.fish_dan_.data_energistics.common.plane.configuration.PlaneConfiguration;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.parts.automation.ItemPickupStrategy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class AreaPlaneAnnihilation {

    private final AreaPlaneBlockEntity host;
    private final Int2ObjectMap<PlaneBlockHarvest> harvests = new Int2ObjectOpenHashMap<>();
    private @Nullable Direction facing;
    private @Nullable UUID owner;

    public AreaPlaneAnnihilation(AreaPlaneBlockEntity host) {
        this.host = host;
    }

    public void tick(ServerLevel level, PlaneLayout layout, PlaneConfiguration configuration,
                     IGrid grid, IActionSource source, @Nullable UUID owner) {
        if (facing != layout.facing() || !Objects.equals(this.owner, owner)) {
            harvests.clear();
            facing = layout.facing();
            this.owner = owner;
        }
        for (var entry : configuration.filters().int2ObjectEntrySet()) {
            int slot = entry.getIntKey();
            var filter = entry.getValue();
            BlockPos target = layout.targetPosition(host.getBlockPos(), slot);
            if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target) ||
                    !level.getChunkSource().hasChunk(target.getX() >> 4, target.getZ() >> 4))
                continue;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(target))) {
                // An entity overlapping a neighboring cell belongs only to the cell containing its center.
                if (item.isAlive() && BlockPos.containing(item.position()).equals(target) && filter.matches(item.getItem()) &&
                        !ItemPickupStrategy.isItemBlacklisted(item.getItem().getItem()))
                    PlaneBlockHarvest.recover(item, grid, source);
            }
            PlaneBlockHarvest harvest = harvests.computeIfAbsent(slot, ignored -> new PlaneBlockHarvest(
                    level, target, layout.facing().getOpposite(), host, owner));
            harvest.harvest(filter, grid, source);
        }
    }
}
