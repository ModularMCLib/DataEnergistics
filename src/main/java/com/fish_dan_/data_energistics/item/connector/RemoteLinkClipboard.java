package com.fish_dan_.data_energistics.item.connector;

import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Stores copied absolute links on the connector until they are pasted into another host of the same type. */
public final class RemoteLinkClipboard {

    private static final String LINKS = "links";
    private static final int MAX_LINKS = 256;

    private RemoteLinkClipboard() {}

    public static Snapshot read(ItemStack stack) {
        CompoundTag tag = stack.get(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR_CLIPBOARD.get());
        if (tag == null || !tag.contains(LINKS, Tag.TAG_LIST)) {
            return new Snapshot("", ConnectorHostType.ADAPTIVE_PROVIDER, List.of());
        }
        ListTag links = tag.getList(LINKS, Tag.TAG_COMPOUND);
        if (links.size() > MAX_LINKS) {
            throw new IllegalArgumentException("Connector clipboard has too many links");
        }
        ObjectArrayList<ConnectorLink> result = new ObjectArrayList<>(links.size());
        for (int index = 0; index < links.size(); index++) {
            CompoundTag link = links.getCompound(index);
            int side = link.getByte("side");
            if (side < 0 || side >= Direction.values().length) {
                throw new IllegalArgumentException("Invalid clipboard target face");
            }
            var mode = ConnectorMode.valueOf(link.getString("mode"));
            result.add(new ConnectorLink(
                    BlockPos.of(link.getLong("pos")), Direction.from3DDataValue(side), mode,
                    link.contains("slot", Tag.TAG_INT) ? link.getInt("slot") : -1));
        }
        var type = tag.contains("type") ? ConnectorHostType.valueOf(tag.getString("type")) : ConnectorHostType.ADAPTIVE_PROVIDER;
        return new Snapshot(tag.getString("dimension"), type, result);
    }

    public static void write(ItemStack stack, RemoteLinkConnectorData selection, List<ConnectorLink> bindings) {
        if (bindings.size() > MAX_LINKS) {
            throw new IllegalArgumentException("Connector clipboard cannot contain more than " + MAX_LINKS + " links");
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", selection.providerDimensionId());
        tag.putString("type", selection.targetType().name());
        ListTag links = new ListTag();
        for (ConnectorLink binding : bindings) {
            CompoundTag link = new CompoundTag();
            link.putLong("pos", binding.position().asLong());
            link.putByte("side", (byte) binding.side().get3DDataValue());
            link.putString("mode", binding.mode().name());
            link.putInt("slot", binding.slot());
            links.add(link);
        }
        tag.put(LINKS, links);
        stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR_CLIPBOARD.get(), tag);
    }

    public record Snapshot(String dimensionId, ConnectorHostType type, List<ConnectorLink> bindings) {

        public Snapshot {
            bindings = List.copyOf(bindings);
        }
    }
}
