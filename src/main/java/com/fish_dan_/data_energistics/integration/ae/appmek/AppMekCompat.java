package com.fish_dan_.data_energistics.integration.ae.appmek;

import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlockEntities;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import mekanism.common.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

public final class AppMekCompat {

    private AppMekCompat() {}

    public static void registerChemicalBlockEntityCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.CHEMICAL.block(),
                DEBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                (blockEntity, side) -> {
                    if (side != null && !blockEntity.getTargets().contains(side)) {
                        return null;
                    }
                    return new AdaptivePatternProviderReturnChemicalHandler(
                            () -> blockEntity.getLogic() instanceof AdaptivePatternProviderLogic logic ? logic : null);
                });
    }

    public static void registerChemicalCableBusCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.CHEMICAL.block(),
                AEBlockEntities.CABLE_BUS.get(),
                (CableBusBlockEntity blockEntity, @Nullable Direction context) -> {
                    if (context == null) {
                        return null;
                    }

                    var part = blockEntity.getPart(context);
                    if (part instanceof AdaptivePatternProviderPart adaptivePart) {
                        return new AdaptivePatternProviderReturnChemicalHandler(adaptivePart::getLogic);
                    }

                    return null;
                });
    }
}
