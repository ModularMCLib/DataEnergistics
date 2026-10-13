package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

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
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;

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
     * Analyzes topology while observing the request-wide cancellation and deadline boundary.
     */
    public TrinityAlgorithmResult<TrinityCraftingTopology> analyze(
                                                                   TrinityCraftingGraphSnapshot snapshot,
                                                                   ObjectList<TrinityPatternVariant> variants,
                                                                   int maxSccKeys,
                                                                   TrinityPlanningControl control) {
        if (maxSccKeys <= 0) {
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
                        FastUtilCollections.mapOf(
                                "limit", Integer.toString(maxSccKeys),
                                "required", Integer.toString(component.size()))));
            }
        }
        return TrinityAlgorithmResult.success(buildTopology(graph, variants, rawComponents));
    }

    private static TrinityCraftingTopology buildTopology(
                                                         Graph graph,
                                                         ObjectList<TrinityPatternVariant> variants,
                                                         ObjectList<IntList> rawComponents) {
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

        ObjectArrayList<ObjectList<TrinityPatternVariant>> cycleVariants = new ObjectArrayList<>(rawComponents.size());
        ObjectArrayList<ObjectList<TrinityPatternVariant>> outputVariants = new ObjectArrayList<>(rawComponents.size());
        for (int index = 0; index < rawComponents.size(); index++) {
            cycleVariants.add(new ObjectArrayList<>());
            outputVariants.add(new ObjectArrayList<>());
        }
        for (TrinityPatternVariant variant : variants) {
            IntSet inputComponents = new IntLinkedOpenHashSet();
            IntSet outputComponents = new IntLinkedOpenHashSet();
            // Retained inputs are physical reservations, not production dependencies. Removing only those
            // reservations keeps ordinary input/output overlap visible while preventing an unchanged tool from
            // connecting every recipe that uses it into one artificial SCC.
            graphInputs(variant).keySet().forEach(key -> {
                int component = componentByNode[graph.indexByKey().getInt(key)];
                inputComponents.add(component);
            });
            graphOutputs(variant).keySet().forEach(key -> {
                int component = componentByNode[graph.indexByKey().getInt(key)];
                outputComponents.add(component);
            });
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
            ObjectList<AEKey> keys = nodes.intStream().mapToObj(graph.keys()::get).collect(ObjectArrayList.toList());
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
                    sortedComponentIndexes(predecessors.get(componentIndex)),
                    sortedComponentIndexes(successors.get(componentIndex))));
        }
        Int2ObjectLinkedOpenHashMap<ObjectList<TrinityPatternVariant>> variantsByOutputComponent = new Int2ObjectLinkedOpenHashMap<>();
        for (int componentIndex = 0; componentIndex < outputVariants.size(); componentIndex++) {
            variantsByOutputComponent.put(componentIndex, FastUtilCollections.immutableList(outputVariants.get(componentIndex)));
        }
        Object2ObjectLinkedOpenHashMap<AEKey, ObjectList<TrinityPatternVariant>> variantsByOutputKey = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, ObjectArrayList<TrinityPatternVariant>> producerLists = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            graphOutputs(variant).keySet().forEach(key -> producerLists
                    .computeIfAbsent(key, ignored -> new ObjectArrayList<>())
                    .add(variant));
        }
        for (AEKey key : graph.keys()) {
            ObjectArrayList<TrinityPatternVariant> producers = producerLists.get(key);
            if (producers != null) {
                producers.sort(Comparator.naturalOrder());
                variantsByOutputKey.put(key, FastUtilCollections.immutableList(producers));
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

    /** Condensation edges must be numerically ordered independently of graph traversal and insertion order. */
    private static IntList sortedComponentIndexes(IntSet indexes) {
        IntArrayList sorted = new IntArrayList(indexes);
        IntArrays.quickSort(sorted.elements(), 0, sorted.size());
        return IntLists.unmodifiable(sorted);
    }

    private static IntList topologicalOrder(
                                            ObjectList<? extends IntSet> predecessors,
                                            ObjectList<? extends IntSet> successors) {
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
                    FastUtilCollections.mapOf("phase", "topology")));
            case DEADLINE_EXCEEDED -> TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.timeout"),
                    FastUtilCollections.mapOf("phase", "topology")));
            case RUNNING -> throw new IllegalArgumentException("A running Trinity topology analysis is not stopped");
        };
    }

    private record Graph(
                         ObjectList<AEKey> keys,
                         Object2IntMap<AEKey> indexByKey,
                         ObjectList<IntList> adjacency) {

        private static Graph create(TrinityCraftingGraphSnapshot snapshot,
                                    ObjectList<TrinityPatternVariant> variants) {
            ObjectLinkedOpenHashSet<AEKey> orderedKeys = new ObjectLinkedOpenHashSet<>(snapshot.keys());
            for (TrinityPatternVariant variant : variants) {
                if (variant == null) {
                    throw new IllegalArgumentException("A Trinity topology cannot contain a null variant");
                }
                orderedKeys.addAll(variant.inputs().keySet());
                orderedKeys.addAll(variant.outputs().keySet());
            }
            ObjectList<AEKey> keys = FastUtilCollections.immutableList(orderedKeys);
            Object2IntLinkedOpenHashMap<AEKey> indexByKey = new Object2IntLinkedOpenHashMap<>();
            for (int index = 0; index < keys.size(); index++) {
                indexByKey.put(keys.get(index), index);
            }
            ObjectArrayList<IntLinkedOpenHashSet> edges = new ObjectArrayList<>(keys.size());
            for (int index = 0; index < keys.size(); index++) {
                edges.add(new IntLinkedOpenHashSet());
            }
            for (TrinityPatternVariant variant : variants) {
                for (AEKey input : graphInputs(variant).keySet()) {
                    int inputIndex = indexByKey.getInt(input);
                    for (AEKey output : graphOutputs(variant).keySet()) {
                        edges.get(inputIndex).add(indexByKey.getInt(output));
                    }
                }
            }
            ObjectArrayList<IntList> adjacency = new ObjectArrayList<>(keys.size());
            edges.forEach(nodeEdges -> adjacency.add(IntLists.unmodifiable(new IntArrayList(nodeEdges))));
            return new Graph(
                    keys,
                    Object2IntMaps.unmodifiable(indexByKey),
                    FastUtilCollections.immutableList(adjacency));
        }
    }

    private static Object2ObjectMap<AEKey, BigInteger> graphInputs(TrinityPatternVariant variant) {
        // Retained tools are still required inputs. Keeping them in the dependency graph preserves
        // the producer order for recipes that need an existing tool, while graphOutputs filters the
        // unchanged remainder so the tool does not become a producer of every material recipe.
        return variant.inputs();
    }

    private static Object2ObjectMap<AEKey, BigInteger> graphOutputs(TrinityPatternVariant variant) {
        return subtractRetained(variant.outputs(), retainedAmounts(variant));
    }

    private static Object2ObjectMap<AEKey, BigInteger> subtractRetained(
                                                                        Object2ObjectMap<AEKey, BigInteger> amounts,
                                                                        Object2ObjectMap<AEKey, BigInteger> retained) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> result = new Object2ObjectLinkedOpenHashMap<>(amounts);
        retained.forEach((key, amount) -> {
            BigInteger remaining = result.get(key);
            if (remaining == null) {
                return;
            }
            remaining = remaining.subtract(amount);
            if (remaining.signum() > 0) {
                result.put(key, remaining);
            } else {
                result.remove(key);
            }
        });
        return result;
    }

    private static Object2ObjectMap<AEKey, BigInteger> retainedAmounts(TrinityPatternVariant variant) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> retained = new Object2ObjectLinkedOpenHashMap<>();
        for (var binding : variant.bindings()) {
            if (!binding.lifetimeBudget() && binding.reusableRule() != null && binding.remainingKey() != null &&
                    binding.remainingKey().equals(binding.template().what())) {
                retained.merge(binding.remainingKey(), binding.remainingAmount(), BigInteger::add);
            }
        }
        return retained;
    }

    private static final class TarjanState {

        private final ObjectList<IntList> adjacency;
        private final int[] indexes;
        private final int[] lowLinks;
        private final boolean[] onStack;
        private final ArrayDeque<Integer> stack = new ArrayDeque<>();
        private final ObjectArrayList<IntList> components = new ObjectArrayList<>();
        private int nextIndex;

        private TarjanState(ObjectList<IntList> adjacency) {
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
