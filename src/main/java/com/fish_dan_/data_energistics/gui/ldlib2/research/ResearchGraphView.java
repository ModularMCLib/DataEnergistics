package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

/** Read-only toolkit viewport with no authoring panels, retaining native pan, zoom and selection. */
final class ResearchGraphView extends GraphView {

    private @Nullable ResourceLocation selectedId;
    private @Nullable ResourceLocation hoveredId;
    private final ObjectSet<ResourceLocation> relatedIds = new ObjectOpenHashSet<>();

    ResearchGraphView() {
        setReadOnly(true);
        header.removeSelf();
        canvas.clearAllChildren();
        canvas.addChild(graphView);
        canvas.layout(layout -> layout.widthPercent(100).heightPercent(100));
        graphView.style(style -> style.backgroundTexture(new ColorRectTexture(ResearchUiParts.SURFACE)));
        graphView.graphViewStyle(style -> style.allowPan(true).allowZoom(true).minScale(0.25F).maxScale(2F)
                .gridLineColor(0).gridAccentColor(0).lodEnabled(false));
    }

    void focus(@Nullable ResourceLocation selected, @Nullable ResourceLocation hovered) {
        selectedId = selected;
        hoveredId = hovered;
        relatedIds.clear();
        if (selected != null) relatedIds.add(selected);
        if (hovered != null) relatedIds.add(hovered);
        if (getGraph() == null) return;
        for (WireModel wire : getGraph().graphModel.getWireModels()) {
            if (wire != null && related(wire)) {
                if (wire.getFromPort().getNodeModel() instanceof ResearchGraphNode from) relatedIds.add(from.researchId());
                if (wire.getToPort().getNodeModel() instanceof ResearchGraphNode to) relatedIds.add(to.researchId());
            }
        }
    }

    boolean related(ResourceLocation id) {
        return relatedIds.contains(id);
    }

    boolean related(WireModel wire) {
        return wire.getFromPort() != null && wire.getToPort() != null &&
                ((wire.getFromPort().getNodeModel() instanceof ResearchGraphNode from && focused(from.researchId())) ||
                        (wire.getToPort().getNodeModel() instanceof ResearchGraphNode to && focused(to.researchId())));
    }

    private boolean focused(ResourceLocation id) {
        return id.equals(selectedId) || id.equals(hoveredId);
    }
}
