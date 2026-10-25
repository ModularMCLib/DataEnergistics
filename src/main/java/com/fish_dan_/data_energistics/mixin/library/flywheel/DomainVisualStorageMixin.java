package com.fish_dan_.data_energistics.mixin.library.flywheel;

import com.fish_dan_.data_energistics.integration.library.flywheel.domain.DomainVisualSubjects;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.task.ConditionalPlan;
import dev.engine_room.flywheel.lib.task.ForEachPlan;
import dev.engine_room.flywheel.lib.task.PlanMap;
import dev.engine_room.flywheel.lib.task.functional.ConsumerWithContext;
import dev.engine_room.flywheel.lib.task.functional.SupplierWithContext;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

/** Pauses individual visual plans while leaving the backend and visuals outside the domain running. */
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.storage.Storage", remap = false)
abstract class DomainVisualStorageMixin {

    @Shadow
    public abstract Collection<Visual> getAllVisuals();

    @Inject(method = "invalidate", at = @At("HEAD"))
    private void dataEnergistics$clear(CallbackInfo ci) {
        getAllVisuals().forEach(DomainVisualSubjects::remove);
    }

    @WrapOperation(method = { "add", "lambda$recreateAll$4" }, at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/impl/visualization/storage/Storage;createRaw(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Ljava/lang/Object;F)Ldev/engine_room/flywheel/api/visual/Visual;"))
    private @Nullable Visual dataEnergistics$subject(@Coerce Object storage, VisualizationContext context, Object subject, float partial, Operation<Visual> original) {
        var visual = original.call(storage, context, subject, partial);
        if (visual != null) DomainVisualSubjects.add(visual, subject);
        return visual;
    }

    @WrapOperation(method = { "remove", "lambda$recreateAll$4" }, at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visual/Visual;delete()V"))
    private void dataEnergistics$release(Visual visual, Operation<Void> original) {
        DomainVisualSubjects.remove(visual);
        original.call(visual);
    }

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/task/PlanMap;add(Ljava/lang/Object;Ldev/engine_room/flywheel/api/task/Plan;)V"))
    private <K, C> void dataEnergistics$plan(PlanMap<K, C> plans, K visual, Plan<C> plan, Operation<Void> original) {
        original.call(plans, visual, ConditionalPlan.<C>on(() -> !DomainVisualSubjects.frozen(visual)).then(plan));
    }

    @WrapOperation(method = "framePlan", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/task/ForEachPlan;of(Ldev/engine_room/flywheel/lib/task/functional/SupplierWithContext$Ignored;Ldev/engine_room/flywheel/lib/task/functional/ConsumerWithContext;)Ldev/engine_room/flywheel/lib/task/ForEachPlan;"))
    private ForEachPlan<SimpleDynamicVisual, DynamicVisual.Context> dataEnergistics$frame(SupplierWithContext.Ignored<DynamicVisual.Context, List<SimpleDynamicVisual>> supplier, ConsumerWithContext<SimpleDynamicVisual, DynamicVisual.Context> action, Operation<ForEachPlan<SimpleDynamicVisual, DynamicVisual.Context>> original) {
        ConsumerWithContext<SimpleDynamicVisual, DynamicVisual.Context> guarded = (visual, context) -> { if (!DomainVisualSubjects.frozen(visual)) action.accept(visual, context); };
        return original.call(supplier, guarded);
    }

    @WrapOperation(method = "tickPlan", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/task/ForEachPlan;of(Ldev/engine_room/flywheel/lib/task/functional/SupplierWithContext$Ignored;Ldev/engine_room/flywheel/lib/task/functional/ConsumerWithContext;)Ldev/engine_room/flywheel/lib/task/ForEachPlan;"))
    private ForEachPlan<SimpleTickableVisual, TickableVisual.Context> dataEnergistics$tick(SupplierWithContext.Ignored<TickableVisual.Context, List<SimpleTickableVisual>> supplier, ConsumerWithContext<SimpleTickableVisual, TickableVisual.Context> action, Operation<ForEachPlan<SimpleTickableVisual, TickableVisual.Context>> original) {
        ConsumerWithContext<SimpleTickableVisual, TickableVisual.Context> guarded = (visual, context) -> { if (!DomainVisualSubjects.frozen(visual)) action.accept(visual, context); };
        return original.call(supplier, guarded);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/visual/Visual;update(F)V"))
    private void dataEnergistics$update(Visual visual, float partial, Operation<Void> original) {
        if (!DomainVisualSubjects.frozen(visual)) original.call(visual, partial);
    }
}
