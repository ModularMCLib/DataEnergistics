package com.fish_dan_.data_energistics.gui.ldlib2.terminal.storage;

import com.fish_dan_.data_energistics.client.terminal.TerminalViewportLayout;
import com.fish_dan_.data_energistics.client.terminal.storage.TerminalStorageIndex;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalOverlayUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalResourceCell;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.PendingCraftingJobs;
import appeng.client.gui.me.common.PinnedKeys;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Fixed manual templates and AE2's own crafting pins, with no independent crafting-job tracking. */
@NullMarked
public final class TerminalPinnedResourcesUi {

    private final UIElement root;
    private final HolderLookup.Provider registries;
    private final BiConsumer<String, CompoundTag> request;
    private final BiConsumer<TerminalResourceCell, UIEvent> storageClick;
    private final BiConsumer<TerminalResourceCell, UIEvent> storageScroll;
    private final Int2ObjectMap<AEKey> templates = new Int2ObjectLinkedOpenHashMap<>();
    private final ObjectList<TerminalResourceCell> manualCells = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> automaticCells = new ObjectArrayList<>();
    private final ObjectSet<AEKey> visibleKeys = new ObjectOpenHashSet<>();
    private ObjectList<AEKey> automatic = ObjectImmutableList.of();
    private CompoundTag previous = new CompoundTag();
    private int rows;
    private int columns;

    public TerminalPinnedResourcesUi(UIElement root, HolderLookup.Provider registries, BiConsumer<String, CompoundTag> request,
                                     BiConsumer<TerminalResourceCell, UIEvent> storageClick, BiConsumer<TerminalResourceCell, UIEvent> storageScroll) {
        this.root = root;
        this.registries = registries;
        this.request = request;
        this.storageClick = storageClick;
        this.storageScroll = storageScroll;
    }

    public boolean apply(CompoundTag tag) {
        if (previous.equals(tag)) return false;
        previous = tag.copy();
        rows = tag.getInt("rows");
        templates.clear();
        for (Tag value : tag.getList("slots", Tag.TAG_COMPOUND)) {
            CompoundTag slot = (CompoundTag) value;
            AEKey key = AEKey.fromTagGeneric(registries, slot.getCompound("key"));
            if (key != null) templates.put(slot.getInt("slot"), key);
        }
        return true;
    }

    public boolean refreshAutomatic(TerminalStorageIndex index) {
        ObjectList<AEKey> next = new ObjectArrayList<>();
        for (AEKey key : PinnedKeys.getPinnedKeys())
            if (index.entry(key) != null) next.add(key);
        next.sort(Comparator.comparing(key -> Objects.requireNonNull(PinnedKeys.getPinInfo(key)).since));
        if (next.equals(automatic)) return false;
        automatic = new ObjectImmutableList<>(next);
        return true;
    }

    public int rows() {
        return rows;
    }

    public int automaticCount() {
        return automatic.size();
    }

    public boolean changeRows(int button, TerminalViewportLayout viewport) {
        int available = Math.max(0, viewport.maximumPinnedRows() - (automatic.size() + viewport.columns() - 1) / viewport.columns());
        int next = button == 0 ? Math.min(rows + 1, available) : Math.max(0, Math.min(rows, available) - 1);
        if (next == rows || button == 0 && next < rows) return false;
        rows = next;
        CompoundTag data = new CompoundTag();
        data.putInt("rows", rows);
        request.accept("pin_rows", data);
        return true;
    }

    public void layout(TerminalViewportLayout viewport) {
        int automaticRows = Math.min(viewport.pinnedRows(), (automatic.size() + viewport.columns() - 1) / viewport.columns());
        int manualCount = Math.min(rows, viewport.pinnedRows() - automaticRows) * viewport.columns();
        int automaticCount = automaticRows * viewport.columns();
        boolean rebuild = manualCells.size() != manualCount || automaticCells.size() != automaticCount;
        if (rebuild) {
            root.clearAllChildren();
            manualCells.clear();
            automaticCells.clear();
            for (int index = 0; index < manualCount; index++) {
                int slot = index;
                TerminalResourceCell cell = cell("manual-pin", "manual_pin_hint");
                cell.onResourceClick(event -> {
                    var player = Objects.requireNonNull(Minecraft.getInstance().player);
                    if (event.button != 2 && (!player.containerMenu.getCarried().isEmpty() || event.button == 1)) {
                        CompoundTag data = new CompoundTag();
                        data.putInt("index", slot);
                        data.putInt("button", event.button);
                        request.accept("pin", data);
                    } else storageClick.accept(cell, event);
                });
                manualCells.add(cell);
                root.addChild(cell);
            }
            for (int index = 0; index < automaticCount; index++) {
                TerminalResourceCell cell = cell("crafting-pin", "crafting_pin_hint");
                cell.onResourceClick(event -> storageClick.accept(cell, event));
                automaticCells.add(cell);
                root.addChild(cell);
            }
        }
        if (rebuild || columns != viewport.columns()) {
            columns = viewport.columns();
            for (int index = 0; index < manualCells.size(); index++) position(manualCells.get(index), index);
            for (int index = 0; index < automaticCells.size(); index++) position(automaticCells.get(index), manualCount + index);
        }
        root.setVisible(viewport.pinnedRows() > 0);
    }

    public void bind(TerminalStorageIndex index, boolean connected) {
        visibleKeys.clear();
        for (int slot = 0; slot < manualCells.size(); slot++) {
            if (!templates.containsKey(slot)) {
                manualCells.get(slot).entry(null, false);
                continue;
            }
            AEKey key = templates.get(slot);
            var entry = index.entry(key);
            manualCells.get(slot).entry(entry == null ? new GenericStack(key, 0) : entry.stack(), entry != null && entry.craftable());
            visibleKeys.add(key);
        }
        for (int slot = 0; slot < automaticCells.size(); slot++) {
            TerminalResourceCell cell = automaticCells.get(slot);
            if (slot >= automatic.size()) {
                cell.entry(null, false);
                if (cell.isActive()) cell.setActive(false);
                continue;
            }
            AEKey key = automatic.get(slot);
            var entry = index.entry(key);
            cell.entry(entry == null ? null : entry.stack(), entry != null && entry.craftable());
            if (cell.isActive() != connected) cell.setActive(connected);
            if (entry != null) visibleKeys.add(key);
        }
    }

    public ObjectSet<AEKey> visibleKeys() {
        return visibleKeys;
    }

    public ObjectList<TerminalResourceCell> manualCells() {
        return manualCells;
    }

    /** Returns a visible pin for cursor-release and pick-item gestures on the client thread. */
    public @Nullable TerminalResourceCell cellAt(double x, double y) {
        if (!root.isVisible()) return null;
        for (TerminalResourceCell cell : manualCells)
            if (cell.isActive() && TerminalOverlayUi.over(cell, x, y)) return cell;
        for (TerminalResourceCell cell : automaticCells)
            if (cell.isActive() && TerminalOverlayUi.over(cell, x, y)) return cell;
        return null;
    }

    public void removed() {
        // Match MEStorageScreen.removed: AE2 prunes completed crafting pins after the terminal closes.
        for (AEKey key : PinnedKeys.getPinnedKeys()) {
            var info = PinnedKeys.getPinInfo(key);
            if (info != null && info.reason == PinnedKeys.PinReason.CRAFTING && !PendingCraftingJobs.hasPendingJob(key))
                info.canPrune = true;
        }
    }

    private TerminalResourceCell cell(String style, String hint) {
        TerminalResourceCell cell = new TerminalResourceCell();
        cell.storageCell(true);
        cell.addClass(style);
        cell.actionHint(Component.translatable("gui.data_energistics.player_terminal." + hint));
        cell.addEventListener(UIEvents.MOUSE_WHEEL, event -> storageScroll.accept(cell, event));
        return cell;
    }

    private void position(TerminalResourceCell cell, int index) {
        TerminalUiLayout.at(cell, index % columns * TerminalViewportLayout.CELL_SIZE, index / columns * TerminalViewportLayout.CELL_SIZE,
                TerminalViewportLayout.CELL_SIZE, TerminalViewportLayout.CELL_SIZE);
        cell.gridOutline(index < columns, index % columns == columns - 1, false, index % columns == 0);
    }
}
