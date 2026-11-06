package com.fish_dan_.data_energistics.core.mixins.de.ellpeck.naturesaura.chunk;

import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;
import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import de.ellpeck.naturesaura.chunk.AuraChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aura regeneration and drain-spot effects run independently of the native block entity tick list. */
@RequireMods("naturesaura")
@Mixin(value = AuraChunk.class, remap = false)
abstract class DomainAuraChunkMixin {

    @Shadow
    @Final
    private LevelChunk chunk;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$freeze(CallbackInfo ci) {
        if (chunk.getLevel() instanceof ServerLevel level && StasisDomainSavedData.get(level.getServer()).frozen(level, chunk.getPos().toLong())) ci.cancel();
    }
}
