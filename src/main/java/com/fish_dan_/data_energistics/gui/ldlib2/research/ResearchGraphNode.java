package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortModelOptions;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodeElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.NodeDefinitionScope;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** Native toolkit node carrying only its displayed identity, icon and state. */
final class ResearchGraphNode extends NodeModel {

    private final ResourceLocation researchId;
    private final IGuiTexture icon;
    private final Consumer<ResourceLocation> selection;
    private final Consumer<@Nullable ResourceLocation> hover;
    private Component status = Component.empty();
    private int stateColor = 0xFF7A8097;

    ResearchGraphNode(ResourceLocation id, Component title, ItemStack icon,
                      Consumer<ResourceLocation> selection, Consumer<@Nullable ResourceLocation> hover) {
        this.researchId = id;
        this.icon = new ItemStackTexture(icon);
        this.selection = selection;
        this.hover = hover;
        setTitle(title);
        setMinWidth(106);
    }

    ResourceLocation researchId() {
        return researchId;
    }

    void state(Component status, int color) {
        this.status = status;
        this.stateColor = color;
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

    private static final class ResearchNodeElement extends NodeElement {

        private final ResearchGraphNode research;
        private final Label status = ResearchUiParts.label(Component.empty());
        private int renderedColor;

        ResearchNodeElement(ResearchGraphNode model) {
            super(model);
            this.research = model;
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 0) research.selection.accept(research.researchId);
            });
            addEventListener(UIEvents.MOUSE_ENTER, event -> research.hover.accept(research.researchId));
            addEventListener(UIEvents.MOUSE_LEAVE, event -> research.hover.accept(null));
            addEventListener(UIEvents.TICK, event -> {
                status.setText(research.status);
                if (renderedColor != research.stateColor) {
                    renderedColor = research.stateColor;
                    style(style -> style.backgroundTexture(new ColorRectTexture(renderedColor)));
                }
            });
        }

        @Override
        protected void buildUI() {
            super.buildUI();
            layout(layout -> layout.width(112).paddingAll(3).gapAll(2));
            if (nodeTittle != null) {
                nodeTittle.getNodeTittle().textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true)
                        .fontSize(9).textColor(0xFFF2F2F2).textShadow(false).textWrap(TextWrap.WRAP));
                nodeTittle.getNodeTittle().layout(layout -> layout.width(78));
                nodeTittle.getNodeIcon().layout(layout -> layout.width(16).height(16));
            }
            status.textStyle(style -> style.fontSize(8).textColor(0xFFF2F2F2).textShadow(false));
            addChild(status);
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
