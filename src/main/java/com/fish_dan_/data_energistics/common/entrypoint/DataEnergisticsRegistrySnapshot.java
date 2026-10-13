package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.crafting.matching.RecipeMatchingRuleAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.AeKeyTypeRegistration;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceRegistration;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferContext;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationRegistration;
import com.fish_dan_.data_energistics.api.registry.production.DataProductionResourceRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderWorkstationSourceRegistration;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdLookup;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolver;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchTermRegistration;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistration;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegration;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedRecipeCatalog;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules.FrozenReusableInputRules;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternRecipeIdResolvers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/**
 * Immutable runtime values published after every discovered plugin has finished registration.
 *
 * <p>
 * The snapshot deliberately does not implement the registration-stage registrar. Runtime consumers receive copied
 * values and read-only resolver interfaces, so late mutation cannot be expressed instead of being represented by
 * frozen implementations whose only behavior is to throw.
 * </p>
 */
public final class DataEnergisticsRegistrySnapshot {

    private final PackagedRecipeCatalog packagedCrafting;
    private final ObjectList<RecipeMatchingRuleAdapter> recipeMatching;
    private final ObjectList<UniversalTerminalRegistration> universalTerminalRegistrations;
    private final ObjectList<PatternProviderRegistration> patternProviderRegistrations;
    private final ObjectList<PatternProviderWorkstationSourceRegistration> patternProviderWorkstationSourceRegistrations;
    private final ObjectList<CraftingMachineCapacityRegistration> craftingMachineCapacityRegistrations;
    private final ObjectList<PatternUploadWorkstationRegistration> patternUploadWorkstationRegistrations;
    private final ObjectList<AdaptivePatternProviderRegistration> adaptivePatternProviderRegistrations;
    private final ObjectList<AeKeyTypeRegistration> aeKeyTypeRegistrations;
    private final DataProductionResourceCatalog dataProductionResources;
    private final ObjectList<DigitalSupplyInterfaceRegistration> digitalSupplyInterfaceRegistrations;
    private final TrinityPatternRecipeIdResolvers trinityPatternRecipes;
    private final ObjectList<TrinityPatternSearchTermRegistration> trinityPatternSearchTermRegistrations;
    private final ObjectList<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters;
    private final ObjectList<DynamicCraftingOutputAdapter> dynamicCraftingOutputAdapters;
    private final ReusableInputRules reusableInputRules;
    private final boolean hasReusableInputRules;
    private final ObjectList<TowerEnergyEndpointIntegration> towerEnergyIntegrations;

    /**
     * Freezes all registration values without retaining a mutable staging collection.
     */
    DataEnergisticsRegistrySnapshot(ObjectCollection<UniversalTerminalRegistration> universalTerminalRegistrations,
                                    ObjectCollection<PatternProviderRegistration> patternProviderRegistrations,
                                    ObjectCollection<PatternProviderWorkstationSourceRegistration> patternProviderWorkstationSourceRegistrations,
                                    ObjectCollection<CraftingMachineCapacityRegistration> craftingMachineCapacityRegistrations,
                                    ObjectCollection<PatternUploadWorkstationRegistration> patternUploadWorkstationRegistrations,
                                    ObjectCollection<AdaptivePatternProviderRegistration> adaptivePatternProviderRegistrations,
                                    ObjectCollection<AeKeyTypeRegistration> aeKeyTypeRegistrations,
                                    ObjectCollection<DataProductionResourceRegistration> dataProductionResources,
                                    ObjectCollection<DigitalSupplyInterfaceRegistration> digitalSupplyInterfaceRegistrations,
                                    Object2ObjectMap<ResourceLocation, TrinityPatternRecipeIdResolver> trinityPatternRecipeIdResolvers,
                                    Object2ObjectMap<ResourceLocation, TrinityPatternSearchTermRegistration> trinityPatternSearchTerms,
                                    ObjectCollection<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters,
                                    Object2ObjectMap<ResourceLocation, DynamicCraftingOutputAdapter> dynamicCraftingOutputAdapters,
                                    Object2ObjectMap<ResourceLocation, ReusableInputRuleAdapter> reusableInputAdapters,
                                    ObjectCollection<TowerEnergyEndpointIntegration> towerEnergyIntegrations,
                                    ObjectList<PackagedMachineAdapter> packagedAdapters,
                                    ObjectCollection<RecipeMatchingRuleAdapter> recipeMatchingAdapters) {
        this.recipeMatching = immutableList(recipeMatchingAdapters);
        this.packagedCrafting = new PackagedRecipeCatalog(packagedAdapters);
        this.universalTerminalRegistrations = immutableList(universalTerminalRegistrations);
        this.patternProviderRegistrations = immutableList(patternProviderRegistrations);
        this.patternProviderWorkstationSourceRegistrations = immutableList(
                patternProviderWorkstationSourceRegistrations);
        this.craftingMachineCapacityRegistrations = immutableList(craftingMachineCapacityRegistrations);
        this.patternUploadWorkstationRegistrations = immutableList(patternUploadWorkstationRegistrations);
        this.adaptivePatternProviderRegistrations = immutableList(adaptivePatternProviderRegistrations);
        this.aeKeyTypeRegistrations = immutableList(aeKeyTypeRegistrations);
        this.dataProductionResources = new DataProductionResourceCatalog(dataProductionResources);
        this.digitalSupplyInterfaceRegistrations = freezeDigitalSupplyInterfaces(digitalSupplyInterfaceRegistrations);
        this.trinityPatternRecipes = new TrinityPatternRecipeIdResolvers(trinityPatternRecipeIdResolvers);
        this.trinityPatternSearchTermRegistrations = immutableList(trinityPatternSearchTerms.values());
        this.virtualCraftingOutputAdapters = immutableList(virtualCraftingOutputAdapters);
        this.dynamicCraftingOutputAdapters = immutableList(dynamicCraftingOutputAdapters.values());
        this.reusableInputRules = new FrozenReusableInputRules(immutableList(reusableInputAdapters.values()));
        this.hasReusableInputRules = !reusableInputAdapters.isEmpty();
        this.towerEnergyIntegrations = immutableList(towerEnergyIntegrations);
    }

    /** Returns only committed energy adapters, frozen after common-setup registration. */
    public ObjectList<TowerEnergyEndpointIntegration> towerEnergyIntegrations() {
        return this.towerEnergyIntegrations;
    }

    /** Returns the immutable resource directory available to extractor and mimetic rules. */
    public DataProductionResourceCatalog dataProductionResources() {
        return this.dataProductionResources;
    }

    private static <T> ObjectList<T> immutableList(ObjectCollection<T> values) {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(values));
    }

    private static ObjectList<DigitalSupplyInterfaceRegistration> freezeDigitalSupplyInterfaces(
                                                                                                ObjectCollection<DigitalSupplyInterfaceRegistration> registrations) {
        ObjectArrayList<DigitalSupplyInterfaceRegistration> frozen = new ObjectArrayList<>(registrations.size());
        for (DigitalSupplyInterfaceRegistration registration : registrations) {
            ObjectList<DigitalSupplyResourceDefinition> resources = registration.resources();
            frozen.add(new DigitalSupplyInterfaceRegistration(
                    registration.id(), resources, new FrozenDigitalSupplyAdapter(registration.adapter(), resources)));
        }
        return ObjectLists.unmodifiable(frozen);
    }

    /** Adapter facade that prevents runtime resource discovery from replacing the frozen registration catalog. */
    private static final class FrozenDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

        private final DigitalSupplyInterfaceAdapter delegate;
        private final ObjectList<DigitalSupplyResourceDefinition> resources;

        private FrozenDigitalSupplyAdapter(DigitalSupplyInterfaceAdapter delegate,
                                           ObjectList<DigitalSupplyResourceDefinition> resources) {
            this.delegate = delegate;
            this.resources = resources;
        }

        @Override
        public ResourceLocation id() {
            return this.delegate.id();
        }

        @Override
        public ObjectList<DigitalSupplyResourceDefinition> resources() {
            return this.resources;
        }

        @Override
        public int priority() {
            return this.delegate.priority();
        }

        @Override
        public boolean supports(DigitalSupplyInterfaceTarget target) {
            return this.delegate.supports(target);
        }

        @Override
        public void discover(DigitalSupplyInterfaceTarget target) {
            this.delegate.discover(target);
        }

        @Override
        public void updateLinks(DigitalSupplyInterfaceTarget target) {
            this.delegate.updateLinks(target);
        }

        @Override
        public void tick(DigitalSupplyInterfaceTarget target, DigitalSupplyTransferContext transfer) {
            this.delegate.tick(target, transfer);
        }

        @Override
        public int acceptItem(DigitalSupplyInterfaceTarget target, ItemStack stack, boolean simulate) {
            return this.delegate.acceptItem(target, stack, simulate);
        }

        @Override
        public int acceptFluid(DigitalSupplyInterfaceTarget target, FluidStack stack, boolean simulate) {
            return this.delegate.acceptFluid(target, stack, simulate);
        }

        @Override
        public void saveState(CompoundTag tag) {
            this.delegate.saveState(tag);
        }

        @Override
        public void loadState(CompoundTag tag) {
            this.delegate.loadState(tag);
        }

        @Override
        public void detach(DigitalSupplyInterfaceTarget target) {
            this.delegate.detach(target);
        }
    }

    /**
     * @return terminals in deterministic plugin and declaration order
     */
    public ObjectList<UniversalTerminalRegistration> universalTerminalRegistrations() {
        return this.universalTerminalRegistrations;
    }

    /**
     * @return provider lifecycle declarations in deterministic plugin and declaration order
     */
    public ObjectList<PatternProviderRegistration> patternProviderRegistrations() {
        return this.patternProviderRegistrations;
    }

    /**
     * @return custom provider workstation sources in deterministic plugin and declaration order
     */
    public ObjectList<PatternProviderWorkstationSourceRegistration> patternProviderWorkstationSourceRegistrations() {
        return this.patternProviderWorkstationSourceRegistrations;
    }

    /**
     * @return machine-capacity declarations in deterministic plugin and declaration order
     */
    public ObjectList<CraftingMachineCapacityRegistration> craftingMachineCapacityRegistrations() {
        return this.craftingMachineCapacityRegistrations;
    }

    /**
     * @return workstation upload declarations in deterministic plugin and declaration order
     */
    public ObjectList<PatternUploadWorkstationRegistration> patternUploadWorkstationRegistrations() {
        return this.patternUploadWorkstationRegistrations;
    }

    /**
     * @return adaptive provider declarations in deterministic plugin and declaration order
     */
    public ObjectList<AdaptivePatternProviderRegistration> adaptivePatternProviderRegistrations() {
        return this.adaptivePatternProviderRegistrations;
    }

    /** Returns dynamic AE2 key types declared by successfully committed plugins. */
    public ObjectList<AeKeyTypeRegistration> aeKeyTypes() {
        return this.aeKeyTypeRegistrations;
    }

    /** Returns digital-supply adapters declared by successfully committed plugins. */
    public ObjectList<DigitalSupplyInterfaceRegistration> digitalSupplyInterfaces() {
        return this.digitalSupplyInterfaceRegistrations;
    }

    /**
     * @return immutable Trinity recipe resolution runtime
     */
    public TrinityPatternRecipeIdLookup trinityPatternRecipes() {
        return this.trinityPatternRecipes;
    }

    /**
     * @return number of frozen Trinity resolver declarations
     */
    public int trinityPatternRecipeResolverCount() {
        return this.trinityPatternRecipes.size();
    }

    /**
     * @return machine-specific search contributors in deterministic plugin and declaration order
     */
    public ObjectList<TrinityPatternSearchTermRegistration> trinityPatternSearchTermRegistrations() {
        return this.trinityPatternSearchTermRegistrations;
    }

    /**
     * @return virtual-output adapters in deterministic plugin and declaration order
     */
    public ObjectList<VirtualCraftingOutputAdapter> virtualCraftingOutputAdapters() {
        return this.virtualCraftingOutputAdapters;
    }

    /**
     * @return dynamic-output adapters in deterministic plugin and declaration order
     */
    public ObjectList<DynamicCraftingOutputAdapter> dynamicCraftingOutputAdapters() {
        return this.dynamicCraftingOutputAdapters;
    }

    /** @return immutable server-thread rule lookup; resolved model values are safe for background planning */
    public ReusableInputRules reusableInputs() {
        return this.reusableInputRules;
    }

    /** @return whether a server capture can benefit from querying reusable input rules */
    public boolean hasReusableInputRules() {
        return this.hasReusableInputRules;
    }

    /** Immutable global matching declarations in deterministic plugin order; not tied to packaged crafting. */
    public ObjectList<RecipeMatchingRuleAdapter> recipeMatching() {
        return this.recipeMatching;
    }

    /** Immutable real-machine adapters declared by successfully committed plugins. */
    public PackagedRecipeCatalog packagedCrafting() {
        return this.packagedCrafting;
    }
}
