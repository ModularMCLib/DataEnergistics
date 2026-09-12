package com.fish_dan_.data_energistics.api.registry.adaptive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

/** One persistent target link owned by an adaptive provider. */
public record AdaptiveProviderConnectorBinding(BlockPos position, Direction side) {

    public AdaptiveProviderConnectorBinding {
        Objects.requireNonNull(position, "Connector binding position");
        Objects.requireNonNull(side, "Connector binding side");
    }
}
