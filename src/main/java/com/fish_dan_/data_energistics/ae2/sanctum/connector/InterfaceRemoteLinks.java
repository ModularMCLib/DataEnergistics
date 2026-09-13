package com.fish_dan_.data_energistics.ae2.sanctum.connector;

import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceConstants;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorEndpoint;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Per-interface link metadata and transfer progress; the stock/config and legacy side-pull settings are separate. */
public final class InterfaceRemoteLinks implements ConnectorEndpoint {

    private static final String NBT_KEY = "remote_links";
    private final DataSanctumLargeInterfaceHost host;
    private final Runnable changed;
    private final IActionSource actionSource;
    private final IManagedGridNode mainNode;
    private List<ConnectorLink> links = List.of();
    private ConnectorMode mode = ConnectorMode.INPUT;
    private int syncedSlots = DataSanctumInterfaceConstants.BASE_PAGE_COUNT * DataSanctumInterfaceConstants.STOCK_SLOTS_PER_PAGE;
    private int linkCursor;
    private int[] sourceCursors = new int[0];
    private @Nullable GenericStack pendingReturn;

    public InterfaceRemoteLinks(DataSanctumLargeInterfaceHost host, IManagedGridNode mainNode, IActionSource actionSource, Runnable changed) {
        this.host = host;
        this.changed = changed;
        this.actionSource = actionSource;
        this.mainNode = mainNode;
    }

    @Override
    public List<ConnectorLink> bindings() {
        return links;
    }

    @Override
    public ConnectorMode mode() {
        return mode;
    }

    @Override
    public void setMode(ConnectorMode mode) {
        this.mode = mode;
        changed.run();
    }

    @Override
    public int slotCount() {
        Level level = host.getInterfaceLevel();
        return level != null && level.isClientSide() ? syncedSlots : host.getUnlockedPageCount() * DataSanctumInterfaceConstants.STOCK_SLOTS_PER_PAGE;
    }

    @Override
    public boolean toggle(BlockPos position, Direction side, int slot) {
        if (slot < 0 || slot >= slotCount()) {
            throw new IllegalArgumentException("Interface connector slot is locked or out of range: " + slot);
        }
        var replacement = new ArrayList<>(links);
        boolean removed = replacement.removeIf(link -> link.position().equals(position) && link.side() == side && link.slot() == slot);
        if (!removed) {
            replacement.add(new ConnectorLink(position.immutable(), side, mode, slot));
        }
        replace(replacement);
        return !removed;
    }

    @Override
    public int replace(List<ConnectorLink> bindings) {
        var unique = new LinkedHashMap<Identity, ConnectorLink>();
        for (var link : bindings) {
            if (link.slot() < 0 || link.slot() >= DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT) {
                throw new IllegalArgumentException("Interface link has no valid stock slot: " + link.slot());
            }
            unique.putIfAbsent(new Identity(link.position(), link.side(), link.slot()), link);
        }
        links = List.copyOf(unique.values());
        linkCursor = links.isEmpty() ? 0 : Math.floorMod(linkCursor, links.size());
        sourceCursors = new int[links.size()];
        changed.run();
        return links.size();
    }

    public void tick() {
        InterfaceRemoteTransfer.tick(host, this);
    }

    int linkCursor() {
        return linkCursor;
    }

    int sourceCursor(int linkIndex) {
        return sourceCursors[linkIndex];
    }

    boolean isActive() {
        return mainNode.isActive();
    }

    IActionSource actionSource() {
        return actionSource;
    }

    void advanceLink(int nextLink) {
        linkCursor = nextLink;
        host.saveChanges();
    }

    void advanceSource(int linkIndex, int nextSource) {
        sourceCursors[linkIndex] = nextSource;
        host.saveChanges();
    }

    boolean flushReturn() {
        if (pendingReturn == null) {
            return true;
        }
        long inserted = host.getReturnInventory().insert(pendingReturn.what(), pendingReturn.amount(), Actionable.MODULATE, actionSource);
        pendingReturn = inserted == pendingReturn.amount() ? null : new GenericStack(pendingReturn.what(), pendingReturn.amount() - inserted);
        if (inserted > 0) {
            host.saveChanges();
        }
        return pendingReturn == null;
    }

    void receive(GenericStack stack) {
        // Ownership moves here immediately after external extraction; backpressure is persisted until the return bar
        // accepts it.
        if (pendingReturn != null) {
            throw new IllegalStateException("Remote return is still pending");
        }
        pendingReturn = stack;
        host.saveChanges();
        flushReturn();
    }

    public void write(CompoundTag root, HolderLookup.Provider registries) {
        CompoundTag state = new CompoundTag();
        state.putString("mode", mode.name());
        state.putInt("link_cursor", linkCursor);
        state.put("pending_return", GenericStack.writeTag(registries, pendingReturn));
        ListTag targets = new ListTag();
        for (int index = 0; index < links.size(); index++) {
            var link = links.get(index);
            CompoundTag target = new CompoundTag();
            target.putLong("pos", link.position().asLong());
            target.putByte("side", (byte) link.side().get3DDataValue());
            target.putString("mode", link.mode().name());
            target.putInt("slot", link.slot());
            target.putInt("source_cursor", sourceCursors[index]);
            targets.add(target);
        }
        state.put("targets", targets);
        root.put(NBT_KEY, state);
    }

    public void read(CompoundTag root, HolderLookup.Provider registries) {
        CompoundTag state = root.getCompound(NBT_KEY);
        mode = state.contains("mode") ? ConnectorMode.valueOf(state.getString("mode")) : ConnectorMode.INPUT;
        var restored = new ArrayList<ConnectorLink>();
        ListTag targets = state.getList("targets", Tag.TAG_COMPOUND);
        int[] restoredCursors = new int[targets.size()];
        for (int index = 0; index < targets.size(); index++) {
            CompoundTag target = targets.getCompound(index);
            int side = target.getByte("side");
            int slot = target.getInt("slot");
            if (side < 0 || side > 5 || slot < 0 || slot >= DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT) {
                throw new IllegalArgumentException("Invalid saved interface remote link");
            }
            restored.add(new ConnectorLink(BlockPos.of(target.getLong("pos")), Direction.from3DDataValue(side),
                    ConnectorMode.valueOf(target.getString("mode")), slot));
            restoredCursors[index] = Math.max(0, target.contains("source_cursor") ? target.getInt("source_cursor") : state.getInt("source_cursor"));
        }
        links = List.copyOf(restored);
        linkCursor = links.isEmpty() ? 0 : Math.floorMod(state.getInt("link_cursor"), links.size());
        sourceCursors = restoredCursors;
        pendingReturn = GenericStack.readTag(registries, state.getCompound("pending_return"));
    }

    public void writeToStream(RegistryFriendlyByteBuf data) {
        data.writeEnum(mode);
        data.writeVarInt(slotCount());
        data.writeCollection(links, (buffer, link) -> {
            buffer.writeBlockPos(link.position());
            buffer.writeEnum(link.side());
            buffer.writeEnum(link.mode());
            buffer.writeVarInt(link.slot());
        });
    }

    public boolean readFromStream(RegistryFriendlyByteBuf data) {
        var nextMode = data.readEnum(ConnectorMode.class);
        int nextSlots = data.readVarInt();
        if (nextSlots < 1 || nextSlots > DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid interface slot count");
        }
        var nextLinks = data.readList(buffer -> {
            BlockPos position = buffer.readBlockPos();
            Direction side = buffer.readEnum(Direction.class);
            ConnectorMode linkMode = buffer.readEnum(ConnectorMode.class);
            int slot = buffer.readVarInt();
            if (slot < 0 || slot >= DataSanctumInterfaceConstants.LOGIC_SLOT_COUNT) {
                throw new IllegalArgumentException("Invalid synchronized interface link slot: " + slot);
            }
            return new ConnectorLink(position, side, linkMode, slot);
        });
        boolean updated = mode != nextMode || syncedSlots != nextSlots || !links.equals(nextLinks);
        mode = nextMode;
        syncedSlots = nextSlots;
        links = List.copyOf(nextLinks);
        sourceCursors = new int[links.size()];
        return updated;
    }

    public void addDrops(List<ItemStack> drops, Level level, BlockPos position) {
        if (pendingReturn != null) {
            pendingReturn.what().addDrops(pendingReturn.amount(), drops, level, position);
        }
    }

    public void clearContent() {
        pendingReturn = null;
    }

    private record Identity(BlockPos position, Direction side, int slot) {}
}
