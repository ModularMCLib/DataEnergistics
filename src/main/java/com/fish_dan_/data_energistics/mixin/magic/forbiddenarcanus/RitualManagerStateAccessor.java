package com.fish_dan_.data_energistics.mixin.magic.forbiddenarcanus;

import net.minecraft.core.Holder;

import com.stal111.forbidden_arcanus.common.block.entity.forge.ForgeDataCache;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.UUID;

/** Server-thread access to the native cache and cancellation without consuming pedestal inventories. */
@Mixin(value = RitualManager.class, remap = false)
public interface RitualManagerStateAccessor {

    /** Returns the current cache for read-only admission checks; callers must not mutate it. */
    @Accessor("dataCache")
    ForgeDataCache dataEnergistics$dataCache();

    /** Clears native ritual timing on dismantling without invoking reset's destructive pedestal clear. */
    @Invoker("setActiveRitual")
    void dataEnergistics$setActiveRitual(@Nullable Holder<Ritual> ritual, @Nullable UUID startedBy);
}
