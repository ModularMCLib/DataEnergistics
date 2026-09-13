package com.fish_dan_.data_energistics.api.registry.adaptive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

/** One persistent target link owned by an adaptive provider, including its independent transfer direction. */
public record AdaptiveProviderConnectorBinding(BlockPos position, Direction side, AdaptiveProviderConnectorMode mode) {

    /** Creates a legacy input link for integrations that do not persist a direction yet. */
    public AdaptiveProviderConnectorBinding(BlockPos position, Direction side) {
        this(position, side, AdaptiveProviderConnectorMode.INPUT);
    }

    public AdaptiveProviderConnectorBinding {
        Objects.requireNonNull(position, "Connector binding position");
        Objects.requireNonNull(side, "Connector binding side");
        Objects.requireNonNull(mode, "Connector binding mode");
    }
}
