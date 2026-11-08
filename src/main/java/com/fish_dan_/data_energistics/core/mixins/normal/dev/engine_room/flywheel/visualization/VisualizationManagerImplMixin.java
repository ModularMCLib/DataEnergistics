package com.fish_dan_.data_energistics.core.mixins.normal.dev.engine_room.flywheel.visualization;

import com.fish_dan_.data_energistics.integration.library.flywheel.domain.DomainVisualSubjects;

import dev.engine_room.flywheel.api.backend.RenderContext;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = @Condition("flywheel"))
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", remap = false)
abstract class VisualizationManagerImplMixin {

    @Inject(method = "beginFrame", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/impl/task/Flag;lower()V"))
    private void dataEnergistics$gpuFrames(RenderContext context, CallbackInfo ci) {
        DomainVisualSubjects.frame(context.level(), context.renderer().getTicks() + context.partialTick());
    }
}
