package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.WireElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;

/** Keeps native toolkit routing/hit geometry while applying research-specific connection highlights. */
final class ResearchGraphWire extends WireModel {

    @Override
    public WireElement createElementUI() {
        return new ResearchWireElement(this);
    }

    private static final class ResearchWireElement extends WireElement {

        ResearchWireElement(WireModel model) {
            super(model);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            updatePortPosition();
            if (drawPoints.isEmpty()) return;
            boolean highlighted = graphView instanceof ResearchGraphView view && view.related(getModel());
            int color = highlighted ? 0xFFFFE8A0 : 0xFFF2F2F2;
            DrawerHelper.drawLines(context.graphics, drawPoints, color, color, highlighted ? 3.0F : 1.5F);
        }
    }
}
