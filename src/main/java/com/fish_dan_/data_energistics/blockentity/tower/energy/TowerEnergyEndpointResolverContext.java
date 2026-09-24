package com.fish_dan_.data_energistics.blockentity.tower.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Supplies tower-specific state required by {@link CachedTowerEnergyEndpointResolver}.
 *
 * <p>
 * The block entity keeps ownership of persistence, UI state, and AE link graph mutation.
 */
public interface TowerEnergyEndpointResolverContext {

    /**
     * Returns the level containing the owning tower.
     *
     * @return level, or null before the block entity is attached
     */
    @Nullable
    Level level();

    /**
     * Returns cached FE-capable target positions.
     *
     * @return target positions already accepted by tower discovery
     */
    List<BlockPos> cachedEndpointPositions();

    /**
     * Checks whether FE interaction with a target is allowed.
     *
     * @param pos target position
     * @return true when FE transfer may use the target
     */
    boolean targetAllowsFe(BlockPos pos);

    /**
     * Checks whether a target should be reserved for AE grid display/linking rather than receive-side FE transfer.
     *
     * @param pos target position
     * @return true when receive endpoint resolution should skip the target
     */
    boolean isDedicatedAeGridTarget(BlockPos pos);

    /**
     * Checks whether the position belongs to a Data Distribution Tower block.
     *
     * @param pos target position
     * @return true when the target is a tower block
     */
    boolean isTowerBlock(BlockPos pos);
}
