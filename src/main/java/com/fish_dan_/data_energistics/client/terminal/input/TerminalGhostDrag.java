package com.fish_dan_.data_energistics.client.terminal.input;

import appeng.helpers.InventoryAction;

import net.minecraft.nbt.CompoundTag;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/** One client pointer gesture paints each configuration slot once without moving the carried stack. */
@NullMarked
public final class TerminalGhostDrag {

    private final BiConsumer<String, CompoundTag> request;
    private final ObjectSet<Target> visited = new ObjectLinkedOpenHashSet<>();
    private int button = -1;

    public TerminalGhostDrag(BiConsumer<String, CompoundTag> request) {
        this.request = request;
    }

    public void begin(Target target, int button) {
        cancel();
        this.button = button;
        visited.add(target);
    }

    public boolean drag(@Nullable Target target, int button) {
        if (this.button != button) return false;
        if (target != null && visited.add(target)) {
            if (visited.size() == 2) visited.forEach(slot -> apply(slot, true));
            else apply(target, true);
        }
        return true;
    }

    public boolean release(@Nullable Target target, int button) {
        if (this.button != button) return false;
        if (visited.size() == 1 && visited.contains(target)) apply(visited.iterator().next(), false);
        cancel();
        return true;
    }

    public void cancel() {
        button = -1;
        visited.clear();
    }

    private void apply(Target target, boolean dragging) {
        CompoundTag data = new CompoundTag();
        data.putString("inventory", target.inventory());
        data.putInt("index", target.index());
        data.putInt("button", button);
        data.putString("interaction", (button == 0 ? InventoryAction.PICKUP_OR_SET_DOWN :
                dragging ? InventoryAction.PLACE_SINGLE : InventoryAction.SPLIT_OR_PLACE_SINGLE).name());
        request.accept(target.inventory().equals("pins") ? "pin" : "ghost", data);
    }

    public record Target(String inventory, int index) {}
}
