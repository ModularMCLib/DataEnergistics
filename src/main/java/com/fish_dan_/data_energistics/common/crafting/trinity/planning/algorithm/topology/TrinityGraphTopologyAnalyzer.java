package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Partitions the immutable AE key hypergraph with Tarjan and builds its condensation DAG.
 * <p>
 * Stable Tarjan implementation over input-to-output edges induced by every bound hypertransition.
 */
public final class TrinityGraphTopologyAnalyzer {

    /**
     * @return stateless deterministic analyzer
     */
    public static TrinityGraphTopologyAnalyzer create() {
        return new TrinityGraphTopologyAnalyzer();
    }

    /**
     * @param snapshot   graph key order and revision
     * @param variants   complete bound transition set for the snapshot
     * @param maxSccKeys configured per-component key limit
     * @return topology or {@code SCC_KEY_LIMIT}
     */
    public TrinityAlgorithmResult<TrinityCraftingTopology> analyze(
                                                                   TrinityCraftingGraphSnapshot snapshot,
                                                                   List<TrinityPatternVariant> variants,
                                                                   int maxSccKeys) {
        return analyze(snapshot, variants, maxSccKeys, TrinityPlanningControl.unbounded());
    }

    /**
     * Analyzes topology while observing the request-wide cancellation and deadline boundary.
     */
    public TrinityAlgorithmResult<TrinityCraftingTopology> analyze(
                                                                   TrinityCraftingGraphSnapshot snapshot,
                                                                   List<TrinityPatternVariant> variants,
                                                                   int maxSccKeys,
                                                                   TrinityPlanningControl control) {
        if (snapshot == null || variants == null || maxSccKeys <= 0 || control == null) {
            throw new IllegalArgumentException(
                    "A Trinity topology analysis requires complete inputs and a positive SCC key limit");
        }

        StopState initialState = stopState(control);
        if (initialState != StopState.RUNNING) {
            return stopped(initialState);
        }

        Graph graph = Graph.create(snapshot, variants);
        if (graph.keys().isEmpty()) {
            throw new IllegalArgumentException("A Trinity topology requires at least one graph key");
        }
        TarjanState tarjan = new TarjanState(graph.adjacency());
        StopState traversalState = tarjan.traverse(control);
        if (traversalState != StopState.RUNNING) {
            return stopped(traversalState);
        }
        ObjectArrayList<IntList> rawComponents = tarjan.components();
        rawComponents.sort(Comparator.comparingInt(component -> {
            int minimum = Integer.MAX_VALUE;
            for (int index : component) {
                minimum = Math.min(minimum, index);
            }
            return minimum;
        }));

        for (IntList component : rawComponents) {
            if (component.size() > maxSccKeys) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.SCC_KEY_LIMIT,
                        Component.translatable("gui.data_energistics.trinity_planning.diagnostic.scc_key_limit"),
                        Map.of(
                                "limit", Integer.toString(maxSccKeys),
                                "required", Integer.toString(component.size()))));
            }
        }
        return TrinityAlgorithmResult.success(buildTopology(graph, variants, rawComponents));
    }

    private static TrinityCraftingTopology buildTopology(
                                                         Graph graph,
                                                         List<TrinityPatternVariant> variants,
                                                         List<IntList> rawComponents) {
        int[] componentByNode = new int[graph.keys().size()];
        Arrays.fill(componentByNode, -1);
        for (int componentIndex = 0; componentIndex < rawComponents.size(); componentIndex++) {
            for (int node : rawComponents.get(componentIndex)) {
                componentByNode[node] = componentIndex;
            }
        }

        ObjectArrayList<IntSet> predecessors = new ObjectArrayList<>(rawComponents.size());
        ObjectArrayList<IntSet> successors = new ObjectArrayList<>(rawComponents.size());
        for (int index = 0; index < rawComponents.size(); index++) {
            predecessors.add(new IntLinkedOpenHashSet());
            successors.add(new IntLinkedOpenHashSet());
        }
        boolean[] selfEdges = new boolean[rawComponents.size()];
        for (int input = 0; input < graph.adjacency().size(); input++) {
            int inputComponent = componentByNode[input];
            for (int output : graph.adjacency().get(input)) {
                int outputComponent = componentByNode[output];
                if (inputComponent == outputComponent) {
                    if (input == output) {
                        selfEdges[inputComponent] = true;
                    }
                } else {
                    successors.get(inputComponent).add(outputComponent);
                    predecessors.get(outputComponent).add(inputComponent);
                }
            }
        }

        ObjectArrayList<List<TrinityPatternVariant>> cycleVariants = new ObjectArrayList<>(rawComponents.size());
        ObjectArrayList<List<TrinityPatternVariant>> outputVariants = new ObjectArrayList<>(rawComponents.size());
        for (int index = 0; index < rawComponents.size(); index++) {
            cycleVariants.add(new ObjectArrayList<>());
            outputVariants.add(new ObjectArrayList<>());
        }
        for (TrinityPatternVariant variant : variants) {
            IntSet inputComponents = new IntLinkedOpenHashSet();
            IntSet outputComponents = new IntLinkedOpenHashSet();
            variant.inputs().keySet().forEach(key -> inputComponents.add(
                    componentByNode[graph.indexByKey().getInt(key)]));
            variant.outputs().keySet().forEach(key -> outputComponents.add(
                    componentByNode[graph.indexByKey().getInt(key)]));
            for (int outputComponent : outputComponents) {
                outputVariants.get(outputComponent).add(variant);
                if (inputComponents.contains(outputComponent)) {
                    cycleVariants.get(outputComponent).add(variant);
                }
            }
        }

        ObjectArrayList<TrinityStronglyConnectedComponent> components = new ObjectArrayList<>(rawComponents.size());
        Object2IntLinkedOpenHashMap<AEKey> mapping = new Object2IntLinkedOpenHashMap<>();
        for (int componentIndex = 0; componentIndex < rawComponents.size(); componentIndex++) {
            IntArrayList nodes = new IntArrayList(rawComponents.get(componentIndex));
            IntArrays.quickSort(nodes.elements(), 0, nodes.size());
            List<AEKey> keys = nodes.intStream().mapToObj(graph.keys()::get).toList();
            for (AEKey key : keys) {
                mapping.put(key, componentIndex);
            }
            cycleVariants.get(componentIndex).sort(Comparator.naturalOrder());
            outputVariants.get(componentIndex).sort(Comparator.naturalOrder());
            components.add(new TrinityStronglyConnectedComponent(
                    componentIndex,
                    keys,
                    nodes.size() > 1 || selfEdges[componentIndex],
                    cycleVariants.get(componentIndex),
                    IntLists.unmodifiable(new IntArrayList(predecessors.get(componentIndex))),
                    IntLists.unmodifiable(new IntArrayList(successors.get(componentIndex)))));
        }
        Int2ObjectLinkedOpenHashMap<List<TrinityPatternVariant>> variantsByOutputComponent = new Int2ObjectLinkedOpenHashMap<>();
        for (int componentIndex = 0; componentIndex < outputVariants.size(); componentIndex++) {
            variantsByOutputComponent.put(componentIndex, List.copyOf(outputVariants.get(componentIndex)));
        }
        Object2ObjectLinkedOpenHashMap<AEKey, List<TrinityPatternVariant>> variantsByOutputKey = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, ObjectArrayList<TrinityPatternVariant>> producerLists = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            variant.outputs().keySet().forEach(key -> producerLists
                    .computeIfAbsent(key, ignored -> new ObjectArrayList<>())
                    .add(variant));
        }
        for (AEKey key : graph.keys()) {
            ObjectArrayList<TrinityPatternVariant> producers = producerLists.get(key);
            if (producers != null) {
                producers.sort(Comparator.naturalOrder());
                variantsByOutputKey.put(key, List.copyOf(producers));
            }
        }
        Object2IntLinkedOpenHashMap<TrinityPatternVariant> cyclicOwnerByVariant = new Object2IntLinkedOpenHashMap<>();
        for (TrinityStronglyConnectedComponent component : components) {
            if (!component.cyclic()) {
                continue;
            }
            for (TrinityPatternVariant variant : component.cycleVariants()) {
                if (cyclicOwnerByVariant.containsKey(variant) &&
                        cyclicOwnerByVariant.getInt(variant) != component.index()) {
                    throw new IllegalStateException("A Trinity feedback transition cannot belong to multiple SCCs");
                }
                cyclicOwnerByVariant.put(variant, component.index());
            }
        }
        return new TrinityCraftingTopology(
                components,
                mapping,
                topologicalOrder(predecessors, successors),
                variantsByOutputComponent,
                variantsByOutputKey,
                cyclicOwnerByVariant);
    }

    private static IntList topologicalOrder(
                                            List<? extends IntSet> predecessors,
                                            List<? extends IntSet> successors) {
        int[] indegree = predecessors.stream().mapToInt(IntSet::size).toArray();
        IntPriorityQueue ready = new IntHeapPriorityQueue();
        for (int index = 0; index < indegree.length; index++) {
            if (indegree[index] == 0) {
                ready.enqueue(index);
            }
        }
        IntArrayList order = new IntArrayList(indegree.length);
        while (!ready.isEmpty()) {
            int component = ready.dequeueInt();
            order.add(component);
            for (int successor : successors.get(component)) {
                indegree[successor]--;
                if (indegree[successor] == 0) {
                    ready.enqueue(successor);
                }
            }
        }
        if (order.size() != indegree.length) {
            throw new IllegalStateException("Tarjan condensation unexpectedly contains a cycle");
        }
        return IntLists.unmodifiable(order);
    }

    private static StopState stopState(TrinityPlanningControl control) {
        if (control.cancellationRequested()) {
            return StopState.CANCELLED;
        }
        return control.deadlineExceeded() ? StopState.DEADLINE_EXCEEDED : StopState.RUNNING;
    }

    private static <T> TrinityAlgorithmResult<T> stopped(StopState state) {
        return switch (state) {
            case CANCELLED -> TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.cancelled"),
                    Map.of("phase", "topology")));
            case DEADLINE_EXCEEDED -> TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.timeout"),
                    Map.of("phase", "topology")));
            case RUNNING -> throw new IllegalArgumentException("A running Trinity topology analysis is not stopped");
        };
    }

    private record Graph(
                         List<AEKey> keys,
                         Object2IntMap<AEKey> indexByKey,
                         List<IntList> adjacency) {

        private static Graph create(TrinityCraftingGraphSnapshot snapshot,
                                    List<TrinityPatternVariant> variants) {
            ObjectLinkedOpenHashSet<AEKey> orderedKeys = new ObjectLinkedOpenHashSet<>(snapshot.keys());
            for (TrinityPatternVariant variant : variants) {
                if (variant == null) {
                    throw new IllegalArgumentException("A Trinity topology cannot contain a null variant");
                }
                orderedKeys.addAll(variant.inputs().keySet());
                orderedKeys.addAll(variant.outputs().keySet());
            }
            List<AEKey> keys = List.copyOf(orderedKeys);
            Object2IntLinkedOpenHashMap<AEKey> indexByKey = new Object2IntLinkedOpenHashMap<>();
            for (int index = 0; index < keys.size(); index++) {
                indexByKey.put(keys.get(index), index);
            }
            ObjectArrayList<IntLinkedOpenHashSet> edges = new ObjectArrayList<>(keys.size());
            for (int index = 0; index < keys.size(); index++) {
                edges.add(new IntLinkedOpenHashSet());
            }
            for (TrinityPatternVariant variant : variants) {
                for (AEKey input : variant.inputs().keySet()) {
                    int inputIndex = indexByKey.getInt(input);
                    for (AEKey output : variant.outputs().keySet()) {
                        edges.get(inputIndex).add(indexByKey.getInt(output));
                    }
                }
            }
            ObjectArrayList<IntList> adjacency = new ObjectArrayList<>(keys.size());
            edges.forEach(nodeEdges -> adjacency.add(IntLists.unmodifiable(new IntArrayList(nodeEdges))));
            return new Graph(
                    keys,
                    Object2IntMaps.unmodifiable(indexByKey),
                    List.copyOf(adjacency));
        }
    }

    private static final class TarjanState {

        private final List<IntList> adjacency;
        private final int[] indexes;
        private final int[] lowLinks;
        private final boolean[] onStack;
        private final ArrayDeque<Integer> stack = new ArrayDeque<>();
        private final ObjectArrayList<IntList> components = new ObjectArrayList<>();
        private int nextIndex;

        private TarjanState(List<IntList> adjacency) {
            this.adjacency = adjacency;
            this.indexes = new int[adjacency.size()];
            this.lowLinks = new int[adjacency.size()];
            this.onStack = new boolean[adjacency.size()];
            Arrays.fill(this.indexes, -1);
        }

        private StopState traverse(TrinityPlanningControl control) {
            for (int node = 0; node < this.adjacency.size(); node++) {
                StopState state = stopState(control);
                if (state != StopState.RUNNING) {
                    return state;
                }
                if (this.indexes[node] < 0) {
                    state = traverseFrom(node, control);
                    if (state != StopState.RUNNING) {
                        return state;
                    }
                }
            }
            return StopState.RUNNING;
        }

        private StopState traverseFrom(int start, TrinityPlanningControl control) {
            ArrayDeque<TarjanFrame> frames = new ArrayDeque<>();
            discover(start);
            frames.push(new TarjanFrame(start, -1));
            while (!frames.isEmpty()) {
                StopState state = stopState(control);
                if (state != StopState.RUNNING) {
                    return state;
                }
                TarjanFrame frame = frames.peek();
                IntList successors = this.adjacency.get(frame.node);
                if (frame.nextSuccessor < successors.size()) {
                    int successor = successors.getInt(frame.nextSuccessor++);
                    if (this.indexes[successor] < 0) {
                        discover(successor);
                        frames.push(new TarjanFrame(successor, frame.node));
                    } else if (this.onStack[successor]) {
                        this.lowLinks[frame.node] = Math.min(
                                this.lowLinks[frame.node],
                                this.indexes[successor]);
                    }
                    continue;
                }

                frames.pop();
                if (frame.parent >= 0) {
                    this.lowLinks[frame.parent] = Math.min(
                            this.lowLinks[frame.parent],
                            this.lowLinks[frame.node]);
                }
                completeComponent(frame.node);
            }
            return StopState.RUNNING;
        }

        private void discover(int node) {
            this.indexes[node] = this.nextIndex;
            this.lowLinks[node] = this.nextIndex;
            this.nextIndex++;
            this.stack.push(node);
            this.onStack[node] = true;
        }

        private void completeComponent(int node) {
            if (this.lowLinks[node] != this.indexes[node]) {
                return;
            }
            IntArrayList component = new IntArrayList();
            int member;
            do {
                member = this.stack.pop();
                this.onStack[member] = false;
                component.add(member);
            } while (member != node);
            this.components.add(component);
        }

        private ObjectArrayList<IntList> components() {
            return this.components;
        }
    }

    private static final class TarjanFrame {

        private final int node;
        private final int parent;
        private int nextSuccessor;

        private TarjanFrame(int node, int parent) {
            this.node = node;
            this.parent = parent;
        }
    }

    private enum StopState {
        RUNNING,
        CANCELLED,
        DEADLINE_EXCEEDED
    }
}
