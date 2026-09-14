package com.fish_dan_.data_energistics.item.connector;

import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorEndpoint;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.objects.ObjectList;

/** Keeps the provider's existing persistence and dispatch API behind the shared connector editing surface. */
record AdaptiveProviderConnectorEndpoint(AdaptivePatternProviderLogic logic) implements ConnectorEndpoint {

    @Override
    public ObjectList<ConnectorLink> bindingsFast() {
        return logic.adaptiveConnectorBindings();
    }

    @Override
    public ConnectorMode mode() {
        return logic.connectorMode();
    }

    @Override
    public void setMode(ConnectorMode mode) {
        logic.setConnectorMode(mode);
    }

    @Override
    public int slotCount() {
        return 0;
    }

    @Override
    public boolean toggle(BlockPos position, Direction side, int slot) {
        if (logic.hasConnectorTarget(position, side)) {
            logic.unbindConnectorTarget(position, side);
            return false;
        }
        logic.bindConnectorTarget(position, side);
        return true;
    }

    @Override
    public int replaceFast(ObjectList<ConnectorLink> bindings) {
        return logic.replaceConnectorTargets(bindings);
    }
}
