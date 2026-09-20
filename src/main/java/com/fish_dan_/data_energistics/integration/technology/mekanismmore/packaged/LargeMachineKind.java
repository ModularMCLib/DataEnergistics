package com.fish_dan_.data_energistics.integration.technology.mekanismmore.packaged;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.jerry.meklm.common.inventory.slot.BigStackInputInventorySlot;
import com.jerry.meklm.common.inventory.slot.BigStackOutputInventorySlot;
import com.jerry.meklm.common.tile.machine.TileEntityLargeAntiprotonicNucleosynthesizer;
import com.jerry.meklm.common.tile.machine.TileEntityLargeChemicalInfuser;
import com.jerry.meklm.common.tile.machine.TileEntityLargeElectrolyticSeparator;
import com.jerry.meklm.common.tile.machine.TileEntityLargePigmentMixer;
import com.jerry.meklm.common.tile.machine.TileEntityLargeRotaryCondensentrator;
import com.jerry.meklm.common.tile.machine.TileEntityLargeSolarNeutronActivator;
import it.unimi.dsi.fastutil.objects.ObjectList;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.common.tile.TileEntityChemicalTank.GasMode;
import mekanism.common.tile.prefab.TileEntityRecipeMachine;
import org.jspecify.annotations.Nullable;

import java.util.List;

public enum LargeMachineKind {

    INFUSER(MekanismRecipeTypes.NAME_CHEMICAL_INFUSING),
    PIGMENT_MIXER(MekanismRecipeTypes.NAME_PIGMENT_MIXING),
    SOLAR_ACTIVATOR(MekanismRecipeTypes.NAME_ACTIVATING),
    SEPARATOR(MekanismRecipeTypes.NAME_SEPARATING),
    ROTARY(MekanismRecipeTypes.NAME_ROTARY),
    NUCLEOSYNTHESIZER(MekanismRecipeTypes.NAME_NUCLEOSYNTHESIZING);

    final ResourceLocation recipeType;

    LargeMachineKind(ResourceLocation recipeType) {
        this.recipeType = recipeType;
    }

    @Nullable
    Layout layout(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position)) return null;
        var entity = level.getBlockEntity(position);
        if (entity instanceof TileEntityBoundingBlock bounding) {
            var main = bounding.getMainPos();
            if (main.equals(position) || !level.isLoaded(main)) return null;
            entity = level.getBlockEntity(main);
        }
        return layout(entity);
    }

    ObjectList<BlockPos> occupiedPositions(BlockPos position, CompoundTag progress) {
        var main = BlockPos.of(progress.getLong("machine_position"));
        return main.equals(position) ? ObjectList.of(position) : ObjectList.of(position, main);
    }

    @Nullable
    Layout runningLayout(ServerLevel level, BlockPos position, CompoundTag progress) {
        var layout = layout(level, position);
        if (layout != null && layout.tile().getBlockPos().asLong() != progress.getLong("machine_position")) {
            throw new IllegalStateException("Mekanism bounding block changed its owner");
        }
        return layout;
    }

    @Nullable
    Layout layout(@Nullable BlockEntity entity) {
        return switch (this) {
            case INFUSER -> entity instanceof TileEntityLargeChemicalInfuser tile ?
                    new Layout(tile, List.of(new MachineResourcePort.Chemical(tile.leftTank), new MachineResourcePort.Chemical(tile.rightTank)),
                            List.of(new MachineResourcePort.Chemical(tile.centerTank)), false) :
                    null;
            case PIGMENT_MIXER -> entity instanceof TileEntityLargePigmentMixer tile ?
                    new Layout(tile, List.of(new MachineResourcePort.Chemical(tile.leftInputTank), new MachineResourcePort.Chemical(tile.rightInputTank)),
                            List.of(new MachineResourcePort.Chemical(tile.outputTank)), false) :
                    null;
            case SOLAR_ACTIVATOR -> entity instanceof TileEntityLargeSolarNeutronActivator tile ?
                    new Layout(tile, List.of(new MachineResourcePort.Chemical(tile.inputTank)),
                            List.of(new MachineResourcePort.Chemical(tile.outputTank)), false) :
                    null;
            case SEPARATOR -> entity instanceof TileEntityLargeElectrolyticSeparator tile ?
                    new Layout(tile, List.of(new MachineResourcePort.Fluid(tile.fluidTank)),
                            List.of(new MachineResourcePort.Chemical(tile.leftTank), new MachineResourcePort.Chemical(tile.rightTank)), false) :
                    null;
            case ROTARY -> entity instanceof TileEntityLargeRotaryCondensentrator tile ?
                    new Layout(tile, tile.getMode() ? List.of(new MachineResourcePort.Fluid(tile.fluidTank)) : List.of(new MachineResourcePort.Chemical(tile.chemicalTank)),
                            tile.getMode() ? List.of(new MachineResourcePort.Chemical(tile.chemicalTank)) : List.of(new MachineResourcePort.Fluid(tile.fluidTank)), tile.getMode()) :
                    null;
            case NUCLEOSYNTHESIZER -> entity instanceof TileEntityLargeAntiprotonicNucleosynthesizer tile ? nucleosynthesizer(tile) : null;
        };
    }

    private static @Nullable Layout nucleosynthesizer(TileEntityLargeAntiprotonicNucleosynthesizer tile) {
        MachineResourcePort.Item input = null;
        MachineResourcePort.Item output = null;
        for (var slot : tile.getInventorySlots(null)) {
            if (slot instanceof BigStackInputInventorySlot) input = new MachineResourcePort.Item(slot);
            if (slot instanceof BigStackOutputInventorySlot) output = new MachineResourcePort.Item(slot);
        }
        return input == null || output == null ? null :
                new Layout(tile, List.of(input, new MachineResourcePort.Chemical(tile.gasTank)), List.of(output), false);
    }

    record Layout(TileEntityRecipeMachine<?> tile, List<MachineResourcePort> inputs,
                  List<MachineResourcePort> outputs, boolean fluidToChemical) {

        boolean empty() {
            if (tile.getSavedOperatingTicks(0) != 0) return false;
            if (tile instanceof TileEntityLargeAntiprotonicNucleosynthesizer n && n.getSavedUsedSoFar(0) != 0) return false;
            return tile.getInventorySlots(null).stream().allMatch(slot -> slot instanceof EnergyInventorySlot || slot.isEmpty()) && tile.getChemicalTanks(null).stream().allMatch(tank -> tank.isEmpty()) && tile.getFluidTanks(null).stream().allMatch(tank -> tank.isEmpty());
        }

        boolean operatingModeValid() {
            return !(tile instanceof TileEntityLargeElectrolyticSeparator separator) || separator.dumpLeft == GasMode.IDLE && separator.dumpRight == GasMode.IDLE;
        }
    }
}
