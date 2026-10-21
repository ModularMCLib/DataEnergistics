package com.fish_dan_.data_energistics.mixin.minecraft.entity;

import com.fish_dan_.data_energistics.common.entity.projectile.DataTridentAppearance;
import com.fish_dan_.data_energistics.common.item.trident.DataTridentItem;
import com.fish_dan_.data_energistics.common.item.trident.TridentMobField;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.projectile.ThrownTrident;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the data trident's base damage before vanilla applies impaling and other enchantment effects.
 */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin implements DataTridentAppearance {

    @Unique
    private static final EntityDataAccessor<Boolean> DATA_ENERGISTICS_DATA_TRIDENT = SynchedEntityData.defineId(
            ThrownTrident.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void dataEnergistics$defineTextureIdentity(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(DATA_ENERGISTICS_DATA_TRIDENT, false);
    }

    // Bare <init> selects only the EntityType/Level overload, skipping the two stack-taking throw constructors.
    @Inject(method = "<init>*", at = @At("RETURN"), require = 3)
    private void dataEnergistics$initializeTextureIdentity(CallbackInfo callback) {
        this.dataEnergistics$syncTextureIdentity();
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void dataEnergistics$restoreTextureIdentity(CompoundTag tag, CallbackInfo callback) {
        this.dataEnergistics$syncTextureIdentity();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void dataEnergistics$tickMobField(CallbackInfo callback) {
        TridentMobField.tick((ThrownTrident) (Object) this);
    }

    @Unique
    private void dataEnergistics$syncTextureIdentity() {
        ThrownTrident trident = (ThrownTrident) (Object) this;
        trident.getEntityData().set(DATA_ENERGISTICS_DATA_TRIDENT,
                trident.getWeaponItem().getItem() instanceof DataTridentItem);
    }

    @Override
    public boolean usesDataTridentTexture() {
        return ((ThrownTrident) (Object) this).getEntityData().get(DATA_ENERGISTICS_DATA_TRIDENT);
    }

    @ModifyConstant(method = "onHitEntity", constant = @Constant(floatValue = 8.0F))
    private float dataEnergistics$dataTridentDamage(float original) {
        ThrownTrident trident = (ThrownTrident) (Object) this;
        return trident.getWeaponItem().getItem() instanceof DataTridentItem ? DataTridentItem.DAMAGE : original;
    }
}
