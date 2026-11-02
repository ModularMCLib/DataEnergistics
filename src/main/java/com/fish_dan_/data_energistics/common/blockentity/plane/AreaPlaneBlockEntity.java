package com.fish_dan_.data_energistics.common.blockentity.plane;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.block.plane.AreaPlaneBlock;
import com.fish_dan_.data_energistics.common.data.DEBlockEntities;
import com.fish_dan_.data_energistics.common.plane.annihilation.AreaPlaneAnnihilation;
import com.fish_dan_.data_energistics.common.plane.configuration.PlaneConfiguration;
import com.fish_dan_.data_energistics.common.plane.formation.AreaPlaneFormation;
import com.fish_dan_.data_energistics.common.plane.model.AreaPlaneVariant;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;
import com.fish_dan_.data_energistics.common.plane.structure.AreaPlaneStructure;

import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import lombok.Getter;

import java.util.EnumSet;
import java.util.List;

public final class AreaPlaneBlockEntity extends AENetworkedBlockEntity {

    @Getter
    private final PlaneConfiguration configuration;
    private final AreaPlaneFormation formation = new AreaPlaneFormation(this);
    private final AreaPlaneAnnihilation annihilation = new AreaPlaneAnnihilation(this);
    private final MachineSource actionSource = new MachineSource(this);
    private int workTicks;
    @Getter
    private boolean faulted;

    public AreaPlaneBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.AREA_PLANE.get(), pos, state);
        configuration = new PlaneConfiguration(((AreaPlaneBlock) state.getBlock()).getVariant().getSlotCount(), this::configurationChanged);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1);
    }

    public PlaneLayout layout() {
        return ((AreaPlaneBlock) getBlockState().getBlock()).layout(getBlockState());
    }

    private void configurationChanged() {
        faulted = false;
        workTicks = 0;
        saveChanges();
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(getBlockState().getValue(AreaPlaneBlock.FACING).getOpposite());
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return direction == getBlockState().getValue(AreaPlaneBlock.FACING).getOpposite() ? AECableType.SMART : AECableType.NONE;
    }

    public void serverTick() {
        if (faulted || ++workTicks < 5) return;
        workTicks = 0;
        ServerLevel level = (ServerLevel) getLevel();
        try {
            PlaneLayout layout = layout();
            var integrity = AreaPlaneStructure.integrity(level, worldPosition, layout);
            if (integrity == AreaPlaneStructure.Integrity.BROKEN) {
                level.destroyBlock(worldPosition, true);
                return;
            }
            if (integrity == AreaPlaneStructure.Integrity.UNLOADED) return;
            getMainNode().ifPresent((grid, node) -> {
                if (!node.isActive()) return;
                if (layout.variant().getOperation() == AreaPlaneVariant.Operation.FORMATION) {
                    formation.tick(level, layout, configuration, grid, actionSource, node.getOwningPlayerProfileId());
                } else {
                    annihilation.tick(level, layout, configuration, grid, actionSource, node.getOwningPlayerProfileId());
                }
            });
        } catch (RuntimeException error) {
            faulted = true;
            saveChanges();
            Data_Energistics.LOGGER.error("Area plane stopped at {} in {}", worldPosition, level.dimension().location(), error);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        configuration.read(tag.getCompound("plane_configuration"), registries);
        formation.read(tag, registries);
        faulted = tag.getBoolean("plane_faulted");
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag cells = new CompoundTag();
        configuration.write(cells, registries);
        tag.put("plane_configuration", cells);
        tag.putBoolean("plane_faulted", faulted);
        formation.write(tag, registries);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        formation.addDrops(drops);
    }
}
