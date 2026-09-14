package com.fish_dan_.data_energistics.api.registry.search;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.List;

/**
 * Supplies extra independent search candidates for an encoded Trinity pattern.
 *
 * <p>
 * Contributors expose pattern metadata that is meaningful to a foreign machine but is not represented by AE2's
 * ordinary input or output stacks. Each returned value is matched as its own candidate, so a query never combines
 * one token from a machine-specific value with another token from a visible input or output.
 * </p>
 */
@FunctionalInterface
public interface TrinityPatternSearchTermContributor {

    /**
     * Extracts stable, player-visible search candidates from one encoded pattern stack.
     *
     * @param encodedPattern encoded pattern displayed by the Trinity information exchange depot
     * @return non-null independent candidate names in declaration order
     * @deprecated scheduled for removal in plan 340; use {@link #searchTermsFast(ItemStack)}
     */
    @Deprecated(forRemoval = true)
    List<String> searchTerms(ItemStack encodedPattern);

    /** Returns candidate names as an immutable FastUtil snapshot. */
    default ObjectList<String> searchTermsFast(ItemStack encodedPattern) {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(searchTerms(encodedPattern)));
    }
}
