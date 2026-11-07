package com.fish_dan_.data_energistics.core.mixins.com.brandon3055.draconicevolution.entity.guardian;

import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureAttachments;
import com.fish_dan_.data_energistics.integration.technology.draconicevolution.orbital.GuardianEncounterCleanup;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import com.brandon3055.draconicevolution.entity.guardian.DraconicGuardianEntity;
import com.brandon3055.draconicevolution.entity.guardian.GuardianFightManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;

/** Routes the encounter's native heart through its owning death's capture and cancellable drop event. */
@Restriction(require = @Condition("draconicevolution"))
@Mixin(value = GuardianFightManager.class, remap = false)
public abstract class GuardianFightManagerMixin implements GuardianEncounterCleanup {

    @WrapOperation(method = "processDragonDeath", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean dataEnergistics$captureTerminalReward(Level level, Entity reward, Operation<Boolean> original, DraconicGuardianEntity guardian) {
        if (reward instanceof ItemEntity item && ErasureAttachments.blocksRecovery(guardian)) {
            var drops = guardian.captureDrops();
            if (drops != null) {
                if (level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) drops.add(item);
                return true;
            }
        }
        return original.call(level, reward);
    }

    @Override
    @Invoker("cleanUpAndDispose")
    public abstract void dataEnergistics$releaseErasedEncounter();
}
