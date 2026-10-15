package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

/** Size-driven outline shared by the graph viewport and its native target slot. */
final class ResearchGraphFrame extends UIElement {

    private static final int FRAME_INSET = 2;
    private static final int CANVAS_INSET = 4;
    private static final int TARGET_SIZE = 26;
    private final ResearchGraphView graph;
    private final ItemSlot target;
    private final Label empty;
    private final UIElement innerFrame = new UIElement();
    private final UIElement targetFrame = new UIElement();

    ResearchGraphFrame(ResearchGraphView graph, ItemSlot target, Label empty) {
        this.graph = graph;
        this.target = target;
        this.empty = empty;
        setId("research_station_graph_frame");
        style(style -> style.backgroundTexture(ResearchUiParts.roundedFrame(ResearchUiParts.SURFACE, ResearchUiParts.NODE_BORDER)));
        innerFrame.style(style -> style.backgroundTexture(ResearchUiParts.rounded(0).setRadius(ResearchUiParts.CORNER_RADIUS - FRAME_INSET)
                .setStroke(1).setBorderColor(ResearchUiParts.NODE_BORDER)).zIndex(1));
        innerFrame.setAllowHitTest(false);
        targetFrame.style(style -> style.backgroundTexture(ResearchUiParts.roundedFrame(ResearchUiParts.SURFACE, ResearchUiParts.NODE_BORDER))
                .zIndex(1));
        targetFrame.setAllowHitTest(false);
        target.style(style -> style.backgroundTexture(ResearchUiParts.rounded(0).setRadius(ResearchUiParts.CORNER_RADIUS - FRAME_INSET)
                .setStroke(1).setBorderColor(ResearchUiParts.NODE_BORDER)).zIndex(2));
        empty.style(style -> style.zIndex(1));
        addChildren(graph, empty, innerFrame, targetFrame, target);
    }

    @Override
    protected void onLayoutChanged() {
        super.onLayoutChanged();
        float width = getSizeWidth();
        float height = getSizeHeight();
        float shortest = Math.min(width, height);
        float frameInset = Math.min(FRAME_INSET, shortest / 4);
        float canvasInset = Math.min(CANVAS_INSET, shortest / 4);
        ResearchUiParts.at(graph, canvasInset, canvasInset, width - canvasInset * 2, height - canvasInset * 2);
        ResearchUiParts.at(innerFrame, frameInset, frameInset, width - frameInset * 2, height - frameInset * 2);
        float targetSize = Math.min(TARGET_SIZE, shortest);
        float contentSize = targetSize - frameInset * 2;
        ResearchUiParts.at(targetFrame, 0, 0, targetSize, targetSize);
        ResearchUiParts.at(target, frameInset, frameInset, contentSize, contentSize);
        target.layout(layout -> layout.paddingAll(contentSize * 3 / 22));
        ResearchUiParts.at(empty, canvasInset + 3, Math.max(canvasInset, height - canvasInset - 30),
                Math.max(0, width - (canvasInset + 3) * 2), Math.min(30, height - canvasInset * 2));
    }
}
