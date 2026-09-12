package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatch;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderProfile;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarAction;

import appeng.api.stacks.AEItemKey;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Shared construction rules for the individual adaptive provider registrations.
 * Mod-specific declarations remain in their own entrypoint classes.
 */
public final class AdaptivePatternProviderRegistrationFactory {

    private AdaptivePatternProviderRegistrationFactory() {}

    /**
     * Creates a registry-ID matched provider declaration.
     */
    public static AdaptivePatternProviderRegistration fixed(
                                                            String path,
                                                            Predicate<ItemStack> matcher,
                                                            int slotsPerProvider,
                                                            ObjectSet<ResourceLocation> capabilities,
                                                            ResourceLocation... toolbarActionIds) {
        return fixed(path, matcher, slotsPerProvider, capabilities, context -> false, toolbarActionIds);
    }

    /**
     * Creates a provider declaration with a dispatch implementation owned by its integration registration.
     */
    public static AdaptivePatternProviderRegistration fixed(
                                                            String path,
                                                            Predicate<ItemStack> matcher,
                                                            int slotsPerProvider,
                                                            ObjectSet<ResourceLocation> capabilities,
                                                            AdaptivePatternProviderDispatch dispatch,
                                                            ResourceLocation... toolbarActionIds) {
        return new AdaptivePatternProviderRegistration(
                Data_Energistics.id("adaptive_pattern_provider/" + path),
                providerStack -> {
                    if (!matcher.test(providerStack)) {
                        return null;
                    }
                    ItemStack icon = providerStack.copyWithCount(1);
                    AEItemKey terminalIcon = AEItemKey.of(icon);
                    if (terminalIcon == null) {
                        throw new IllegalStateException("Adaptive pattern provider item has no AE item key");
                    }
                    return new AdaptivePatternProviderProfile(
                            slotsPerProvider,
                            icon,
                            terminalIcon,
                            icon.getHoverName(),
                            capabilities);
                },
                dispatch,
                toolbarActions(toolbarActionIds));
    }

    /**
     * Creates a fastutil capability set for a provider declaration.
     */
    public static ObjectSet<ResourceLocation> capabilities(ResourceLocation... capabilities) {
        ObjectOpenHashSet<ResourceLocation> result = new ObjectOpenHashSet<>();
        result.addAll(Arrays.asList(capabilities));
        return ObjectSets.unmodifiable(result);
    }

    /**
     * Creates a registry-ID matcher without linking optional implementation classes.
     */
    public static Predicate<ItemStack> itemIds(String... ids) {
        ObjectSet<ResourceLocation> itemIds = new ObjectOpenHashSet<>();
        Arrays.stream(ids).map(ResourceLocation::parse).forEach(itemIds::add);
        return stack -> itemIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static ObjectList<AdaptivePatternProviderToolbarAction> toolbarActions(ResourceLocation[] actionIds) {
        ObjectArrayList<AdaptivePatternProviderToolbarAction> actions = new ObjectArrayList<>();
        for (ResourceLocation actionId : actionIds) {
            actions.add(new AdaptivePatternProviderToolbarAction(actionId));
        }
        return ObjectLists.unmodifiable(actions);
    }
}
