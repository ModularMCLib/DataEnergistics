package com.fish_dan_.data_energistics.common.gui.trinity.storage;

import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageView;
import com.fish_dan_.data_energistics.integration.ae.ae2.gui.resource.AeKeyAmountRow;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.function.IntConsumer;

/**
 * Fixed-pool exact-key viewport used only by the Trinity storage hosted window.
 */
final class TrinityStorageContentsList extends BindableUIElement<TrinityDataCoreStorageView> {

    private static final int VISIBLE_ROW_COUNT = TrinityDataCoreStorageView.VISIBLE_ROW_COUNT;
    private static final int ROW_WIDTH = 112;
    private static final int ROW_HEIGHT = AeKeyAmountRow.HEIGHT;
    private static final int VISIBLE_ENTRY_COUNT = TrinityDataCoreStorageView.PAGE_SIZE;
    private final ObjectList<AeKeyAmountRow> rows = new ObjectArrayList<>(VISIBLE_ENTRY_COUNT);
    private final IntConsumer pageRequest;
    private TrinityDataCoreStorageView value = TrinityDataCoreStorageView.EMPTY;
    private int requestedFirstEntry;

    TrinityStorageContentsList(String id, IntConsumer pageRequest) {
        this.pageRequest = pageRequest;
        setId(id);
        setOverflowVisible(false);
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(3)
                .top(4)
                .width(ROW_WIDTH)
                .height(VISIBLE_ROW_COUNT * ROW_HEIGHT));
        for (int index = 0; index < VISIBLE_ENTRY_COUNT; index++) {
            AeKeyAmountRow row = createRow(id, index);
            this.rows.add(row);
            addChild(row);
        }
        addEventListener(UIEvents.MOUSE_WHEEL, this::onMouseWheel);
        internalSetup();
    }

    @Override
    public TrinityDataCoreStorageView getValue() {
        return this.value;
    }

    @Override
    public TrinityStorageContentsList setValue(@Nullable TrinityDataCoreStorageView value, boolean notify) {
        TrinityDataCoreStorageView next = value == null ? TrinityDataCoreStorageView.EMPTY : value;
        if (this.value.equals(next)) {
            return this;
        }
        this.value = next;
        int previousRequest = this.requestedFirstEntry;
        this.requestedFirstEntry = next.firstEntry();
        if (previousRequest != this.requestedFirstEntry) {
            this.pageRequest.accept(this.requestedFirstEntry);
        }
        refreshRows();
        if (notify) {
            notifyListeners();
        }
        return this;
    }

    @Override
    protected void onRemoved() {
        for (var dataSource : new ObjectArrayList<>(getBoundDataSources())) {
            unbindDataSource(dataSource);
        }
        super.onRemoved();
    }

    private AeKeyAmountRow createRow(String idPrefix, int index) {
        AeKeyAmountRow row = new AeKeyAmountRow();
        row.setId(idPrefix + "_entry_" + index);
        row.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(index * ROW_HEIGHT)
                .width(ROW_WIDTH));
        return row;
    }

    private void onMouseWheel(UIEvent event) {
        int maximum = maxFirstVisibleEntry();
        if (maximum == 0 || event.deltaY == 0.0F) {
            return;
        }
        int direction = event.deltaY > 0.0F ? -1 : 1;
        int requestedEntry = Math.clamp(this.requestedFirstEntry + direction, 0, maximum);
        if (requestedEntry == this.requestedFirstEntry) {
            return;
        }
        this.requestedFirstEntry = requestedEntry;
        this.pageRequest.accept(this.requestedFirstEntry);
        event.stopPropagation();
    }

    private int maxFirstVisibleEntry() {
        return Math.max(0, this.value.status().typeCount() - VISIBLE_ROW_COUNT);
    }

    private void refreshRows() {
        for (int visibleIndex = 0; visibleIndex < this.rows.size(); visibleIndex++) {
            AeKeyAmountRow row = this.rows.get(visibleIndex);
            if (visibleIndex >= this.value.entries().size()) {
                row.clearEntry();
            } else {
                var entry = this.value.entries().get(visibleIndex);
                row.setEntry(entry.key(), entry.amount());
            }
        }
    }
}
