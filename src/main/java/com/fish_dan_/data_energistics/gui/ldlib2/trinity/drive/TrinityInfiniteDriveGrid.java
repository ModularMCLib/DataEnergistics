package com.fish_dan_.data_energistics.gui.ldlib2.trinity.drive;

import com.fish_dan_.data_energistics.common.trinity.drive.TrinityInfiniteDriveInventory;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Scrollable native LDLib2 grid for the Trinity Data Core's fixed-layout infinite-drive inventory.
 *
 * <p>
 * The grid always creates eighty vanilla slots so that client and server menu layouts cannot diverge. Cells beyond
 * the current capacity-derived usable prefix are hidden and inactive, rather than being client-only decoration.
 * </p>
 */
public final class TrinityInfiniteDriveGrid extends UIElement {

    public static final String CONTENT_ID = "trinity_data_core_infinite_drive_content";
    public static final String SCROLLER_ID = "trinity_data_core_infinite_drive_scrollbar";

    private static final String SLOT_ID_PREFIX = "trinity_data_core_infinite_drive_slot_";
    private static final int SLOT_SIZE = 18;
    private static final int VISIBLE_ROW_COUNT = 4;
    private static final int VIEW_LEFT = 167;
    private static final int VIEW_TOP = 5;
    private static final int VIEW_WIDTH = TrinityInfiniteDriveInventory.COLUMN_COUNT * SLOT_SIZE;
    private static final int VIEW_HEIGHT = VISIBLE_ROW_COUNT * SLOT_SIZE;
    private static final int TOTAL_ROW_COUNT = TrinityInfiniteDriveInventory.MAXIMUM_SLOT_COUNT /
            TrinityInfiniteDriveInventory.COLUMN_COUNT;
    private static final SpriteTexture[] SLOT_TEXTURES = createSlotTextures();

    private final UIElement cellContent = new UIElement();
    private final Scroller.Vertical scrollbar;
    private final IntSupplier availableSlotCount;
    private final List<ItemSlot> itemSlots = new ObjectArrayList<>(TrinityInfiniteDriveInventory.MAXIMUM_SLOT_COUNT);
    private int activeSlotCount = -1;
    private int maximumFirstRow;
    private int firstVisibleRow;

    private TrinityInfiniteDriveGrid(Container inventory, IntSupplier availableSlotCount, Scroller.Vertical scrollbar) {
        this.scrollbar = scrollbar;
        this.availableSlotCount = availableSlotCount;

        setId(CONTENT_ID);
        setOverflowVisible(false);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(VIEW_LEFT)
                .top(VIEW_TOP)
                .width(VIEW_WIDTH)
                .height(VIEW_HEIGHT));

        this.cellContent.setId(CONTENT_ID + "_rows");
        this.cellContent.setOverflowVisible(false);
        this.cellContent.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(TrinityInfiniteDriveInventory.COLUMN_COUNT * SLOT_SIZE)
                .height(TOTAL_ROW_COUNT * SLOT_SIZE));
        addChild(this.cellContent);

        for (int index = 0; index < TrinityInfiniteDriveInventory.MAXIMUM_SLOT_COUNT; index++) {
            addDriveSlot(inventory, index);
        }

        scrollbar.layout(layout -> layout.top(1));
        configureScrollbar();
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY != 0 && this.maximumFirstRow > 0) {
                float delta = this.scrollbar.getScrollerStyle().scrollDelta();
                this.scrollbar.scrollValue(event.deltaY > 0 ? -delta : delta);
                event.stopPropagation();
            }
        });
    }

    /** Creates the grid after the editor-authored scrollbar is resolved from the main UI tree. */
    public static TrinityInfiniteDriveGrid create(Container inventory,
                                                  IntSupplier availableSlotCount,
                                                  Scroller.Vertical scrollbar) {
        return new TrinityInfiniteDriveGrid(inventory, availableSlotCount, scrollbar);
    }

    /** Returns the exact native slots in their fixed container order. */
    public List<ItemSlot> itemSlots() {
        return List.copyOf(this.itemSlots);
    }

    @Override
    public void screenTick() {
        configureScrollbar();
        super.screenTick();
    }

    private void addDriveSlot(Container inventory, int index) {
        int column = index % TrinityInfiniteDriveInventory.COLUMN_COUNT;
        int row = index / TrinityInfiniteDriveInventory.COLUMN_COUNT;
        Slot slot = new InfiniteDriveSlot(inventory, index, this.availableSlotCount);
        ItemSlot itemSlot = new InfiniteDriveItemSlot(slot, index, this.availableSlotCount);
        itemSlot.setId(SLOT_ID_PREFIX + index);
        itemSlot.getStyle().backgroundTexture(SLOT_TEXTURES[column]);
        itemSlot.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(column * SLOT_SIZE)
                .top(row * SLOT_SIZE)
                .width(SLOT_SIZE)
                .height(SLOT_SIZE));
        this.cellContent.addChild(itemSlot);
        this.itemSlots.add(itemSlot);
    }

    private void configureScrollbar() {
        int nextActiveSlotCount = Math.clamp(
                this.availableSlotCount.getAsInt(),
                0,
                TrinityInfiniteDriveInventory.MAXIMUM_SLOT_COUNT);
        if (nextActiveSlotCount == this.activeSlotCount) {
            return;
        }
        this.activeSlotCount = nextActiveSlotCount;
        int activeRowCount = Math.ceilDiv(this.activeSlotCount, TrinityInfiniteDriveInventory.COLUMN_COUNT);
        this.maximumFirstRow = Math.max(0, activeRowCount - VISIBLE_ROW_COUNT);
        this.firstVisibleRow = Math.min(this.firstVisibleRow, this.maximumFirstRow);

        boolean scrollable = this.maximumFirstRow > 0;
        this.scrollbar.headButton.setDisplay(false);
        this.scrollbar.tailButton.setDisplay(false);
        this.scrollbar.layout(layout -> layout
                .gapRow(0)
                .gapColumn(0));
        this.scrollbar.setRange(0.0F, 1.0F);
        this.scrollbar.scrollerStyle(style -> style.scrollDelta(
                scrollable ? 1.0F / this.maximumFirstRow : 1.0F));
        this.scrollbar.setScrollBarSize(100.0F * Math.min(1.0F,
                (float) (VISIBLE_ROW_COUNT * SLOT_SIZE) / Math.max(1, activeRowCount * SLOT_SIZE)));
        this.scrollbar.setDisplay(true);
        this.scrollbar.setActive(scrollable);
        this.scrollbar.selfAndAllChildren().forEach(element -> element.setAllowHitTest(scrollable));
        this.scrollbar.setOnValueChanged(this::scrollToNormalizedRow);
        scrollToNormalizedRow(scrollable ? (float) this.firstVisibleRow / this.maximumFirstRow : 0.0F);
    }

    private void scrollToNormalizedRow(float normalizedValue) {
        this.firstVisibleRow = Math.round(Math.clamp(normalizedValue, 0.0F, 1.0F) * this.maximumFirstRow);
        this.cellContent.layout(layout -> layout.top(-this.firstVisibleRow * SLOT_SIZE));
        if (this.maximumFirstRow > 0) {
            this.scrollbar.setNormalizedValue((float) this.firstVisibleRow / this.maximumFirstRow, false);
        }
    }

    private static SpriteTexture[] createSlotTextures() {
        SpriteTexture[] textures = new SpriteTexture[TrinityInfiniteDriveInventory.COLUMN_COUNT];
        for (int column = 0; column < textures.length; column++) {
            textures[column] = SpriteTexture
                    .of("data_energistics:textures/guis/trinity_data_core/trinity_drive_slot.png")
                    .setSprite(column * SLOT_SIZE, 0, SLOT_SIZE, SLOT_SIZE);
        }
        return textures;
    }

    /** Native slot boundary that rejects inactive cells and ordinary storage cells on the authoritative menu. */
    private static final class InfiniteDriveSlot extends Slot {

        private final int driveIndex;
        private final IntSupplier availableSlotCount;

        private InfiniteDriveSlot(Container inventory, int driveIndex, IntSupplier availableSlotCount) {
            super(inventory, driveIndex, 0, 0);
            this.driveIndex = driveIndex;
            this.availableSlotCount = availableSlotCount;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && TrinityInfiniteDriveInventory.accepts(stack) && super.mayPlace(stack);
        }

        @Override
        public boolean isActive() {
            return this.driveIndex < Math.clamp(
                    this.availableSlotCount.getAsInt(),
                    0,
                    TrinityInfiniteDriveInventory.MAXIMUM_SLOT_COUNT);
        }
    }

    /** Keeps a hidden or newly activated cell's rendering and hit testing aligned with its authoritative slot state. */
    private static final class InfiniteDriveItemSlot extends ItemSlot {

        private final int driveIndex;
        private final IntSupplier availableSlotCount;

        private InfiniteDriveItemSlot(Slot slot, int driveIndex, IntSupplier availableSlotCount) {
            super(slot);
            this.driveIndex = driveIndex;
            this.availableSlotCount = availableSlotCount;
        }

        @Override
        public void screenTick() {
            boolean active = this.driveIndex < Math.clamp(
                    this.availableSlotCount.getAsInt(),
                    0,
                    TrinityInfiniteDriveInventory.MAXIMUM_SLOT_COUNT);
            setVisible(active);
            setAllowHitTest(active);
            super.screenTick();
        }

        @Override
        public boolean isIntersectWithPoint(double localX, double localY) {
            return getSlot().isActive() && super.isIntersectWithPoint(localX, localY);
        }
    }
}
