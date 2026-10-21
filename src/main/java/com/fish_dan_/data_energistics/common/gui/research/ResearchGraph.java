package com.fish_dan_.data_energistics.common.gui.research;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** Presentation-only graph: toolkit snapshots are local to this view and never become research save data. */
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

        private final Object2ObjectMap<String, ResearchGraphNode> snapshotNodes = new Object2ObjectOpenHashMap<>();

        ResearchGraphModel(Graph graph) {
            super(graph);
        }

        void add(ResearchGraphNode node) {
            String discriminator = discriminator(node);
            if (snapshotNodes.putIfAbsent(discriminator, node) != null) {
                throw new IllegalArgumentException("Duplicate research graph node: " + node.researchId());
            }
            node.setGraphModel(this);
            node.onCreateNode();
            addNode(node);
        }

        @Override
        protected String getNodeDiscriminator(AbstractNodeModel node) {
            return node instanceof ResearchGraphNode research ? discriminator(research) : super.getNodeDiscriminator(node);
        }

        @Override
        protected AbstractNodeModel createNodeFromDiscriminator(String type) {
            // The read-only toolkit still snapshots on load. Keep each node's live UI callbacks and
            // presentation binding when restoring that snapshot, rather than creating an unbound model.
            return snapshotNodes.containsKey(type) ? snapshotNodes.get(type) : super.createNodeFromDiscriminator(type);
        }

        private static String discriminator(ResearchGraphNode node) {
            return "data_energistics:research_node/" + node.researchId();
        }

        void connect(PortModel from, PortModel to, boolean mutuallyExclusive) {
            WireModel wire = new ResearchGraphWire(mutuallyExclusive);
            wire.setGraphModel(this);
            wire.setPorts(to, from);
            addWire(wire);
        }
    }
}
