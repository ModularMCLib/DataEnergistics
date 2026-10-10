package com.fish_dan_.data_energistics.blockentity.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.digitalsupply.DigitalSupplyExternalFluidHandler;
import com.fish_dan_.data_energistics.ae2.digitalsupply.DigitalSupplyExternalInput;
import com.fish_dan_.data_energistics.ae2.digitalsupply.DigitalSupplyExternalItemHandler;
import com.fish_dan_.data_energistics.ae2.digitalsupply.DigitalSupplyInterfaceTransferContext;
import com.fish_dan_.data_energistics.ae2.digitalsupply.DigitalSupplyNetworkStorage;
import com.fish_dan_.data_energistics.ae2.digitalsupply.PresenceMarkerStorage;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorEndpoint;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceRegistration;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyLinkContext;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;

/**
 * Independent AE network endpoint for digital-supply adapters.
 *
 * <p>
 * Only resource-type markers and adapter/link cursors are persisted. Consumable quantities always remain in the
 * ordinary AE network storage and are transferred through {@link DigitalSupplyInterfaceTransferContext}.
 * </p>
 */
public final class DigitalSupplyInterfaceBlockEntity extends AENetworkedBlockEntity
                                                     implements DigitalSupplyInterfaceTarget, DigitalSupplyLinkContext, ConnectorEndpoint {

    private static final String PRESENCE_MARKERS_TAG = "presence_markers";
    private static final String LINKS_TAG = "native_links";
    private static final String LINK_X_TAG = "x";
    private static final String LINK_Y_TAG = "y";
    private static final String LINK_Z_TAG = "z";
    private static final String LINK_SIDE_TAG = "side";
    private static final String LINK_MODE_TAG = "mode";
    private static final String LINK_SLOT_TAG = "slot";
    private static final String ADAPTER_STATES_TAG = "adapter_states";

    private final PresenceMarkerStorage presenceStorage = new PresenceMarkerStorage(this::onPresenceStorageChanged);
    private final IStorageProvider storageProvider = new MarkerStorageProvider();
    private final ObjectList<DigitalSupplyInterfaceAdapter> adapters;
    private final ObjectSet<ResourceLocation> failedAdapters = new ObjectLinkedOpenHashSet<>();
    @Getter
    private final IItemHandler externalItemHandler;
    @Getter
    private final IFluidHandler externalFluidHandler;
    private final ObjectSet<ConnectorLink> automaticLinks = new ObjectLinkedOpenHashSet<>();
    private ObjectList<ConnectorLink> links = new ObjectArrayList<>();
    private ConnectorMode mode = ConnectorMode.INPUT;

    public DigitalSupplyInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.DIGITAL_SUPPLY_INTERFACE.get(), pos, state);
        ObjectArrayList<DigitalSupplyInterfaceAdapter> discovered = new ObjectArrayList<>();
        for (DigitalSupplyInterfaceRegistration registration : DataEnergisticsEntrypointLoader.snapshot().digitalSupplyInterfaces()) {
            discovered.add(registration.adapter());
        }
        discovered.sort(Comparator.comparingInt(DigitalSupplyInterfaceAdapter::priority)
                .thenComparing(adapter -> adapter.id().toString()));
        this.adapters = ObjectLists.unmodifiable(discovered);
        DigitalSupplyExternalInput externalInput = new DigitalSupplyExternalInput(this, this.adapters, this.failedAdapters);
        this.externalItemHandler = new DigitalSupplyExternalItemHandler(externalInput);
        this.externalFluidHandler = new DigitalSupplyExternalFluidHandler(externalInput);
        this.getMainNode()
                .addService(IStorageProvider.class, this.storageProvider)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setVisualRepresentation(DEBlocks.DIGITAL_SUPPLY_INTERFACE.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.COVERED;
    }

    @Override
    public Level level() {
        if (this.level == null) {
            throw new IllegalStateException("Digital Supply Interface is not attached to a level");
        }
        return this.level;
    }

    @Override
    public BlockPos position() {
        return this.worldPosition;
    }

    @Override
    public @Nullable IGridNode gridNode() {
        return this.getMainNode().getNode();
    }

    @Override
    public @Nullable MEStorage networkStorage() {
        if (!this.getMainNode().isOnline() || this.getMainNode().getGrid() == null) {
            return null;
        }
        return new DigitalSupplyNetworkStorage(
                this.getMainNode().getGrid().getStorageService().getInventory(), this.presenceStorage);
    }

    /** Returns the marker mount for diagnostics and focused tests. */
    public MEStorage presenceStorage() {
        return this.presenceStorage;
    }

    @Override
    public ConnectorEndpoint connectorEndpoint() {
        return this;
    }

    @Override
    public DigitalSupplyLinkContext links() {
        return this;
    }

    @Override
    public ObjectSet<AEKey> presenceKeys() {
        return this.presenceStorage.keys();
    }

    @Override
    public void setPresence(AEKey key, boolean present) {
        this.presenceStorage.setPresent(key, present);
    }

    @Override
    public void refreshState() {
        this.setChanged();
        this.markForClientUpdate();
        if (this.level != null) {
            IStorageProvider.requestUpdate(this.getMainNode());
        }
    }

    /** Runs adapter discovery and native/network transactions on the logical server thread. */
    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        refreshAdjacentLinks();
        removeOfflineLinks();
        DigitalSupplyInterfaceTransferContext transfer = new DigitalSupplyInterfaceTransferContext(
                networkStorage(), new MachineSource(this));
        for (DigitalSupplyInterfaceAdapter adapter : this.adapters) {
            if (this.failedAdapters.contains(adapter.id())) {
                continue;
            }
            try {
                if (!adapter.supports(this)) {
                    continue;
                }
                adapter.discover(this);
                adapter.updateLinks(this);
                adapter.tick(this, transfer);
            } catch (RuntimeException exception) {
                this.failedAdapters.add(adapter.id());
                Data_Energistics.LOGGER.error(
                        "Disabled Digital Supply Interface adapter {} at {} after a tick failure",
                        adapter.id(),
                        this.worldPosition,
                        exception);
            }
        }
    }

    /** Detaches adapters before the block entity is removed. */
    public void detachAdapters() {
        for (DigitalSupplyInterfaceAdapter adapter : this.adapters) {
            try {
                adapter.detach(this);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to detach Digital Supply Interface adapter {} at {}",
                        adapter.id(),
                        this.worldPosition,
                        exception);
            }
        }
    }

    @Override
    public ObjectList<ConnectorLink> bindingsFast() {
        return ObjectLists.unmodifiable(new ObjectArrayList<>(this.links));
    }

    @Override
    public ConnectorMode mode() {
        return this.mode;
    }

    @Override
    public void setMode(ConnectorMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            refreshState();
        }
    }

    @Override
    public int slotCount() {
        return 0;
    }

    @Override
    public boolean toggle(BlockPos position, Direction side, int slot) {
        if (slot != -1) {
            throw new IllegalArgumentException("Digital Supply Interface links do not expose stock slots");
        }
        ConnectorLink existing = null;
        for (ConnectorLink candidate : this.links) {
            if (candidate.position().equals(position) && candidate.side() == side && candidate.slot() == slot) {
                existing = candidate;
                break;
            }
        }
        if (existing == null) {
            ConnectorLink link = new ConnectorLink(position, side, this.mode, -1);
            this.links.add(link);
            this.automaticLinks.remove(link);
            refreshState();
            return true;
        }
        this.links.remove(existing);
        this.automaticLinks.remove(existing);
        refreshState();
        return false;
    }

    @Override
    public int replaceFast(ObjectList<ConnectorLink> bindings) {
        ObjectArrayList<ConnectorLink> replacement = new ObjectArrayList<>();
        for (ConnectorLink binding : bindings) {
            if (binding.slot() != -1) {
                throw new IllegalArgumentException("Digital Supply Interface links do not expose stock slots");
            }
            if (!replacement.contains(binding)) {
                replacement.add(binding);
            }
        }
        this.links = replacement;
        this.automaticLinks.clear();
        refreshState();
        return replacement.size();
    }

    @Override
    public ObjectList<ConnectorLink> bindings() {
        return bindingsFast();
    }

    @Override
    public boolean isOnline(ConnectorLink link) {
        return this.level != null && this.level.isLoaded(link.position());
    }

    @Override
    public int removeOfflineLinks() {
        int before = this.links.size();
        this.links.removeIf(link -> !isOnline(link));
        int removed = before - this.links.size();
        this.automaticLinks.removeIf(link -> !this.links.contains(link));
        if (removed > 0) {
            refreshState();
        }
        return removed;
    }

    @Override
    public ResourceLocation dimensionId() {
        if (this.level == null) {
            return ResourceLocation.withDefaultNamespace("overworld");
        }
        return this.level.dimension().location();
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        this.presenceStorage.clear();
        if (data.contains(PRESENCE_MARKERS_TAG, Tag.TAG_LIST)) {
            ListTag markers = data.getList(PRESENCE_MARKERS_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < markers.size(); index++) {
                try {
                    GenericStack marker = GenericStack.readTag(
                            registries, markers.getCompound(index));
                    if (marker != null) {
                        this.presenceStorage.loadMarker(marker.what(), marker.amount());
                    }
                } catch (RuntimeException exception) {
                    Data_Energistics.LOGGER.error(
                            "Rejected Digital Supply Interface presence marker {} at {}",
                            index,
                            this.worldPosition,
                            exception);
                }
            }
        }
        readLinks(data);
        this.failedAdapters.clear();
        CompoundTag states = data.getCompound(ADAPTER_STATES_TAG);
        for (DigitalSupplyInterfaceAdapter adapter : this.adapters) {
            String key = adapter.id().toString();
            if (!states.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            try {
                adapter.loadState(states.getCompound(key));
            } catch (RuntimeException exception) {
                this.failedAdapters.add(adapter.id());
                Data_Energistics.LOGGER.error(
                        "Rejected persisted state for Digital Supply Interface adapter {} at {}",
                        adapter.id(),
                        this.worldPosition,
                        exception);
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        ListTag markers = new ListTag();
        for (AEKey key : this.presenceStorage.keys()) {
            markers.add(GenericStack.writeTag(registries, new GenericStack(key, 1L)));
        }
        data.put(PRESENCE_MARKERS_TAG, markers);
        writeLinks(data);
        CompoundTag states = new CompoundTag();
        for (DigitalSupplyInterfaceAdapter adapter : this.adapters) {
            CompoundTag state = new CompoundTag();
            adapter.saveState(state);
            states.put(adapter.id().toString(), state);
        }
        data.put(ADAPTER_STATES_TAG, states);
    }

    private void onPresenceStorageChanged() {
        refreshState();
    }

    private void readLinks(CompoundTag data) {
        this.links = new ObjectArrayList<>();
        this.automaticLinks.clear();
        ListTag saved = data.getList(LINKS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < saved.size(); index++) {
            CompoundTag link = saved.getCompound(index);
            int side = link.getInt(LINK_SIDE_TAG);
            int linkMode = link.getInt(LINK_MODE_TAG);
            if (side < 0 || side >= Direction.values().length || linkMode < 0 || linkMode >= ConnectorMode.values().length) {
                Data_Energistics.LOGGER.error("Rejected invalid Digital Supply Interface link {} at {}", index, this.worldPosition);
                continue;
            }
            this.links.add(new ConnectorLink(
                    new BlockPos(link.getInt(LINK_X_TAG), link.getInt(LINK_Y_TAG), link.getInt(LINK_Z_TAG)),
                    Direction.values()[side],
                    ConnectorMode.values()[linkMode],
                    -1));
        }
    }

    private void writeLinks(CompoundTag data) {
        ListTag saved = new ListTag();
        for (ConnectorLink link : this.links) {
            if (this.automaticLinks.contains(link)) {
                continue;
            }
            CompoundTag value = new CompoundTag();
            value.putInt(LINK_X_TAG, link.position().getX());
            value.putInt(LINK_Y_TAG, link.position().getY());
            value.putInt(LINK_Z_TAG, link.position().getZ());
            value.putInt(LINK_SIDE_TAG, link.side().ordinal());
            value.putInt(LINK_MODE_TAG, link.mode().ordinal());
            value.putInt(LINK_SLOT_TAG, -1);
            saved.add(value);
        }
        data.put(LINKS_TAG, saved);
    }

    private void refreshAdjacentLinks() {
        if (this.level == null) {
            return;
        }
        ObjectArrayList<ConnectorLink> stale = new ObjectArrayList<>();
        for (ConnectorLink link : this.automaticLinks) {
            if (link.mode() != ConnectorMode.BOTH || !isAdjacentLoadedTarget(link)) {
                stale.add(link);
            }
        }
        for (ConnectorLink link : stale) {
            this.automaticLinks.remove(link);
            this.links.remove(link);
        }
        for (Direction direction : Direction.values()) {
            BlockPos target = this.worldPosition.relative(direction);
            if (!this.level.isLoaded(target) || this.level.getBlockState(target).isAir()) {
                continue;
            }
            ConnectorLink link = new ConnectorLink(target, direction.getOpposite(), ConnectorMode.BOTH, -1);
            if (this.links.contains(link)) {
                continue;
            }
            this.links.add(link);
            this.automaticLinks.add(link);
        }
    }

    private boolean isAdjacentLoadedTarget(ConnectorLink link) {
        if (this.level == null || !this.level.isLoaded(link.position())) {
            return false;
        }
        return this.worldPosition.distManhattan(link.position()) == 1 && !this.level.getBlockState(link.position()).isAir();
    }

    private final class MarkerStorageProvider implements IStorageProvider {

        @Override
        public void mountInventories(IStorageMounts storageMounts) {
            storageMounts.mount(DigitalSupplyInterfaceBlockEntity.this.presenceStorage, IStorageMounts.DEFAULT_PRIORITY);
        }
    }
}
