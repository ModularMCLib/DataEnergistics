package com.fish_dan_.data_energistics.common.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Advances the shared recipe epoch after the authoritative server data reload completes. */
@EventBusSubscriber(modid = Data_Energistics.MOD_ID)
public final class RecipeReloadEventHandler {

    private RecipeReloadEventHandler() {}

    /**
     * Invalidates all recipe-derived caches after tags and recipes have been rebound.
     *
     * @param event tag update event emitted at the end of data reload
     */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            RecipeReloadEpoch.advance();
        }
    }
}
