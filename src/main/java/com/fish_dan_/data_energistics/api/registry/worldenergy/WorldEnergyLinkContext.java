package com.fish_dan_.data_energistics.api.registry.worldenergy;

import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;

/** Read-only view of native links associated with a Digital Supply Interface target. */
public interface WorldEnergyLinkContext {

    /** Returns an immutable snapshot of connector and native links in registration order. */
    ObjectList<ConnectorLink> links();

    /** Returns whether the linked dimension and position are currently available to an adapter. */
    boolean isOnline(ConnectorLink link);

    /** Removes links that no longer resolve and publishes the resulting block state. */
    int removeOfflineLinks();

    /** Returns the dimension currently hosting the interface. */
    ResourceLocation dimensionId();
}
