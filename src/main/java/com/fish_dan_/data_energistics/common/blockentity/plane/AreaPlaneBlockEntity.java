package com.fish_dan_.data_energistics.common.blockentity.plane;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.block.plane.AreaPlaneBlock;
import com.fish_dan_.data_energistics.common.data.DEBlockEntities;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.plane.annihilation.AreaPlaneAnnihilation;
import com.fish_dan_.data_energistics.common.plane.configuration.PlaneConfiguration;
import com.fish_dan_.data_energistics.common.plane.configuration.PlaneConfigurationData;
import com.fish_dan_.data_energistics.common.plane.formation.AreaPlaneFormation;
import com.fish_dan_.data_energistics.common.plane.model.AreaPlaneVariant;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;
import com.fish_dan_.data_energistics.common.plane.storage.PlaneSavedState;
import com.fish_dan_.data_energistics.common.plane.structure.AreaPlaneStructure;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;
import appeng.util.SettingsFrom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

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
        configuration = new PlaneConfiguration(((AreaPlaneBlock) state.getBlock()).getVariant().getConfigurationSlots(), this::configurationChanged);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1);
    }

    public PlaneLayout layout() {
        return ((AreaPlaneBlock) getBlockState().getBlock()).layout(getBlockState());
    }

    @Override
    public void setBlockState(BlockState state) {
        Direction previousFacing = getBlockState().getValue(AreaPlaneBlock.FACING);
        super.setBlockState(state);
        if (previousFacing != state.getValue(AreaPlaneBlock.FACING)) onGridConnectableSidesChanged();
    }

    private void configurationChanged() {
        faulted = false;
        workTicks = 0;
        saveChanges();
        updateActiveState();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        updateActiveState();
    }

    @Override
    public void onReady() {
        super.onReady();
        updateActiveState();
    }

    private void updateActiveState() {
        if (!(getLevel() instanceof ServerLevel level)) return;
        BlockState state = getBlockState();
        boolean active = !faulted && getMainNode().isOnline();
        if (state.getValue(AreaPlaneBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(AreaPlaneBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
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
            updateActiveState();
            Data_Energistics.LOGGER.error("Area plane stopped at {} in {}", worldPosition, level.dimension().location(), error);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        PlaneSavedState state = PlaneSavedState.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
        configuration.restore(state.configuration());
        formation.restorePendingItem(state.pendingItem());
        faulted = state.faulted();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        PlaneSavedState state = new PlaneSavedState(configuration.snapshot(), formation.copyPendingItem(), faulted);
        tag.merge((CompoundTag) PlaneSavedState.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), state).getOrThrow());
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        formation.drainPendingItem(drops::add);
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        builder.set(DEDataComponents.AREA_PLANE_CONFIGURATION.get(), configuration.snapshot());
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap components, @Nullable Player player) {
        super.importSettings(mode, components, player);
        PlaneConfigurationData data = components.get(DEDataComponents.AREA_PLANE_CONFIGURATION.get());
        if (data != null) {
            configuration.restore(data);
            configurationChanged();
        }
    }
}
