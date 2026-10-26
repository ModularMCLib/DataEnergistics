package com.fish_dan_.data_energistics.common.gui.trinity.storage;

import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageView;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityStorageCapacitySegments;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Independent vertical capacity visualization for the Trinity storage hosted window.
 */
final class TrinityStorageCapacityDisplay extends BindableUIElement<TrinityDataCoreStorageView> {

    private static final int BAR_WIDTH = 20;
    private static final int BAR_HEIGHT = TrinityStorageCapacitySegments.BAR_HEIGHT;
    private static final int TEXTURE_SLICE_HEIGHT = 2;

    private static final SpriteTexture ITEM_FILL = fillTexture("item_fill.png");
    private static final SpriteTexture FLUID_FILL = fillTexture("fluid_fill.png");
    private static final SpriteTexture OTHER_FILL = fillTexture("aekey_fill.png");

    private TrinityDataCoreStorageView value = TrinityDataCoreStorageView.EMPTY;

    TrinityStorageCapacityDisplay(String id) {
        setId(id);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(140)
                .top(8)
                .width(BAR_WIDTH)
                .height(BAR_HEIGHT));
        addEventListener(
                UIEvents.HOVER_TOOLTIPS,
                event -> event.hoverTooltips = new HoverTooltips(tooltip(this.value.status()), null, null, null));
        internalSetup();
    }

    @Override
    public TrinityDataCoreStorageView getValue() {
        return this.value;
    }

    @Override
    public TrinityStorageCapacityDisplay setValue(@Nullable TrinityDataCoreStorageView value, boolean notify) {
        TrinityDataCoreStorageView next = value == null ? TrinityDataCoreStorageView.EMPTY : value;
        if (this.value.equals(next)) {
            return this;
        }
        this.value = next;
        if (notify) {
            notifyListeners();
        }
        return this;
    }

    @Override
    protected void onRemoved() {
        for (var dataSource : List.copyOf(getBoundDataSources())) {
            unbindDataSource(dataSource);
        }
        super.onRemoved();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackgroundAdditional(GUIContext guiContext) {
        TrinityStorageCapacitySegments heights = this.value.status().segments();
        int bottom = (int) (getContentY() + BAR_HEIGHT);
        bottom = drawSegment(guiContext, ITEM_FILL, bottom, heights.itemHeight());
        bottom = drawSegment(guiContext, FLUID_FILL, bottom, heights.fluidHeight());
        drawSegment(guiContext, OTHER_FILL, bottom, heights.otherHeight());
    }

    @OnlyIn(Dist.CLIENT)
    private int drawSegment(GUIContext guiContext, SpriteTexture texture, int bottom, int height) {
        int segmentBottom = bottom;
        int remaining = height;
        while (remaining > 0) {
            int sliceHeight = Math.min(TEXTURE_SLICE_HEIGHT, remaining);
            segmentBottom -= sliceHeight;
            texture.draw(
                    guiContext.graphics,
                    guiContext.localMouseX,
                    guiContext.localMouseY,
                    getContentX(),
                    segmentBottom,
                    BAR_WIDTH,
                    sliceHeight,
                    guiContext.partialTick);
            remaining -= sliceHeight;
        }
        return segmentBottom;
    }

    private static List<Component> tooltip(TrinityDataCoreStorageStatus status) {
        List<Component> lines = new ObjectArrayList<>();
        lines.add(Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.amount_exact",
                status.totalAmount(),
                status.unlimited() ?
                        Component.translatable("gui.data_energistics.trinity.unlimited") :
                        Component.literal(status.amountCapacity())));
        lines.add(Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.item_amount",
                status.itemAmount()).withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.fluid_amount",
                status.fluidAmount()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable(
                "screen.data_energistics.trinity_data_core.storage.other_amount",
                status.otherKeyAmount()).withStyle(ChatFormatting.GREEN));
        return lines;
    }

    private static SpriteTexture fillTexture(String name) {
        return SpriteTexture.of("data_energistics:textures/guis/storage/" + name);
    }
}
