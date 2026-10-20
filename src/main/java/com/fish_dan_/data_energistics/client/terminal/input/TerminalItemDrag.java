package com.fish_dan_.data_energistics.client.terminal.input;

import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalInputDistribution;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/** Client preview and one release request for a native-style physical input distribution gesture. */
@NullMarked
public final class TerminalItemDrag {

    private final HolderLookup.Provider registries;
    private final BiConsumer<String, CompoundTag> request;
    private final Int2ObjectMap<Placement> selected = new Int2ObjectLinkedOpenHashMap<>();
    private ItemStack carried = ItemStack.EMPTY;
    private String workspace = "";
    private int button = -1;

    public TerminalItemDrag(HolderLookup.Provider registries, BiConsumer<String, CompoundTag> request) {
        this.registries = registries;
        this.request = request;
    }

    public void begin(String workspace, int button, ItemStack carried) {
        cancel();
        this.workspace = workspace;
        this.button = button;
        this.carried = carried.copy();
    }

    public boolean drag(int index, ItemStack current, int button) {
        if (this.button != button) return false;
        int maximum = TerminalInputDistribution.maximum(carried);
        if (index >= 0 && selected.size() < carried.getCount() && current.getCount() < maximum &&
                (current.isEmpty() || ItemStack.isSameItemSameComponents(current, carried)))
            selected.putIfAbsent(index, new Placement(current.getCount(), maximum));
        return true;
    }

    public boolean release(int index, int button, boolean toNetwork) {
        if (this.button != button) return false;
        CompoundTag data = new CompoundTag();
        data.putString("workspace", workspace);
        data.putInt("button", button);
        if (selected.size() > 1) {
            data.putIntArray("slots", selected.keySet().toIntArray());
            data.put("carried", carried.save(registries));
            request.accept("slot_drag", data);
        } else if (index >= 0) {
            data.putString("inventory", "inputs");
            data.putInt("index", index);
            data.putBoolean("to_network", toNetwork);
            request.accept("slot", data);
        }
        cancel();
        return true;
    }

    public @Nullable GenericStack preview(int index) {
        if (selected.size() < 2 || !selected.containsKey(index)) return null;
        Placement placement = selected.get(index);
        return new GenericStack(AEItemKey.of(carried), placement.amount() + addition(placement));
    }

    public ItemStack cursor(ItemStack current) {
        if (selected.size() < 2) return current;
        int remaining = carried.getCount();
        for (Placement placement : selected.values()) remaining -= addition(placement);
        return carried.copyWithCount(remaining);
    }

    public void cancel() {
        selected.clear();
        carried = ItemStack.EMPTY;
        button = -1;
    }

    private int addition(Placement placement) {
        return Math.min(placement.maximum() - placement.amount(), button == 0 ? carried.getCount() / selected.size() : 1);
    }

    private record Placement(int amount, int maximum) {}
}
