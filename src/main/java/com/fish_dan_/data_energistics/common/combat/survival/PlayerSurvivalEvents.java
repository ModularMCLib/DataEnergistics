package com.fish_dan_.data_energistics.common.combat.survival;

import com.fish_dan_.data_energistics.common.data.DEAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Seeds life-local attribute state on world entry and restores missing initial observations. */
public final class PlayerSurvivalEvents {

    @SubscribeEvent
    public void joined(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PlayerSurvival.observe(player);
    }

    @SubscribeEvent
    public void tick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var writes = player.getData(DEAttachments.SHIELD_HEALTH_WRITES);
            if (writes.maximumHealth == null) PlayerSurvival.observe(player);
        }
    }
}
