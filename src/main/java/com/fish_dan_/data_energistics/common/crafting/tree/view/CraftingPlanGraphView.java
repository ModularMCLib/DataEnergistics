package com.fish_dan_.data_energistics.common.crafting.tree.view;

import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Edge;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Material;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Node;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Process;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph.Role;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Immutable, server-safe projection. Folding never mutates the authoritative plan or duplicates a material. */
public final class CraftingPlanGraphView {

    private final CraftingPlanGraph graph;
    private final Int2ObjectMap<Node> sourceNodes = new Int2ObjectAVLTreeMap<>();
    private final Int2IntMap aliases = new Int2IntOpenHashMap();
    private final Int2IntMap embedded = new Int2IntOpenHashMap();
    private final Int2ObjectMap<IntList> outgoing = new Int2ObjectAVLTreeMap<>();
    private final Int2ObjectMap<IntList> reverse = new Int2ObjectAVLTreeMap<>();
    private final ObjectList<ViewEdge> edges;
    private final GraphComponents components;
    private final IntList[] componentChildren;
    private final int root;

    public CraftingPlanGraphView(CraftingPlanGraph graph) {
        this.graph = graph;
        graph.nodes().forEach(node -> sourceNodes.put(node.id(), node));
        Int2ObjectMap<ObjectList<Edge>> incomingEdges = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<ObjectList<Edge>> outgoingEdges = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<IntList> sourceOutgoing = new Int2ObjectAVLTreeMap<>();
        Int2ObjectMap<IntList> displayChildren = new Int2ObjectAVLTreeMap<>();
        for (int id : sourceNodes.keySet()) {
            sourceOutgoing.put(id, new IntArrayList());
        }
        for (Edge edge : graph.edges()) {
            sourceOutgoing.get(edge.source()).add(edge.target());
            incomingEdges.computeIfAbsent(edge.target(), unused -> new ObjectArrayList<>()).add(edge);
            outgoingEdges.computeIfAbsent(edge.source(), unused -> new ObjectArrayList<>()).add(edge);
        }
        GraphComponents sourceComponents = GraphComponents.find(graph, sourceNodes.keySet(), sourceOutgoing);
        for (Node node : sourceNodes.values()) {
            if (!(node instanceof Process process) || !process.cycleIds().isEmpty() || sourceComponents.cyclicComponents().contains(sourceComponents.componentByNode().get(node.id()))) {
                continue;
            }
            ObjectList<Edge> parents = incomingEdges.getOrDefault(node.id(), ObjectList.of());
            ObjectList<Edge> children = outgoingEdges.getOrDefault(node.id(), ObjectList.of());
            if (parents.size() != 1 || parents.getFirst().role() != Role.OUTPUT) {
                continue;
            }
            int materialId = parents.getFirst().source();
            Material material = (Material) sourceNodes.get(materialId);
            if (!material.key().equals(process.primaryOutput()) || outgoingEdges.getOrDefault(materialId, ObjectList.of()).size() != 1 || incomingEdges.getOrDefault(materialId, ObjectList.of()).size() > 1 || children.stream().anyMatch(edge -> incomingEdges.getOrDefault(edge.target(), ObjectList.of()).size() > 1)) {
                continue;
            }
            aliases.put(node.id(), materialId);
            embedded.put(materialId, node.id());
        }
        for (int id : sourceNodes.keySet()) {
            if (!aliases.containsKey(id)) {
                outgoing.put(id, new IntArrayList());
                reverse.put(id, new IntArrayList());
                displayChildren.put(id, new IntArrayList());
            }
        }
        Object2ObjectMap<Connection, IntList> edgeGroups = new Object2ObjectAVLTreeMap<>();
        for (Edge edge : graph.edges()) {
            int source = projectedId(edge.source());
            int target = projectedId(edge.target());
            if (source == target && edge.source() != edge.target() && aliases.containsKey(edge.target())) {
                continue;
            }
            edgeGroups.computeIfAbsent(new Connection(source, target), unused -> new IntArrayList()).add(edge.id());
            outgoing.get(source).add(target);
            reverse.get(target).add(source);
            displayChildren.get(source).add(target);
            // Output/remainder arrows point toward the process, but their material cards still belong to its display.
            if (edge.role() == Role.OUTPUT || edge.role() == Role.REMAINDER) {
                displayChildren.get(target).add(source);
            }
        }
        outgoing.values().forEach(list -> list.sort(IntComparators.NATURAL_COMPARATOR));
        reverse.values().forEach(list -> list.sort(IntComparators.NATURAL_COMPARATOR));
        displayChildren.values().forEach(list -> list.sort(IntComparators.NATURAL_COMPARATOR));
        components = GraphComponents.find(graph, outgoing.keySet(), outgoing);
        componentChildren = new IntList[components.members().size()];
        for (int component = 0; component < componentChildren.length; component++) {
            IntSet children = new IntAVLTreeSet();
            for (int id : components.members().get(component)) {
                for (int child : displayChildren.get(id)) {
                    int target = components.componentByNode().get(child);
                    if (target != component) {
                        children.add(target);
                    }
                }
            }
            componentChildren[component] = IntLists.unmodifiable(new IntArrayList(children));
        }
        ObjectList<ViewEdge> projectedEdges = new ObjectArrayList<>();
        edgeGroups.forEach((connection, ids) -> {
            ids.sort(IntComparators.NATURAL_COMPARATOR);
            int component = components.componentByNode().get(connection.source());
            boolean cyclic = component == components.componentByNode().get(connection.target()) && components.cyclicComponents().contains(component);
            projectedEdges.add(new ViewEdge(connection.source(), connection.target(), ids, cyclic));
        });
        edges = FastUtilCollections.immutableList(projectedEdges);
        root = projectedId(graph.rootId());
    }

    public CraftingPlanGraph graph() {
        return graph;
    }

    /** Maps an original process id to its material card when that process is safely embedded. */
    public int projectedId(int originalId) {
        return aliases.getOrDefault(originalId, originalId);
    }

    /** Admits a stable breadth-first prefix, including the whole component that first reaches the soft budget. */
    public Expansion initialExpansion(int budget) {
        if (budget < 1) {
            throw new IllegalArgumentException("The visible-node budget must be positive");
        }
        IntSet queued = new IntOpenHashSet();
        IntSet admitted = new IntOpenHashSet();
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        int rootComponent = components.componentByNode().get(root);
        queued.add(rootComponent);
        queue.enqueue(rootComponent);
        int count = 0;
        while (!queue.isEmpty() && count < budget) {
            int current = queue.dequeueInt();
            admitted.add(current);
            count += components.members().get(current).size();
            for (int child : childrenOfComponent(current)) {
                if (queued.add(child)) {
                    queue.enqueue(child);
                }
            }
        }
        IntSet collapsed = new IntAVLTreeSet();
        IntSet initiallyHidden = new IntAVLTreeSet();
        for (int component = 0; component < components.members().size(); component++) {
            IntList members = components.members().get(component);
            if (!admitted.contains(component)) {
                initiallyHidden.addAll(members);
            }
            IntList children = childrenOfComponent(component);
            if (children.isEmpty()) {
                continue;
            }
            boolean admittedChild = false;
            for (int child : children) {
                if (admitted.contains(child)) {
                    admittedChild = true;
                    break;
                }
            }
            // Partial frontiers keep their admitted edges; newly revealed non-leaves still expand one level at a time.
            if (!admitted.contains(component) || !admittedChild) {
                collapsed.addAll(members);
            }
        }
        return new Expansion(IntSets.unmodifiable(collapsed), IntSets.unmodifiable(initiallyHidden));
    }

    /** Changes one complete component; manual expansion reveals all direct display children without a budget. */
    public Expansion setCollapsed(Expansion expansion, int nodeId, boolean fold) {
        IntSet result = new IntAVLTreeSet(expansion.collapsed());
        int component = components.componentByNode().get(projectedId(nodeId));
        if (fold) {
            result.addAll(components.members().get(component));
            return new Expansion(IntSets.unmodifiable(result), expansion.initiallyHidden());
        }
        for (int member : components.members().get(component)) result.remove(member);
        IntSet initiallyHidden = new IntAVLTreeSet(expansion.initiallyHidden());
        revealComponent(initiallyHidden, component);
        return new Expansion(IntSets.unmodifiable(result), IntSets.unmodifiable(initiallyHidden));
    }

    /** Iterative recursive folding terminates on cycles and keeps independent expanded paths meaningful. */
    public Expansion recursiveCollapsed(Expansion expansion, int nodeId, boolean fold) {
        IntSet result = new IntAVLTreeSet(expansion.collapsed());
        IntSet initiallyHidden = fold ? expansion.initiallyHidden() : new IntAVLTreeSet(expansion.initiallyHidden());
        IntSet reached = new IntOpenHashSet();
        int start = components.componentByNode().get(projectedId(nodeId));
        IntSet protectedComponents = new IntOpenHashSet();
        if (fold) {
            visible(setCollapsed(expansion, nodeId, true), false).nodes()
                    .forEach(node -> protectedComponents.add(node.componentId()));
            protectedComponents.remove(start);
        }
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        queue.enqueue(start);
        while (!queue.isEmpty()) {
            int component = queue.dequeueInt();
            if (!reached.add(component) || protectedComponents.contains(component)) {
                continue;
            }
            if (fold) {
                result.addAll(components.members().get(component));
            } else {
                for (int member : components.members().get(component)) result.remove(member);
                revealComponent(initiallyHidden, component);
            }
            // Follow dependency arrows, not the reverse display attachment to co-products.
            for (int member : components.members().get(component)) {
                for (int target : outgoing.get(member)) {
                    queue.enqueue(components.componentByNode().get(target));
                }
            }
        }
        return new Expansion(IntSets.unmodifiable(result), fold ? initiallyHidden : IntSets.unmodifiable(initiallyHidden));
    }

    /** Projects the requested expansion state; the initial budget is never reapplied during visibility or layout. */
    public ViewGraph visible(Expansion expansion, boolean missingOnly) {
        IntSet collapsedComponents = new IntOpenHashSet();
        for (int id : expansion.collapsed()) {
            collapsedComponents.add(components.componentByNode().get(id));
        }
        IntSet allowed = missingOnly ? missingExplanation() : outgoing.keySet();
        IntSet visible = new IntAVLTreeSet();
        IntSet reached = new IntOpenHashSet();
        IntSet partialFrontiers = new IntOpenHashSet();
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        queue.enqueue(components.componentByNode().get(root));
        while (!queue.isEmpty()) {
            int component = queue.dequeueInt();
            if (!reached.add(component)) {
                continue;
            }
            IntList members = components.members().get(component);
            if (expansion.initiallyHidden().contains(members.getInt(0))) {
                continue;
            }
            boolean allowedMember = false;
            for (int member : members) {
                if (allowed.contains(member)) {
                    allowedMember = true;
                    break;
                }
            }
            if (!allowedMember) {
                continue;
            }
            visible.addAll(members);
            if (collapsedComponents.contains(component)) {
                continue;
            }
            for (int child : childrenOfComponent(component)) {
                if (expansion.initiallyHidden().contains(components.members().get(child).getInt(0))) {
                    partialFrontiers.add(component);
                } else {
                    queue.enqueue(child);
                }
            }
        }
        ObjectList<ViewNode> nodes = new ObjectArrayList<>();
        for (int id : visible) {
            int component = components.componentByNode().get(id);
            boolean folded = collapsedComponents.contains(component) || partialFrontiers.contains(component);
            nodes.add(new ViewNode(id, sourceNodes.get(id), embedded.containsKey(id) ? embedded.get(id) : null, component,
                    components.cyclicComponents().contains(component), folded, !childrenOfComponent(component).isEmpty()));
        }
        ObjectList<ViewEdge> visibleEdges = edges.stream().filter(edge -> visible.contains(edge.source()) && visible.contains(edge.target()) && (edge.cyclic() || !collapsedComponents.contains(components.componentByNode().get(edge.source()))))
                .collect(ObjectArrayList.toList());
        ObjectList<ObjectList<Integer>> visibleComponents = new ObjectArrayList<>();
        for (IntList group : components.members()) {
            if (!visible.contains(group.getInt(0))) continue;
            ObjectArrayList<Integer> boxed = new ObjectArrayList<>(group.size());
            for (int member : group) boxed.add(member);
            visibleComponents.add(boxed);
        }
        return new ViewGraph(graph, root, nodes, visibleEdges, visibleComponents);
    }

    private void revealComponent(IntSet initiallyHidden, int component) {
        for (int member : components.members().get(component)) initiallyHidden.remove(member);
        for (int child : childrenOfComponent(component)) {
            for (int member : components.members().get(child)) initiallyHidden.remove(member);
        }
    }

    private IntList childrenOfComponent(int component) {
        return componentChildren[component];
    }

    private IntSet missingExplanation() {
        IntSet reachesMissing = new IntOpenHashSet();
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (Node node : sourceNodes.values()) {
            if (node instanceof Material material && (material.missing().signum() > 0 || material.unresolved().signum() > 0)) {
                queue.enqueue(projectedId(node.id()));
            }
        }
        while (!queue.isEmpty()) {
            int id = queue.dequeueInt();
            if (reachesMissing.add(id)) {
                for (int parent : reverse.get(id)) {
                    queue.enqueue(parent);
                }
            }
        }
        // A successful complete plan still has a meaningful target card.
        reachesMissing.add(root);
        IntSet reachable = new IntOpenHashSet();
        queue.enqueue(root);
        while (!queue.isEmpty()) {
            int id = queue.dequeueInt();
            if (reachable.add(id)) {
                for (int child : outgoing.get(id)) {
                    queue.enqueue(child);
                }
            }
        }
        reachesMissing.retainAll(reachable);
        return reachesMissing;
    }

    /**
     * Immutable presentation state owned by this projection. Both sets contain projected IDs in complete components;
     * initial hiding is separate from ordinary folding so a partially admitted parent can retain its visible children.
     * Instances can be shared between the UI and layout worker; edits return a new state and never mutate earlier ones.
     */
    public static final class Expansion {

        private static final Expansion EMPTY = new Expansion(IntSets.emptySet(), IntSets.emptySet());

        private final IntSet collapsed;
        private final IntSet initiallyHidden;

        // Only already frozen, privately owned sets enter here; unchanged state is reused without another wrapper.
        private Expansion(IntSet collapsed, IntSet initiallyHidden) {
            this.collapsed = collapsed;
            this.initiallyHidden = initiallyHidden;
        }

        /** Fully expanded state with no initialization frontier or manual node limit. */
        public static Expansion empty() {
            return EMPTY;
        }

        /** Read-only projected IDs whose outgoing traversal is explicitly folded. */
        public IntSet collapsed() {
            return collapsed;
        }

        /** Read-only projected IDs not yet admitted by initialization or a manual expansion. */
        public IntSet initiallyHidden() {
            return initiallyHidden;
        }
    }

    public record ViewGraph(CraftingPlanGraph source, int rootId, ObjectList<ViewNode> nodes,
                            ObjectList<ViewEdge> edges, ObjectList<ObjectList<Integer>> components) {

        public ViewGraph {
            nodes = FastUtilCollections.immutableList(nodes);
            edges = FastUtilCollections.immutableList(edges);
            components = components.stream().map(FastUtilCollections::immutableList).collect(ObjectArrayList.toList());
        }
    }

    public record ViewNode(int id, Node sourceNode, @Nullable Integer embeddedProcessId,
                           int componentId, boolean cyclic, boolean collapsed, boolean expandable) {}

    public record ViewEdge(int source, int target, IntList originalEdgeIds, boolean cyclic) {

        public ViewEdge {
            originalEdgeIds = IntLists.unmodifiable(new IntArrayList(originalEdgeIds));
        }
    }

    private record Connection(int source, int target) implements Comparable<Connection> {

        @Override
        public int compareTo(Connection other) {
            int sourceOrder = Integer.compare(source, other.source);
            return sourceOrder != 0 ? sourceOrder : Integer.compare(target, other.target);
        }
    }
}
