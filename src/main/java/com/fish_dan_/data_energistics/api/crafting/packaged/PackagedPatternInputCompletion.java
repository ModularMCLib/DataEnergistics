package com.fish_dan_.data_energistics.api.crafting.packaged;

import appeng.api.stacks.GenericStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/**
 * Optional machine-adapter capability for world-dependent recipe inputs omitted by viewers.
 * Called on the server thread after transfer. Implementations are stateless and must not mutate
 * the world or the supplied non-null, compact ghost-input list; no physical inventory is involved.
 */
public interface PackagedPatternInputCompletion {

    /**
     * Returns the complete compact input list for the exact recipe, or null when it cannot be resolved.
     * Repeated completion must be idempotent. Invalid internal accounting should throw rather than
     * silently encode an incomplete recipe. Returned entries must have positive amounts.
     */
    @Nullable
    ObjectList<GenericStack> completePatternInputs(ServerLevel level, ResourceLocation recipeId, ObjectList<GenericStack> inputs);
}
