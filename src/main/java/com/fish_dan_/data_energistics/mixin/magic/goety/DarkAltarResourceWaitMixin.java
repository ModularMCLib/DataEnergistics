package com.fish_dan_.data_energistics.mixin.magic.goety;

import com.fish_dan_.data_energistics.integration.magic.goety.packaged.DarkAltarAdapter;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;
import com.fish_dan_.data_energistics.world.packaged.PackagedRecoveryJournal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BucketItem;

import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.ritual.EnchantItemRitual;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
        dataEnergistics$captureConsumption();
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
        dataEnergistics$captureConsumption();
    }

    @Inject(method = "stopRitual", at = @At("HEAD"))
    private void dataEnergistics$preserveBeforeStop(boolean finished, CallbackInfo callback) {
        dataEnergistics$captureConsumption();
    }

    @Inject(method = "clearRitual", at = @At("HEAD"))
    private void dataEnergistics$preserveBeforeClear(CallbackInfo callback) {
        dataEnergistics$captureConsumption();
    }

    /** Copies actual native consumption before clear/stop can erase it. */
    @Unique
    private void dataEnergistics$captureConsumption() {
        var altar = (DarkAltarBlockEntity) (Object) this;
        if (!(altar.getLevel() instanceof ServerLevel level)) return;
        var owner = PackagedMachineClaims.get(level).owner(altar.getBlockPos());
        if (owner == null || altar.getCurrentRitualRecipe() == null && altar.consumedIngredients.isEmpty()) return;
        var journal = PackagedRecoveryJournal.get(level);
        var data = journal.read(owner);
        if (data.getBoolean("completed")) return;
        var receipts = data.getList("consumed", Tag.TAG_COMPOUND);
        if (altar.consumedIngredients.size() < receipts.size()) return;
        for (int index = receipts.size(); index < altar.consumedIngredients.size(); index++) {
            var stack = altar.consumedIngredients.get(index);
            if (stack.isEmpty()) throw new IllegalStateException("Native Goety consumption receipt is empty");
            var entry = new CompoundTag();
            entry.put("input", stack.save(level.registryAccess()));
            // Native buckets and crafting remainders are recovered as physical entities,
            // never alongside a recreated input.
            boolean transformed = stack.getItem() instanceof BucketItem bucket && !bucket.content.defaultFluidState().isEmpty() || stack.hasCraftingRemainingItem();
            entry.putBoolean("refund_input", !transformed);
            receipts.add(entry);
        }
        data.put("consumed", receipts);
        data.putBoolean("observed", true);
        journal.write(owner, data);
    }

    @Inject(method = "stopRitual", at = @At("RETURN"))
    private void dataEnergistics$recordCompletion(boolean finished, CallbackInfo callback) {
        if (!finished) return;
        var altar = (DarkAltarBlockEntity) (Object) this;
        if (!(altar.getLevel() instanceof ServerLevel level)) return;
        var owner = PackagedMachineClaims.get(level).owner(altar.getBlockPos());
        if (owner != null) {
            var journal = PackagedRecoveryJournal.get(level);
            var data = journal.read(owner);
            data.putBoolean("observed", true);
            data.putBoolean("completed", true);
            journal.write(owner, data);
            altar.getPersistentData().putUUID(DarkAltarAdapter.COMPLETED_OPERATION, owner);
            altar.setChanged();
        }
    }
}
