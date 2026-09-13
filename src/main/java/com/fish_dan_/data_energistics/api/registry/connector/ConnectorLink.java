package com.fish_dan_.data_energistics.api.registry.connector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** A persistent absolute target and capability face, with an independent transfer mode and optional interface slot. */
public record ConnectorLink(BlockPos position, Direction side, ConnectorMode mode, int slot) {

    /** Provider links do not select a stock slot. Retains the existing integration constructor. */
    public ConnectorLink(BlockPos position, Direction side, ConnectorMode mode) {
        this(position, side, mode, -1);
    }

    /** Creates a legacy input link for integrations that do not persist a direction yet. */
    public ConnectorLink(BlockPos position, Direction side) {
        this(position, side, ConnectorMode.INPUT);
    }

    public ConnectorLink {
        position = position.immutable();
        if (slot < -1) {
            throw new IllegalArgumentException("Connector slot must be -1 or a stock slot index");
        }
    }
}
