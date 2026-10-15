package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SDFRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortModelOptions;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.dependency.ModelUpdateVisitor;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodeElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodeTitleElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.NodeDefinitionScope;
import com.lowdragmc.lowdraglib2.utils.TextUtilities;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Native toolkit node carrying only its displayed identity, icon and state. */
final class ResearchGraphNode extends NodeModel {

    private static final int SUMMARY_WIDTH = 112;
    private final ResourceLocation researchId;
    private final IGuiTexture icon;
    private final Consumer<ResourceLocation> selection;
    private final BiConsumer<ResourceLocation, Boolean> hover;
    private final Runnable layoutChanged;
    private Component status = Component.empty();
    private int stateColor = ResearchUiParts.NODE_BORDER;
    private Presentation presentation = Presentation.COLLAPSED;
    private int presentationRevision;
    private int layoutWidth = SUMMARY_WIDTH;
    private int layoutHeight = 48;
    private @Nullable BiConsumer<UIElement, UIElement> content;

    ResearchGraphNode(ResourceLocation id, Component title, ItemStack icon,
                      Consumer<ResourceLocation> selection, BiConsumer<ResourceLocation, Boolean> hover, Runnable layoutChanged) {
        this.researchId = id;
        this.icon = new ItemStackTexture(icon);
        this.selection = selection;
        this.hover = hover;
        this.layoutChanged = layoutChanged;
        setTitle(title);
        setMinWidth(SUMMARY_WIDTH);
        // The toolkit anchors collapsed ports to the title edges; research nodes never expose port editors.
        setCollapsed(true);
    }

    ResourceLocation researchId() {
        return researchId;
    }

    void state(Component status, int color) {
        this.status = status;
        this.stateColor = color;
    }

    void present(Presentation presentation) {
        if (this.presentation == presentation) return;
        this.presentation = presentation;
        presentationRevision++;
    }

    Presentation presentation() {
        return presentation;
    }

    int layoutHeight() {
        return layoutHeight;
    }

    int layoutWidth() {
        return layoutWidth;
    }

    void content(BiConsumer<UIElement, UIElement> content) {
        this.content = content;
        presentationRevision++;
    }

    @Override
    protected void onDefineNode(NodeDefinitionScope<? extends NodeModel> scope) {
        var input = addInputPort("requires", TypeHandles.BOOL, null, null,
                PortModelOptions.NO_EMBEDDED_CONSTANT, null, null);
        input.setTitle(Component.empty());
        input.setPortCapacity(PortCapacity.MULTIPLE);
        var output = addOutputPort("unlocks", TypeHandles.BOOL, null, null, PortModelOptions.NONE);
        output.setTitle(Component.empty());
        output.setPortCapacity(PortCapacity.MULTIPLE);
    }

    @Override
    public IGuiTexture getNodeIcon() {
        return icon;
    }

    @Override
    public GraphElement<?> createElementUI() {
        return new ResearchNodeElement(this);
    }

    enum Presentation {

        COLLAPSED,
        COSTS,
        DETAILS
    }

    private static final class ResearchNodeElement extends NodeElement {

        private final ResearchGraphNode research;
        private final NodeTitleElement title;
        private final UIElement header = new UIElement();
        private final UIElement summary = new UIElement();
        private final Label status = ResearchUiParts.label(Component.empty());
        private final SDFRectTexture border = ResearchUiParts.rounded(0).setStroke(1).setBorderColor(ResearchUiParts.NODE_BORDER);
        private final IGuiTexture divider = new ColorRectTexture(ResearchUiParts.NODE_SECTION);
        private int renderedRevision = -1;
        private final UIElement costs = new UIElement();
        private final UIElement details = new UIElement();
        private int renderedCostWidth;
        private boolean pointerInside;

        ResearchNodeElement(ResearchGraphNode model) {
            super(model);
            this.research = model;
            this.title = new NodeTitleElement(model) {

                @Override
                public void updateUIFromModel(ModelUpdateVisitor visitor) {
                    super.updateUIFromModel(visitor);
                    Style.importantPipeline(getNodeTittle().getStyle(), style -> style.tooltips(new Component[0]));
                }
            };
            addEventListener(UIEvents.CLICK, event -> {
                if (event.button == 0 && event.target.getStructurePath().stream().noneMatch(Button.class::isInstance)) {
                    refreshHover();
                    research.selection.accept(research.researchId);
                }
            });
            addEventListener(UIEvents.MOUSE_ENTER, event -> refreshHover(), true);
            addEventListener(UIEvents.MOUSE_LEAVE, event -> refreshHover(), true);
            addEventListener(UIEvents.TICK, event -> {
                refreshHover();
                status.setText(research.status);
                if (renderedRevision != research.presentationRevision) {
                    renderedRevision = research.presentationRevision;
                    Presentation presentation = research.presentation;
                    ResearchUiParts.show(costs, presentation != Presentation.COLLAPSED);
                    ResearchUiParts.show(details, presentation == Presentation.DETAILS);
                    costs.clearAllChildren();
                    details.clearAllChildren();
                    if (presentation != Presentation.COLLAPSED && research.content != null) research.content.accept(costs, details);
                    resizeCosts();
                }
            });
        }

        private void refreshHover() {
            boolean inside = isSelfOrChildHover();
            if (pointerInside == inside) return;
            pointerInside = inside;
            research.hover.accept(research.researchId, inside);
        }

        @Override
        public boolean wantsDefaultMouseWiring() {
            return false;
        }

        @Override
        protected void buildPartList() {
            parts.add(nodeTittle = title);
        }

        @Override
        protected void buildUI() {
            Style.importantPipeline(getLayout(), layout -> layout.positionType(TaffyPosition.ABSOLUTE));
            layout(layout -> layout.width(research.layoutWidth).heightAuto().paddingAll(0).gapAll(0)
                    .flexDirection(FlexDirection.COLUMN));
            style(style -> style.backgroundTexture(ResearchUiParts.rounded(ResearchUiParts.NODE_BACKGROUND)));
            header.layout(layout -> layout.widthPercent(100).heightAuto().flexDirection(FlexDirection.ROW)
                    .alignItems(AlignItems.STRETCH).gapAll(0).flexShrink(0));
            summary.layout(layout -> layout.width(SUMMARY_WIDTH).heightAuto().paddingAll(3).gapAll(2)
                    .flexDirection(FlexDirection.COLUMN).flexShrink(0));
            summary.style(style -> style.backgroundTexture(ResearchUiParts.rounded(ResearchUiParts.NODE_SECTION)));
            title.getNodeTittle().textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false)
                    .fontSize(10).textColor(0xFFFFFFFF).textShadow(true).textWrap(TextWrap.HOVER_ROLL));
            title.getNodeTittle().layout(layout -> layout.width(78).height(18));
            title.getNodeIcon().layout(layout -> layout.width(16).height(16));
            status.textStyle(style -> style.fontSize(9).textColor(ResearchUiParts.TEXT).textShadow(false));
            summary.addChildren(title, status);
            costs.layout(layout -> layout.width(60).heightAuto().paddingAll(6).gapAll(3)
                    .flexDirection(FlexDirection.COLUMN).flexShrink(0));
            details.layout(layout -> layout.widthPercent(100).heightAuto().paddingAll(6).gapAll(3)
                    .flexDirection(FlexDirection.COLUMN).flexShrink(0));
            header.addChildren(summary, costs);
            addChildren(header, details);
            ResearchUiParts.show(costs, research.presentation != Presentation.COLLAPSED);
            ResearchUiParts.show(details, research.presentation == Presentation.DETAILS);
        }

        /** Measure translated lines at the actual text font; long material names wrap within the upper bound. */
        private void resizeCosts() {
            int width = 0;
            if (research.presentation != Presentation.COLLAPSED) {
                float preferred = preferredCostWidth(costs);
                width = Math.clamp((int) Math.ceil(preferred), 48, 240) + 12;
            }
            if (renderedCostWidth == width) return;
            renderedCostWidth = width;
            int nodeWidth = SUMMARY_WIDTH + width;
            layout(layout -> layout.width(nodeWidth));
            costs.layout(layout -> layout.width(renderedCostWidth));
        }

        private static float preferredCostWidth(UIElement element) {
            if (element instanceof ResearchUiParts.MaterialRow) {
                float labelWidth = 0;
                for (UIElement child : element.getChildren()) {
                    if (child instanceof Label label) labelWidth = Math.max(labelWidth, preferredLabelWidth(label));
                }
                return labelWidth + ResearchUiParts.MATERIAL_ICON_SPACE;
            }
            float width = 0;
            for (UIElement child : element.getChildren()) width = Math.max(width, preferredCostWidth(child));
            return element instanceof Label label ? Math.max(width, preferredLabelWidth(label)) : width;
        }

        private static float preferredLabelWidth(Label label) {
            var textStyle = label.getTextStyle();
            float width = 0;
            for (var line : TextUtilities.computeFormattedLines(label.getFont(),
                    TextUtilities.withFont(label.getText(), textStyle.font()), textStyle.fontSize(), Float.MAX_VALUE)) {
                width = Math.max(width, line.getB());
            }
            return width;
        }

        @Override
        protected void onLayoutChanged() {
            super.onLayoutChanged();
            resizeCosts();
            int width = (int) Math.ceil(getSizeWidth());
            int height = (int) Math.ceil(getSizeHeight());
            if (research.layoutWidth == width && research.layoutHeight == height) return;
            research.layoutWidth = width;
            research.layoutHeight = height;
            research.layoutChanged.run();
        }

        @Override
        public void drawBackgroundOverlay(GUIContext context) {
            if (research.presentation != Presentation.COLLAPSED) {
                context.drawTexture(divider, costs.getPositionX(), header.getPositionY() + 1, 1, header.getSizeHeight() - 1);
            }
            if (research.presentation == Presentation.DETAILS) {
                context.drawTexture(divider, details.getPositionX() + 1, details.getPositionY(), details.getSizeWidth() - 2, 1);
            }
            int color = isSelected() || shouldBeHighlighted() || isSelfOrChildHover() ? ResearchUiParts.NODE_SELECTED : research.stateColor;
            if (border.getBorderColor() != color) border.setBorderColor(color);
            context.drawTexture(border, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }

        @Override
        public boolean shouldBeHighlighted() {
            if (graphView instanceof ResearchGraphView view) {
                return view.related(research.researchId);
            }
            return super.shouldBeHighlighted();
        }
    }
}
