package com.fish_dan_.data_energistics.integration.library.curios.equipment;

import com.fish_dan_.data_energistics.common.data.DEAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio.DropRule;

/** Both equipped and cosmetic curios remain available to Curios' normal player-clone transfer. */
public final class CuriosInventoryRetention {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void drops(DropRulesEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getData(DEAttachments.PLATFORM_RETAINED_INVENTORY).retained()) {
            event.addOverride(stack -> true, DropRule.ALWAYS_KEEP);
        }
    }
}
