package com.fish_dan_.data_energistics.mixin.library.flywheel;

import com.fish_dan_.data_energistics.integration.library.flywheel.domain.DomainVisualSubjects;

import dev.engine_room.flywheel.api.backend.RenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", remap = false)
abstract class DomainVisualFrameMixin {

    @Inject(method = "beginFrame", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/impl/task/Flag;lower()V"))
    private void dataEnergistics$gpuFrames(RenderContext context, CallbackInfo ci) {
        DomainVisualSubjects.frame(context.renderer().getTicks() + context.partialTick());
    }
}
