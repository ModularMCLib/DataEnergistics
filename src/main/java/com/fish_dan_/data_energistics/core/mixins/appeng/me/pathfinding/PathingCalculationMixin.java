package com.fish_dan_.data_energistics.core.mixins.appeng.me.pathfinding;

import com.fish_dan_.data_energistics.integration.ae.ae2.grid.VirtualGridNode;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.me.pathfinding.PathingCalculation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Keeps virtual members out of AE2's physical controller traversal without altering native allocation rules.
 */
@Mixin(PathingCalculation.class)
@NullMarked
public abstract class PathingCalculationMixin {

    /**
     * Filters incoming virtual nodes from controller discovery while preserving physical iteration order.
     *
     * @param grid         grid being pathed
     * @param machineClass requested machine class
     * @return physical nodes only
     */
    @WrapOperation(
                   method = { "<init>", "propagateAssignments" },
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/api/networking/IGrid;getMachineNodes(Ljava/lang/Class;)Ljava/lang/Iterable;"),
                   require = 2)
    private Iterable<IGridNode> dataEnergistics$physicalMachineNodes(IGrid grid, Class<?> machineClass,
                                                                     Operation<Iterable<IGridNode>> original) {
        List<IGridNode> physicalNodes = new ObjectArrayList<>();
        for (IGridNode node : original.call(grid, machineClass)) {
            if (!(node instanceof VirtualGridNode virtualNode) || virtualNode.virtualPrimaryGrid() == null) {
                physicalNodes.add(node);
            }
        }
        return physicalNodes;
    }
}
