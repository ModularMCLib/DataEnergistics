package com.fish_dan_.data_energistics.gui.ldlib2.research;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** Presentation-only graph: models are constructed directly and never serialized into research data. */
final class ResearchGraph extends Graph {

    @Override
    protected ResearchGraphModel createGraphModel() {
        return new ResearchGraphModel(this);
    }

    @Override
    public ObjectList<Class<? extends Node>> getSupportNodes() {
        return ObjectLists.emptyList();
    }

    ResearchGraphModel models() {
        return (ResearchGraphModel) graphModel;
    }

    static final class ResearchGraphModel extends CustomGraphModelImpl {

        ResearchGraphModel(Graph graph) {
            super(graph);
        }

        void add(ResearchGraphNode node) {
            node.setGraphModel(this);
            node.onCreateNode();
            addNode(node);
        }

        void connect(PortModel from, PortModel to) {
            WireModel wire = new ResearchGraphWire();
            wire.setGraphModel(this);
            wire.setPorts(to, from);
            addWire(wire);
        }
    }
}
