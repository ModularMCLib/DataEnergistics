package com.fish_dan_.data_energistics.api.registry.connector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Common editing surface for a provider or interface's remote links. The connector uses this contract so keyboard,
 * clipboard and rendering operations share the same target identities. Access stays on the host's level thread;
 * mutations persist the host and request its normal client update. Returned lists are immutable snapshots.
 */
@NullMarked
public interface ConnectorEndpoint {

    /**
     * Returns links in registration order, including face, mode and optional interface slot.
     *
     * @deprecated scheduled for removal in plan 340; use {@link #bindingsFast()}
     */
    @Deprecated(forRemoval = true)
    default List<ConnectorLink> bindings() {
        return List.of();
    }

    /** Returns the configured links through the FastUtil collection API. */
    @SuppressWarnings("unchecked")
    default ObjectList<ConnectorLink> bindingsFast() {
        List<ConnectorLink> legacy = bindings();
        return legacy instanceof ObjectList<?> fast ? (ObjectList<ConnectorLink>) fast : ObjectLists.unmodifiable(new ObjectArrayList<>(legacy));
    }

    /** Returns the default mode for the next link; existing links retain their own modes. */
    ConnectorMode mode();

    /** Changes the default mode on the server thread and synchronizes it to the client. */
    void setMode(ConnectorMode mode);

    /** Number of currently unlocked interface stock slots; zero for a provider without slot bindings. */
    int slotCount();

    /**
     * Toggles one identity on the server thread; returns true when added and false when removed. The mode is captured
     * only when adding. Use slot -1 for providers or an unlocked interface slot; invalid interface slots are rejected.
     */
    boolean toggle(BlockPos position, Direction side, int slot);

    /**
     * Replaces all links on the server thread after caller validation and returns the number retained. Absolute targets
     * and first registration order are preserved. Interface slots must remain in the valid logical range, but may be
     * locked after a capacity card was removed. Invalid slots throw IllegalArgumentException.
     *
     * @deprecated scheduled for removal in plan 340; use {@link #replaceFast(ObjectList)}
     */
    @Deprecated(forRemoval = true)
    default int replace(List<ConnectorLink> bindings) {
        return 0;
    }

    /** Replaces links through the FastUtil collection API. */
    default int replaceFast(ObjectList<ConnectorLink> bindings) {
        return replace((List<ConnectorLink>) bindings);
    }
}
