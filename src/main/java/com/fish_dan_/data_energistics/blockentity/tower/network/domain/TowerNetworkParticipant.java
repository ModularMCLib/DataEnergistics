package com.fish_dan_.data_energistics.blockentity.tower.network.domain;

import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerBinding;
import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerRuntimeKey;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.TowerEnergyLocation;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Typed tower-facing contract consumed by the grid-level network domain.
 */
public interface TowerNetworkParticipant {

    /** @return stable tower identity */
    TowerRuntimeKey towerKey();

    /** @return currently loaded tower level */
    ServerLevel towerLevel();

    /** @return physical primary grid containing the tower node */
    IGrid towerGrid();

    /** @return whether the physical tower node is active */
    boolean isTowerNetworkActive();

    /** @return whether current tower mode exposes AE targets */
    boolean towerAllowsAe();

    /** @return whether current tower mode exposes FE targets */
    boolean towerAllowsFe();

    /** @return persisted manual and automatic bindings */
    List<TowerBinding> towerBindings();

    /**
     * Returns the loaded logical tower cluster that should be reconciled by one deterministic primary grid.
     *
     * <p>
     * A peer link is a logical membership edge. It must not be represented as a virtual AE grid target because
     * reciprocal peer links would create a bridge cycle. The cluster snapshot lets the selected primary domain carry
     * the ordinary bindings and energy locations of every member while the physical grids remain independent.
     * </p>
     *
     * @return immutable loaded cluster participants, including this participant
     */
    default List<? extends TowerNetworkParticipant> towerNetworkCluster() {
        return List.of(this);
    }

    /**
     * Returns the physical tower node that must stay active when this peer grid is attached to a cluster primary.
     *
     * @return tower node, or {@code null} while the grid is not ready
     */
    @Nullable
    default IGridNode towerNetworkNode() {
        return null;
    }

    /** @return loaded FE candidate locations discovered by this tower */
    List<TowerEnergyLocation> towerEnergyLocations();

    /** @return AE host used as the Applied Flux action source */
    AENetworkedBlockEntity towerEnergyHost();

    /** @return currently quarantined FE retained by this tower */
    long towerQuarantinedEnergy();

    /**
     * Replaces the tower's quarantined FE after a failed domain compensation.
     *
     * @param amount non-negative quarantined FE
     */
    void setTowerQuarantinedEnergy(long amount);

    /**
     * Publishes the latest immutable domain result for UI and per-device actions.
     *
     * @param snapshot latest tower-specific result
     */
    void applyTowerNetworkSnapshot(TowerNetworkTowerSnapshot snapshot);
}
