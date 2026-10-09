package com.fish_dan_.data_energistics.api.registry.worldenergy;

import com.fish_dan_.data_energistics.api.registry.connector.ConnectorEndpoint;

import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/**
 * Public runtime target supplied to adapters by the Digital Supply Interface block entity.
 *
 * <p>
 * Adapters may use this contract on the server thread during a tick. They must not retain the target after the
 * block entity is unloaded and must not cast it to an internal block-entity class.
 * </p>
 */
public interface DigitalSupplyInterfaceTarget {

    Level level();

    BlockPos position();

    @Nullable
    IGridNode gridNode();

    @Nullable
    MEStorage networkStorage();

    ConnectorEndpoint connectorEndpoint();

    WorldEnergyLinkContext links();

    /** Returns the live type-presence keys; callers must not mutate the returned set. */
    ObjectSet<AEKey> presenceKeys();

    /** Adds or removes one type-presence marker without changing real network quantities. */
    void setPresence(AEKey key, boolean present);

    /** Publishes a changed marker/link state to the world and client. */
    void refreshState();
}
