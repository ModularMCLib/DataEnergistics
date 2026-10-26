package com.fish_dan_.data_energistics.common.gui.research;

import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.WireElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodeElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;

import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/** Orthogonal research connections with rounded corners and matching toolkit hit geometry. */
final class ResearchGraphWire extends WireModel {

    @Override
    public WireElement createElementUI() {
        return new ResearchWireElement(this);
    }

    private static final class ResearchWireElement extends WireElement {

        private ObjectList<Vector2f> strokeNormals = ObjectLists.emptyList();

        ResearchWireElement(WireModel model) {
            super(model);
        }

        @Override
        protected void updatePortPosition() {
            var parent = getParent();
            var view = graphView;
            if (view == null || parent == null) return;
            Vector2f fromWorld = endpointWorldPosition(view, getModel().getFromPort(), true);
            Vector2f toWorld = endpointWorldPosition(view, getModel().getToPort(), false);
            // The toolkit may visit a wire before both node elements have been built.
            if (fromWorld == null || toWorld == null) return;
            Vector2f nextFrom = parent.worldToLocalLayoutOffset(fromWorld);
            Vector2f nextTo = parent.worldToLocalLayoutOffset(toWorld);
            Vector2f parentOffset = new Vector2f(parent.getPositionX(), parent.getPositionY());
            if (!geometryDirty && from.equals(nextFrom) && to.equals(nextTo) && builtParentOffset.equals(parentOffset)) return;
            from.set(nextFrom);
            to.set(nextTo);
            builtParentOffset.set(parentOffset);

            ObjectList<Vector2f> route = new ObjectArrayList<>(4);
            route.add(new Vector2f(from).add(parentOffset));
            if (from.y != to.y) {
                float middleX = (from.x + to.x) / 2;
                route.add(new Vector2f(middleX, from.y).add(parentOffset));
                route.add(new Vector2f(middleX, to.y).add(parentOffset));
            }
            route.add(new Vector2f(to).add(parentOffset));
            ObjectList<Vector2f> rounded = new ObjectArrayList<>();
            for (Vector2f point : roundCorners(route, 6, 8)) {
                if (rounded.isEmpty() || !rounded.getLast().equals(point)) rounded.add(point);
            }
            drawPoints = rounded;
            rawPoints = drawPoints;
            strokeNormals = new ObjectArrayList<>(rounded.size());
            for (int i = 0; i < rounded.size(); i++) {
                Vector2f previous = rounded.get(Math.max(0, i - 1));
                Vector2f next = rounded.get(Math.min(rounded.size() - 1, i + 1));
                Vector2f tangent = new Vector2f(next).sub(previous).normalize();
                strokeNormals.add(tangent.perpendicular().negate());
            }

            float left = Math.min(from.x, to.x) - 2;
            float top = Math.min(from.y, to.y) - 2;
            float width = Math.abs(to.x - from.x) + 4;
            float height = Math.abs(to.y - from.y) + 4;
            Style.importantPipeline(getLayout(), layout -> layout.left(left).top(top).width(width).height(height));
            geometryDirty = false;
        }

        private @Nullable Vector2f endpointWorldPosition(GraphView view, @Nullable PortModel port, boolean output) {
            if (port == null || !(view.getModelElement(port.getNodeModel()) instanceof NodeElement node)) return null;
            var title = node.getNodeTittle();
            if (title == null) return null;
            // Expanded material panels belong to the node; connections must leave its outside edge.
            return node.getWorldMouse(node.getPositionX() + (output ? node.getSizeWidth() : 0),
                    title.getPositionY() + title.getSizeHeight() / 2);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            updatePortPosition();
            if (drawPoints.isEmpty()) return;
            boolean highlighted = graphView instanceof ResearchGraphView view && view.related(getModel());
            drawStroke(context, connectionColor(), highlighted ? 3.0F : 1.5F);
        }

        private int connectionColor() {
            PortModel fromPort = getModel().getFromPort();
            PortModel toPort = getModel().getToPort();
            if (fromPort == null || toPort == null ||
                    !(fromPort.getNodeModel() instanceof ResearchGraphNode fromNode) ||
                    !(toPort.getNodeModel() instanceof ResearchGraphNode toNode)) {
                return ResearchUiParts.NODE_BORDER;
            }
            return graphView instanceof ResearchGraphView view ?
                    view.connectionColor(fromNode, toNode) : toNode.connectionColor();
        }

        private void drawStroke(GUIContext context, int color, float halfWidth) {
            var matrix = context.pose.last().pose();
            var vertices = context.graphics.bufferSource().getBuffer(RenderType.guiOverlay());
            RenderSystem.disableDepthTest();
            // Adjacent segments share their edge vertices so rounded corners have no gaps.
            for (int i = 1; i < drawPoints.size(); i++) {
                Vector2f start = drawPoints.get(i - 1);
                Vector2f end = drawPoints.get(i);
                Vector2f startNormal = strokeNormals.get(i - 1);
                Vector2f endNormal = strokeNormals.get(i);
                float startX = startNormal.x * halfWidth;
                float startY = startNormal.y * halfWidth;
                float endX = endNormal.x * halfWidth;
                float endY = endNormal.y * halfWidth;
                vertices.addVertex(matrix, start.x - startX, start.y - startY, 0).setColor(color);
                vertices.addVertex(matrix, start.x + startX, start.y + startY, 0).setColor(color);
                vertices.addVertex(matrix, end.x + endX, end.y + endY, 0).setColor(color);
                vertices.addVertex(matrix, end.x - endX, end.y - endY, 0).setColor(color);
            }
        }
    }
}
