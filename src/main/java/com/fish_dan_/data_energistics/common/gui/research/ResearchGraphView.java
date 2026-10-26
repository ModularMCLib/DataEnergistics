package com.fish_dan_.data_energistics.common.gui.research;

import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.common.gui.research.ResearchGraphSelection.Viewport;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.DragOffset;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/** Research viewport with canvas-wide left-button panning and pointer-centered zoom. */
final class ResearchGraphView extends GraphView {

    private final ResearchPrerequisitePath prerequisitePath;
    private final ResearchGraphSelection selection;
    private @Nullable ResearchScope viewportScope;
    private @Nullable Viewport pendingViewport;
    private boolean viewportReady;
    private @Nullable ResourceLocation selectedId;
    private @Nullable ResourceLocation hoveredId;
    private final ObjectSet<ResourceLocation> relatedIds = new ObjectOpenHashSet<>();
    private final ObjectSet<ResourceLocation> excludedIds = new ObjectOpenHashSet<>();
    private final ObjectSet<ResourceLocation> winningIds = new ObjectOpenHashSet<>();
    private boolean panned;
    private long pressedAt;

    ResearchGraphView(ResearchPrerequisitePath prerequisitePath, ResearchGraphSelection selection) {
        this.prerequisitePath = prerequisitePath;
        this.selection = selection;
        setReadOnly(true);
        header.removeSelf();
        canvas.clearAllChildren();
        canvas.addChild(graphView);
        canvas.layout(layout -> layout.widthPercent(100).heightPercent(100));
        style(style -> style.backgroundTexture(IGuiTexture.EMPTY).overflowVisible(false)
                .overflowClip(ResearchUiParts.rounded(0xFFFFFFFF)));
        graphView.style(style -> style.backgroundTexture(ResearchUiParts.rounded(ResearchUiParts.SURFACE)));
        graphView.graphViewStyle(style -> style.allowPan(true).allowZoom(true).minScale(0.25F).maxScale(2F)
                .gridLineColor(0).gridAccentColor(0).lodEnabled(false));
        // Capture above the canvas: its own target phase also runs the toolkit's default handlers.
        addEventListener(UIEvents.MOUSE_DOWN, this::beginPan, true);
        addEventListener(UIEvents.MOUSE_WHEEL, this::zoom, true);
        addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            if (event.dragHandler.draggingObject instanceof DragOffset) {
                float x = event.x - event.dragStartX;
                float y = event.y - event.dragStartY;
                panned |= x * x + y * y > 9;
            }
        }, true);
        addEventListener(UIEvents.CLICK, event -> {
            if (event.button == 0 && (panned || event.timeStamp - pressedAt >= 250)) event.stopImmediatePropagation();
        }, true);
        addEventListener(UIEvents.REMOVED, event -> rememberViewport());
    }

    @Override
    public ResearchGraphView loadGraph(@Nullable Graph graph) {
        rememberViewport();
        viewportScope = selection.scope();
        pendingViewport = selection.viewport(viewportScope);
        viewportReady = false;
        super.loadGraph(graph);
        if (pendingViewport != null) restoreViewport(pendingViewport);
        return this;
    }

    @Override
    public void fitGraphChildren() {
        // LDLib2 schedules this after every graph load, including research-state rebuilds.
        Viewport viewport = pendingViewport;
        pendingViewport = null;
        if (viewport == null) super.fitGraphChildren();
        else restoreViewport(viewport);
        viewportReady = true;
        rememberViewport();
    }

    void resetViewport() {
        pendingViewport = null;
        super.fitGraphChildren();
        viewportReady = true;
        rememberViewport();
    }

    private void restoreViewport(Viewport viewport) {
        graphView.setScale(viewport.scale());
        graphView.setOffsetX(viewport.offsetX());
        graphView.setOffsetY(viewport.offsetY());
    }

    void rememberViewport() {
        if (viewportReady && viewportScope != null) {
            selection.viewport(viewportScope, new Viewport(graphView.getOffsetX(), graphView.getOffsetY(), graphView.getScale()));
        }
    }

    @Override
    public void screenTick() {
        super.screenTick();
        rememberViewport();
    }

    private void beginPan(UIEvent event) {
        if (event.button != 0) return;
        panned = false;
        pressedAt = event.timeStamp;
        if (graphView.getGraphViewStyle().allowPan() && graphView.isMouseOverContent(event.x, event.y)) {
            graphView.startDrag(new DragOffset(graphView.getOffsetX(), graphView.getOffsetY()), null);
            event.stopImmediatePropagation();
        }
    }

    @Override
    protected void onGraphViewMouseDown(UIEvent event) {
        // This viewer has no authoring rectangle or context menu; left-button panning is captured above.
        event.stopLaterPropagation();
    }

    private void zoom(UIEvent event) {
        var style = graphView.getGraphViewStyle();
        if (!style.allowZoom() || !graphView.isMouseOverContent(event.x, event.y)) return;
        float scale = graphView.getScale();
        float next = Math.clamp(scale + event.deltaY * 0.1F, style.minScale(), style.maxScale());
        Vector2f mouse = graphView.getLocalMouse(event.x, event.y);
        float delta = 1 / scale - 1 / next;
        graphView.setOffsetX(graphView.getOffsetX() + (mouse.x - graphView.getPositionX()) * delta);
        graphView.setOffsetY(graphView.getOffsetY() + (mouse.y - graphView.getPositionY()) * delta);
        graphView.setScale(next);
        event.stopImmediatePropagation();
    }

    void focus(@Nullable ResourceLocation selected, @Nullable ResourceLocation hovered) {
        selectedId = selected;
        hoveredId = hovered;
        relatedIds.clear();
        if (selected != null) relatedIds.add(selected);
        if (hovered != null) relatedIds.add(hovered);
        var graph = getGraph();
        if (graph == null) return;
        for (WireModel wire : graph.graphModel.getWireModels()) {
            // Toolkit endpoints are nullable while a wire is being constructed; resolve them once at this boundary.
            if (wire == null) continue;
            var fromPort = wire.getFromPort();
            var toPort = wire.getToPort();
            if (fromPort == null || toPort == null ||
                    !(fromPort.getNodeModel() instanceof ResearchGraphNode from) ||
                    !(toPort.getNodeModel() instanceof ResearchGraphNode to))
                continue;
            if (focused(from.researchId()) || focused(to.researchId())) {
                relatedIds.add(from.researchId());
                relatedIds.add(to.researchId());
            }
        }
    }

    boolean related(ResourceLocation id) {
        return relatedIds.contains(id) || prerequisitePath.contains(id);
    }

    void excluded(ObjectSet<ResourceLocation> ids, ObjectSet<ResourceLocation> winners) {
        excludedIds.clear();
        excludedIds.addAll(ids);
        winningIds.clear();
        winningIds.addAll(winners);
    }

    boolean excluded(ResourceLocation id) {
        return excludedIds.contains(id);
    }

    boolean winningRoute(ResourceLocation id) {
        return winningIds.contains(id);
    }

    boolean missingPrerequisite(ResourceLocation id) {
        return prerequisitePath.missingPrerequisite(id);
    }

    int connectionColor(ResearchGraphNode from, ResearchGraphNode to) {
        if (excluded(to.researchId())) return ResearchUiParts.NODE_BLOCKED;
        if (prerequisitePath.missingEdge(from.researchId(), to.researchId())) {
            return ResearchUiParts.NODE_PREREQUISITE;
        }
        if (prerequisitePath.contains(to.researchId())) {
            return to.progress() == ResearchGraphNode.Progress.RUNNING ? ResearchUiParts.NODE_RUNNING : ResearchUiParts.NODE_SECTION;
        }
        return to.connectionColor();
    }

    boolean related(WireModel wire) {
        var fromPort = wire.getFromPort();
        var toPort = wire.getToPort();
        return fromPort != null && toPort != null &&
                ((fromPort.getNodeModel() instanceof ResearchGraphNode from && focused(from.researchId())) ||
                        (toPort.getNodeModel() instanceof ResearchGraphNode to &&
                                (focused(to.researchId()) || prerequisitePath.contains(to.researchId()))));
    }

    private boolean focused(ResourceLocation id) {
        return id.equals(selectedId) || id.equals(hoveredId);
    }
}
