package com.fish_dan_.data_energistics.common.research.development;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.research.persistence.ResearchSavedData;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.server.level.ServerPlayer;

/** Persistent development entitlement for free, instant research through the normal station flow. */
public final class ResearchDevelopmentMode {

    private ResearchDevelopmentMode() {}

    public static void enableTechnology(ServerPlayer player) {
        ResearchSavedData.get(player.server).enableInstantTechnology(ResearchAccess.subjects(player, null));
    }

    public static boolean enableHeldItem(ServerPlayer player) {
        var stack = player.getMainHandItem();
        if (stack.isEmpty()) return false;
        stack.set(DEDataComponents.RESEARCH_INSTANT.get(), true);
        return true;
    }

    public static void enablePlayerResearch(ServerPlayer player) {
        ResearchSavedData.get(player.server).enableInstantPlayerResearch(player.getUUID());
    }
}
