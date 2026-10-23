package com.fish_dan_.data_energistics.common.effect;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEMobEffects;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class RadixStasisAiLogic {

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }

        if (mob.hasEffect(DEMobEffects.RADIX_STASIS)) {
            if (!mob.hasData(DEAttachments.RADIX_STASIS_ORIGINAL_NO_AI)) {
                mob.setData(DEAttachments.RADIX_STASIS_ORIGINAL_NO_AI, mob.isNoAi());
            }
            if (!mob.isNoAi()) {
                mob.setNoAi(true);
            }
            return;
        }

        if (!mob.hasData(DEAttachments.RADIX_STASIS_ORIGINAL_NO_AI)) {
            return;
        }

        mob.setNoAi(mob.getData(DEAttachments.RADIX_STASIS_ORIGINAL_NO_AI));
        mob.removeData(DEAttachments.RADIX_STASIS_ORIGINAL_NO_AI);
    }
}
