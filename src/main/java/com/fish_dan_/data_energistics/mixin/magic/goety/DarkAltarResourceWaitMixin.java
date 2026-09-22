package com.fish_dan_.data_energistics.mixin.magic.goety;

import com.fish_dan_.data_energistics.integration.magic.goety.packaged.DarkAltarAdapter;
import com.fish_dan_.data_energistics.integration.magic.goety.packaged.storage.DarkAltarRecoveryLedger;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.server.level.ServerLevel;

import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.ritual.EnchantItemRitual;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps an owned ritual pending when the native next soul debit cannot be paid. */
@Mixin(value = DarkAltarBlockEntity.class, remap = false)
public abstract class DarkAltarResourceWaitMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$waitForSouls(CallbackInfo callback) {
        var altar = (DarkAltarBlockEntity) (Object) this;
        if (!(altar.getLevel() instanceof ServerLevel level)) return;
        var claims = PackagedMachineClaims.get(level);
        var owner = claims.owner(altar.getBlockPos());
        if (owner == null) return;
        DarkAltarRecoveryLedger.capture(altar);
        if (claims.structureRemoved(owner)) {
            callback.cancel();
            return;
        }
        var recipe = altar.getCurrentRitualRecipe();
        if (recipe == null) return;
        if (!(level.getBlockEntity(altar.getBlockPos().below()) instanceof CursedCageBlockEntity cage) || cage.getItem().isEmpty() || cage.getSouls() <= 0 || level.getGameTime() % 20 == 0 && cage.getSouls() < recipe.getSoulCost()) {
            callback.cancel();
        }
        if (recipe.getRitual() instanceof EnchantItemRitual enchant && altar.castingPlayer != null && altar.experienceTaken < enchant.getLevelCost(altar.itemStackHandler.getStackInSlot(0)) && altar.castingPlayer.experienceLevel <= 1) {
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void dataEnergistics$recordConsumption(CallbackInfo callback) {
        DarkAltarRecoveryLedger.capture((DarkAltarBlockEntity) (Object) this);
    }

    @Inject(method = "stopRitual", at = @At("HEAD"))
    private void dataEnergistics$preserveBeforeStop(boolean finished, CallbackInfo callback) {
        DarkAltarRecoveryLedger.capture((DarkAltarBlockEntity) (Object) this);
    }

    @Inject(method = "clearRitual", at = @At("HEAD"))
    private void dataEnergistics$preserveBeforeClear(CallbackInfo callback) {
        DarkAltarRecoveryLedger.capture((DarkAltarBlockEntity) (Object) this);
    }

    @Inject(method = "stopRitual", at = @At("RETURN"))
    private void dataEnergistics$recordCompletion(boolean finished, CallbackInfo callback) {
        if (!finished) return;
        var altar = (DarkAltarBlockEntity) (Object) this;
        if (!(altar.getLevel() instanceof ServerLevel level)) return;
        var owner = PackagedMachineClaims.get(level).owner(altar.getBlockPos());
        if (owner != null) {
            DarkAltarRecoveryLedger.get(level).completed(owner);
            altar.getPersistentData().putUUID(DarkAltarAdapter.COMPLETED_OPERATION, owner);
            altar.setChanged();
        }
    }
}
