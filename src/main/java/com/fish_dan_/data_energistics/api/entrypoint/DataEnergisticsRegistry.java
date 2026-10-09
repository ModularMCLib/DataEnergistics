package com.fish_dan_.data_energistics.api.entrypoint;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.AeKeyTypeRegistry;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceRegistry;
import com.fish_dan_.data_energistics.api.registry.dynamic.DynamicCraftingOutputRegistry;
import com.fish_dan_.data_energistics.api.registry.machine.CraftingMachineRegistry;
import com.fish_dan_.data_energistics.api.registry.matching.RecipeMatchingRegistry;
import com.fish_dan_.data_energistics.api.registry.packaged.PackagedCraftingRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdRegistry;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRegistry;
import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchRegistry;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;
import com.fish_dan_.data_energistics.api.registry.tower.energy.TowerEnergyIntegrationRegistry;
import com.fish_dan_.data_energistics.api.registry.virtual.VirtualCraftingRegistry;

/**
 * Root registration-stage surface passed to a Data Energistics plugin.
 *
 * <p>
 * All facets refer to the same staging transaction. A plugin can therefore register terminals, provider
 * integrations and crafting-output adapters from one entrypoint without coordinating multiple annotations.
 * </p>
 */
public interface DataEnergisticsRegistry {

    /** Returns the transaction-local AE2 key-type registration facet. */
    AeKeyTypeRegistry aeKeyTypes();

    /** Returns the transaction-local Digital Supply Interface adapter facet. */
    DigitalSupplyInterfaceRegistry digitalSupplyInterfaces();

    /** Returns transaction-local global recipe matching rules during common setup. */
    RecipeMatchingRegistry recipeMatching();

    /** Registers physical packaged machine automation independently of global matching rules. */
    PackagedCraftingRegistry packagedCrafting();

    /** Returns the transaction-local energy adapter registry. */
    TowerEnergyIntegrationRegistry towerEnergyIntegrations();

    /**
     * @return universal-terminal declaration facet
     */
    UniversalTerminalRegistry universalTerminals();

    /**
     * @return pattern-provider lifecycle declaration facet
     */
    PatternProviderRegistry patternProviders();

    /**
     * Returns the complete external crafting-machine declaration facet.
     *
     * <p>
     * Capacity and pattern-upload declarations share the current plugin staging transaction. Register them during
     * the plugin callback; do not retain this facet after registration completes.
     * </p>
     */
    CraftingMachineRegistry craftingMachines();

    /**
     * @return adaptive pattern-provider definition facet
     */
    AdaptivePatternProviderRegistry adaptivePatternProviders();

    /**
     * @return Trinity pattern recipe-ID resolver facet
     */
    TrinityPatternRecipeIdRegistry trinityPatternRecipes();

    /**
     * @return Trinity pattern search contribution facet
     */
    TrinityPatternSearchRegistry trinityPatternSearch();

    /**
     * @return virtual crafting output declaration facet
     */
    VirtualCraftingRegistry virtualCrafting();

    /**
     * @return dynamic physical crafting-output declaration facet
     */
    DynamicCraftingOutputRegistry dynamicCraftingOutputs();

    /** Returns the transaction-local reusable-input registry. */
    ReusableInputRegistry reusableInputs();
}
