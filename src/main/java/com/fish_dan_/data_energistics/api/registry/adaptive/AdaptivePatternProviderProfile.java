package com.fish_dan_.data_energistics.api.registry.adaptive;

import appeng.api.stacks.AEItemKey;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Comparator;

/**
 * Immutable presentation and behavior facts for one installed provider stack.
 *
 * @param slotsPerProvider   number of pattern slots contributed by one installed provider
 * @param mainMenuIcon       icon shown by the provider menu
 * @param terminalIcon       icon used by AE terminal rows
 * @param displayName        provider name shown to players
 * @param recipeCategoryIds  recipe-category IDs understood by the provider
 * @param workstationItemIds workstation item IDs understood by the provider
 * @param capabilities       composable behavior identifiers implemented by the provider
 */
public record AdaptivePatternProviderProfile(
                                             int slotsPerProvider,
                                             ItemStack mainMenuIcon,
                                             AEItemKey terminalIcon,
                                             Component displayName,
                                             ObjectList<ResourceLocation> recipeCategoryIds,
                                             ObjectList<ResourceLocation> workstationItemIds,
                                             ObjectSet<ResourceLocation> capabilities) {

    /**
     * Validates profile invariants and detaches mutable values at the public boundary.
     */
    public AdaptivePatternProviderProfile {
        if (slotsPerProvider <= 0) {
            throw new IllegalArgumentException("Adaptive pattern provider slot count must be positive");
        }
        mainMenuIcon = mainMenuIcon.copy();
        if (mainMenuIcon.isEmpty()) {
            throw new IllegalArgumentException("Adaptive pattern provider main-menu icon must not be empty");
        }
        displayName = displayName.copy();
        recipeCategoryIds = canonicalIds(recipeCategoryIds);
        workstationItemIds = canonicalIds(workstationItemIds);
        capabilities = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(capabilities));
    }

    /**
     * Creates a profile without upload matching metadata for source compatibility with older integrations.
     */
    public AdaptivePatternProviderProfile(
                                          int slotsPerProvider,
                                          ItemStack mainMenuIcon,
                                          AEItemKey terminalIcon,
                                          Component displayName,
                                          ObjectSet<ResourceLocation> capabilities) {
        this(slotsPerProvider, mainMenuIcon, terminalIcon, displayName,
                ObjectLists.emptyList(), ObjectLists.emptyList(), capabilities);
    }

    /**
     * Returns a defensive icon copy because {@link ItemStack} is mutable.
     */
    @Override
    public ItemStack mainMenuIcon() {
        return this.mainMenuIcon.copy();
    }

    /**
     * Returns a defensive component copy so callers cannot retain mutable text state.
     */
    @Override
    public Component displayName() {
        return this.displayName.copy();
    }

    private static ObjectList<ResourceLocation> canonicalIds(ObjectList<ResourceLocation> ids) {
        ObjectLinkedOpenHashSet<ResourceLocation> unique = new ObjectLinkedOpenHashSet<>(ids);
        ObjectArrayList<ResourceLocation> canonical = new ObjectArrayList<>(unique);
        canonical.sort(Comparator.comparing(ResourceLocation::toString));
        return ObjectLists.unmodifiable(canonical);
    }

    /**
     * Checks whether the provider implements one known behavior.
     *
     * @param capability stable behavior identifier
     * @return whether the capability was declared
     */
    public boolean supports(ResourceLocation capability) {
        return this.capabilities.contains(capability);
    }
}
