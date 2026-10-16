package com.fish_dan_.data_energistics.blockentity.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.research.ResearchStationBlock;
import com.fish_dan_.data_energistics.common.research.persistence.ResearchSavedData;
import com.fish_dan_.data_energistics.common.research.progression.ResearchExecution;
import com.fish_dan_.data_energistics.common.research.progression.ResearchStationState;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.util.SettingsFrom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/** Physical AE anchor for a world-owned station journal. UUID copies share its target and tick budget. */
public final class ResearchStationBlockEntity extends AENetworkedBlockEntity {

    private UUID stationId = UUID.randomUUID();
    private ItemStack displayedTarget = ItemStack.EMPTY;
    private final InternalInventory inventory = new TargetInventory();
    private boolean working;
    private boolean faulted;
    private boolean partnerEntityChecked;

    public ResearchStationBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.RESEARCH_STATION.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(1);
    }

    public UUID stationId() {
        return stationId;
    }

    public ServerLevel serverLevel() {
        return (ServerLevel) getLevel();
    }

    public ResearchStationState state() {
        return ResearchSavedData.get(serverLevel().getServer()).station(stationId);
    }

    public InternalInventory targetInventory() {
        return inventory;
    }

    public boolean working() {
        return working;
    }

    public boolean faulted() {
        return faulted;
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        Direction facing = getBlockState().getValue(ResearchStationBlock.FACING);
        return EnumSet.of(Direction.DOWN, facing.getOpposite(), facing.getCounterClockWise());
    }

    @Override
    public AECableType getCableConnectionType(Direction side) {
        return AECableType.SMART;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (!getMainNode().isOnline()) working = false;
        markForUpdate();
    }

    public void serverTick() {
        if (faulted) return;
        try {
            ensurePartnerBlockEntity();
            ResearchExecution.tick(this);
            ResearchStationState state = state();
            boolean targetChanged = !ItemStack.matches(displayedTarget, state.target);
            if (targetChanged) displayedTarget = state.target.copy();
            boolean next = getMainNode().isOnline() && state.lastTick == serverLevel().getServer().getTickCount() &&
                    state.projects.values().stream().anyMatch(project -> project.powered);
            if (working != next || targetChanged) {
                working = next;
                markForClientUpdate();
            }
        } catch (RuntimeException error) {
            faulted = true;
            working = false;
            markForUpdate();
            Data_Energistics.LOGGER.error("Research station {} stopped at {} in {}", stationId, worldPosition, serverLevel().dimension().location(), error);
        }
    }

    /** Old saves have no part BE tag; create it in a loaded chunk and inherit the main node's AE ownership. */
    private void ensurePartnerBlockEntity() {
        if (partnerEntityChecked) return;
        IGridNode mainNode = getMainNode().getNode();
        if (mainNode == null) return;
        BlockPos other = ResearchStationBlock.partner(worldPosition, getBlockState());
        LevelChunk chunk = serverLevel().getChunkSource().getChunkNow(other.getX() >> 4, other.getZ() >> 4);
        if (chunk == null || !chunk.getBlockState(other).is(DEBlocks.RESEARCH_STATION_PART.get())) return;
        if (!(chunk.getBlockEntity(other, LevelChunk.EntityCreationType.IMMEDIATE) instanceof ResearchStationPartBlockEntity part)) {
            throw new IllegalStateException("Research station part has no network block entity at " + other);
        }
        part.getMainNode().setOwningPlayerId(mainNode.getOwningPlayerId());
        part.saveChanges();
        partnerEntityChecked = true;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (tag.hasUUID("research_station_id")) stationId = tag.getUUID("research_station_id");
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("research_station_id", stationId);
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) builder.set(DEDataComponents.RESEARCH_STATION_ID.get(), stationId);
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap components, @Nullable Player player) {
        super.importSettings(mode, components, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            UUID restored = components.get(DEDataComponents.RESEARCH_STATION_ID.get());
            if (restored != null) stationId = restored;
        }
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf buffer) {
        super.writeToStream(buffer);
        buffer.writeUUID(stationId);
        buffer.writeBoolean(working);
        buffer.writeBoolean(faulted);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, state().target);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf buffer) {
        boolean changed = super.readFromStream(buffer);
        stationId = buffer.readUUID();
        boolean next = buffer.readBoolean();
        boolean nextFaulted = buffer.readBoolean();
        ItemStack nextTarget = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        changed |= working != next || faulted != nextFaulted || !ItemStack.matches(displayedTarget, nextTarget);
        working = next;
        faulted = nextFaulted;
        displayedTarget = nextTarget;
        return changed;
    }

    private final class TargetInventory extends BaseInternalInventory {

        @Override
        public int size() {
            return 1;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return level != null && level.isClientSide() ? displayedTarget : state().target;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return level != null && level.isClientSide() || !state().locked();
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            if (level != null && level.isClientSide()) {
                displayedTarget = stack;
                return;
            }
            ResearchStationState state = state();
            if (state.locked()) throw new IllegalStateException("Cannot replace locked research target");
            if (stack.getCount() > 1) throw new IllegalArgumentException("Research slot accepts one item");
            state.target = stack;
            state.revision++;
            ResearchSavedData.get(serverLevel().getServer()).setDirty();
            saveChanges();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return level != null && !level.isClientSide() && state().locked() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }
    }
}
