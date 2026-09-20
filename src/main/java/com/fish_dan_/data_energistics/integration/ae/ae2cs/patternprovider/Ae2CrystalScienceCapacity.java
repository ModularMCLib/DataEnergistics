package com.fish_dan_.data_energistics.integration.ae.ae2cs.patternprovider;

import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacity;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityContext;
import com.fish_dan_.data_energistics.integration.ae.ae2cs.patternprovider.capacity.AecsInputCapacity;

import appeng.api.inventories.InternalInventory;

import io.github.lounode.ae2cs.common.block.entity.AENetworkedComponentBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CircuitEtcherBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CrystalAggregatorBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CrystalGrowthChamberBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.CrystalPulverizerBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.EntropyVariationReactionChamberBlockEntity;
import io.github.lounode.ae2cs.common.block.entity.QuartzGrindstoneBlockEntity;
import io.github.lounode.ae2cs.common.machine.component.SideConfigComponent;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/** Publishes optional AECS machine capacities independently of Data Energistics machine registrations. */
@NullMarked
public final class Ae2CrystalScienceCapacity {

    private Ae2CrystalScienceCapacity() {}

    /** Reads loaded AE2CS input capacity on the server thread without consuming or reserving resources. */
    public static Optional<CraftingMachineCapacity> capture(CraftingMachineCapacityContext context) {
        var machine = (AENetworkedComponentBlockEntity) context.machine();
        var sides = machine.getMachineComponents().getService(SideConfigComponent.class);
        if (!sides.get(context.inputSide()).allowInsert()) {
            return Optional.of(new CraftingMachineCapacity(0L));
        }
        long capacity;
        if (machine instanceof EntropyVariationReactionChamberBlockEntity chamber) {
            capacity = AecsInputCapacity.capture(chamber.getInputInv(), context.prototype(), context.requestedCrafts());
        } else {
            InternalInventory inventory = switch (machine) {
                case CircuitEtcherBlockEntity etcher -> etcher.getInputInv();
                case CrystalAggregatorBlockEntity aggregator -> aggregator.getInputInv();
                case CrystalPulverizerBlockEntity pulverizer -> pulverizer.getInputInv();
                // The separate work slot is not additional queue space: it may contain the active recipe.
                case QuartzGrindstoneBlockEntity grindstone -> grindstone.getInputInv();
                case CrystalGrowthChamberBlockEntity growth -> growth.getInternalInventory();
                default -> throw new IllegalArgumentException("Unsupported AECS capacity machine: " + machine.getType());
            };
            capacity = AecsInputCapacity.capture(inventory, context.prototype(), context.requestedCrafts());
        }
        return Optional.of(new CraftingMachineCapacity(capacity));
    }
}
