package com.fish_dan_.data_energistics.mixin.magic.forbiddenarcanus;

import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import com.stal111.forbidden_arcanus.common.block.entity.forge.ForgeDataCache;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssencesDefinition;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.ActiveRitualData;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualManager;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Keeps unbound managers passive and preserves physical inputs of claimed server rituals. */
@Mixin(value = RitualManager.class, remap = false)
public abstract class RitualManagerRecoveryMixin {

    @Shadow
    private ServerLevel level;
    @Shadow
    private BlockPos pos;
    @Shadow
    private ForgeDataCache dataCache;
    @Shadow
    private @Nullable ActiveRitualData activeRitualData;

    @Shadow
    public abstract boolean isRitualActive();

    @Shadow
    public abstract void updateValidRitual(EssencesDefinition essences, HolderLookup.Provider lookup);

    @Inject(method = "onDataChanged", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$preservePhysicalInputs(ForgeDataCache cache, EssencesDefinition essences,
                                                        HolderLookup.Provider lookup, CallbackInfo callback) {
        // Client synchronization and loading before setup have no server world or position.
        // Refresh the cache without failing/resetting a ritual or sending server events.
        if (this.level != null && (!isRitualActive() || PackagedMachineClaims.get(this.level).owner(this.pos) == null)) return;
        this.dataCache = cache;
        updateValidRitual(essences, lookup);
        callback.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$waitForInputs(CallbackInfoReturnable<Optional<ItemStack>> callback) {
        if (this.level == null || this.activeRitualData == null || PackagedMachineClaims.get(this.level).owner(this.pos) == null) return;
        if (!this.activeRitualData.getRitual().checkIngredients(this.dataCache.getIngredients(), this.dataCache.mainIngredient())) {
            callback.setReturnValue(Optional.empty());
        }
    }
}
