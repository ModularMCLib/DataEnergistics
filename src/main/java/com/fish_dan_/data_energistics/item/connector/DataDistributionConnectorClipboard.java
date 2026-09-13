package com.fish_dan_.data_energistics.item.connector;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptiveProviderConnectorBinding;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptiveProviderConnectorMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Stores copied connector links on the connector item until they are pasted into another provider. */
public final class DataDistributionConnectorClipboard {

    private static final String LINKS = "links";
    private static final int MAX_LINKS = 256;

    private DataDistributionConnectorClipboard() {}

    public static Snapshot read(ItemStack stack) {
        CompoundTag tag = stack.get(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR_CLIPBOARD.get());
        if (tag == null || !tag.contains(LINKS, Tag.TAG_LIST)) {
            return new Snapshot(List.of());
        }
        ListTag links = tag.getList(LINKS, Tag.TAG_COMPOUND);
        ObjectArrayList<AdaptiveProviderConnectorBinding> result = new ObjectArrayList<>(Math.min(links.size(), MAX_LINKS));
        for (int index = 0; index < links.size() && index < MAX_LINKS; index++) {
            CompoundTag link = links.getCompound(index);
            int side = link.getByte("side");
            if (side < 0 || side >= Direction.values().length) {
                continue;
            }
            AdaptiveProviderConnectorMode mode;
            try {
                mode = AdaptiveProviderConnectorMode.valueOf(link.getString("mode"));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            result.add(new AdaptiveProviderConnectorBinding(
                    BlockPos.of(link.getLong("pos")), Direction.from3DDataValue(side), mode));
        }
        return new Snapshot(List.copyOf(result));
    }

    public static void write(ItemStack stack, List<AdaptiveProviderConnectorBinding> bindings) {
        if (bindings.size() > MAX_LINKS) {
            throw new IllegalArgumentException("Connector clipboard cannot contain more than " + MAX_LINKS + " links");
        }
        CompoundTag tag = new CompoundTag();
        ListTag links = new ListTag();
        for (AdaptiveProviderConnectorBinding binding : bindings) {
            CompoundTag link = new CompoundTag();
            link.putLong("pos", binding.position().asLong());
            link.putByte("side", (byte) binding.side().get3DDataValue());
            link.putString("mode", binding.mode().name());
            links.add(link);
        }
        tag.put(LINKS, links);
        stack.set(DEDataComponents.DATA_DISTRIBUTION_CONNECTOR_CLIPBOARD.get(), tag);
    }

    public record Snapshot(List<AdaptiveProviderConnectorBinding> bindings) {

        public Snapshot {
            bindings = List.copyOf(bindings);
        }
    }
}
