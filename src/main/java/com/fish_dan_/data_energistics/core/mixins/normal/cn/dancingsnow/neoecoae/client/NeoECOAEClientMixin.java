package com.fish_dan_.data_energistics.core.mixins.normal.cn.dancingsnow.neoecoae.client;

import com.fish_dan_.data_energistics.integration.ae.neoecoae.tower.client.NeoEcoAdditionalRendererGuard;

import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer;

import cn.dancingsnow.neoecoae.client.NeoECOAEClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(require = @Condition("neoecoae"))
@Pseudo
@Mixin(value = NeoECOAEClient.class, remap = false)
@NullMarked
public class NeoECOAEClientMixin {

    @WrapOperation(
                   method = "onAddChunkGeometry",
                   at = @At(
                            value = "INVOKE",
                            target = "Lnet/neoforged/neoforge/client/event/AddSectionGeometryEvent;addRenderer(Lnet/neoforged/neoforge/client/event/AddSectionGeometryEvent$AdditionalSectionRenderer;)V"),
                   remap = false)
    private static void dataEnergistics$guardAdditionalRenderer(
                                                                AddSectionGeometryEvent event, AdditionalSectionRenderer renderer, Operation<Void> original) {
        original.call(event, NeoEcoAdditionalRendererGuard.guard(renderer, event.getSectionOrigin()));
    }
}
