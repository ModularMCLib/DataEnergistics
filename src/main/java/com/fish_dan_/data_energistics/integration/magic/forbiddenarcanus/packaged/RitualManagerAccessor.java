package com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.packaged;

import net.minecraft.core.Holder;

import com.stal111.forbidden_arcanus.common.block.entity.forge.ForgeDataCache;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Server-thread access to the native cache and cancellation without consuming pedestal inventories. */
public interface RitualManagerAccessor {

    /** Returns the current cache for read-only admission checks; callers must not mutate it. */
    ForgeDataCache dataEnergistics$dataCache();

    /** Clears native ritual timing on dismantling without invoking reset's destructive pedestal clear. */
    void dataEnergistics$setActiveRitual(@Nullable Holder<Ritual> ritual, @Nullable UUID startedBy);
}
