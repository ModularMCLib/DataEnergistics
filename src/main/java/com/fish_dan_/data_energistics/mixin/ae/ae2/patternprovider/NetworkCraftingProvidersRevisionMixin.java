package com.fish_dan_.data_energistics.mixin.ae.ae2.patternprovider;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture.TrinityCraftingProviderRevision;

import appeng.me.service.helpers.NetworkCraftingProviders;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Supplies the settled planning model revision, independently of dispatch registration identities.
 */
@Mixin(NetworkCraftingProviders.class)
public abstract class NetworkCraftingProvidersRevisionMixin implements TrinityCraftingProviderRevision {

    @Override
    public long data_energistics$trinityCraftingProviderRevision() {
        return ((CraftingProviderPublicationIndex) this).planningRevision();
    }
}
