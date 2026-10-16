package com.fish_dan_.data_energistics.recipe.matching;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BiFunction;

/**
 * Matches counted item requirements against physical input slots.
 *
 * <p>
 * This item-only matcher aggregates stacks with the same item and data components before matching. Fluid and AE key
 * resources are matched by their own resource-key aggregation in the recipe implementations. The matcher keeps the
 * physical slot amounts in its result so a caller can reserve or consume the exact source slots after the logical
 * match succeeds. Every non-empty item input must still be accepted by at least one requirement; an accepted extra
 * stack may remain in the machine when the logical requirement was already satisfied by another stack.
 * </p>
 */
public final class CountedItemInputMatcher {

    private CountedItemInputMatcher() {}

    /**
     * Matches all counted requirements and returns the physical amounts consumed from each input slot.
     *
     * @return a non-empty assignment for a successful non-empty recipe, an empty assignment when both sides have no
     *         item inputs, or {@code null} when the requirements cannot be satisfied without an unrecognized input
     */
    public static @Nullable ObjectList<Assignment> match(
                                                         Iterable<? extends CountedItemRequirement> requirements,
                                                         ObjectList<ItemStack> inputs) {
        ObjectList<CountedItemRequirement> normalized = new ObjectArrayList<>();
        for (CountedItemRequirement requirement : requirements) {
            normalized.add(requirement);
        }
        ObjectList<AggregatedInput> aggregatedInputs = aggregate(inputs);
        if (normalized.isEmpty()) {
            return aggregatedInputs.isEmpty() ? ObjectLists.emptyList() : null;
        }
        if (aggregatedInputs.isEmpty()) {
            return null;
        }

        for (AggregatedInput input : aggregatedInputs) {
            if (!acceptsAny(normalized, input.stack)) {
                return null;
            }
        }

        int source = 0;
        int requirementStart = 1;
        int inputStart = requirementStart + normalized.size();
        int sink = inputStart + aggregatedInputs.size();
        FlowNetwork network = new FlowNetwork(sink + 1);
        int requiredAmount = 0;
        for (int requirementIndex = 0; requirementIndex < normalized.size(); requirementIndex++) {
            CountedItemRequirement requirement = normalized.get(requirementIndex);
            requiredAmount = Math.addExact(requiredAmount, requirement.count());
            network.addEdge(source, requirementStart + requirementIndex, requirement.count());
            for (int inputIndex = 0; inputIndex < aggregatedInputs.size(); inputIndex++) {
                AggregatedInput input = aggregatedInputs.get(inputIndex);
                if (requirement.ingredient().test(input.stack)) {
                    network.addEdge(requirementStart + requirementIndex,
                            inputStart + inputIndex,
                            input.count);
                }
            }
        }

        ObjectList<FlowNetwork.Edge> inputCapacityEdges = new ObjectArrayList<>(aggregatedInputs.size());
        for (int inputIndex = 0; inputIndex < aggregatedInputs.size(); inputIndex++) {
            inputCapacityEdges.add(network.addEdge(
                    inputStart + inputIndex,
                    sink,
                    aggregatedInputs.get(inputIndex).count));
        }

        if (network.maxFlow(source, sink) != requiredAmount) {
            return null;
        }

        ObjectList<Assignment> assignments = new ObjectArrayList<>();
        for (int inputIndex = 0; inputIndex < aggregatedInputs.size(); inputIndex++) {
            int remaining = inputCapacityEdges.get(inputIndex).flow();
            if (remaining <= 0) {
                continue;
            }

            for (PhysicalInput physicalInput : aggregatedInputs.get(inputIndex).physicalInputs) {
                if (remaining <= 0) {
                    break;
                }
                int amount = Math.min(remaining, physicalInput.amount);
                assignments.add(new Assignment(physicalInput.index, amount));
                remaining -= amount;
            }
            if (remaining != 0) {
                throw new IllegalStateException("Aggregated item input could not be mapped to physical slots");
            }
        }

        assignments.sort(Comparator.comparingInt(Assignment::inputIndex));
        return ObjectLists.unmodifiable(assignments);
    }

    /**
     * Matches one ingredient whose required amount may be split across several physical slots.
     */
    public static @Nullable ObjectList<Assignment> match(Ingredient ingredient, int count,
                                                         ObjectList<ItemStack> inputs) {
        return match(ObjectLists.singleton(new Requirement(ingredient, count)), inputs);
    }

    /** Maps physical assignments to a recipe-specific slot type without copying the assignment list twice. */
    public static @Nullable <T> ObjectList<T> mapAssignments(
                                                             @Nullable ObjectList<Assignment> assignments,
                                                             BiFunction<Integer, Integer, T> factory) {
        if (assignments == null) {
            return null;
        }
        ObjectList<T> mapped = new ObjectArrayList<>(assignments.size());
        for (Assignment assignment : assignments) {
            mapped.add(factory.apply(assignment.inputIndex(), assignment.amount()));
        }
        return ObjectLists.unmodifiable(mapped);
    }

    private static boolean acceptsAny(ObjectList<CountedItemRequirement> requirements, ItemStack stack) {
        for (CountedItemRequirement requirement : requirements) {
            if (requirement.ingredient().test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One ingredient and the total number of items required for it.
     */
    public record Requirement(Ingredient ingredient, int count) implements CountedItemRequirement {

        public Requirement {
            if (ingredient.isEmpty()) {
                throw new IllegalArgumentException("Counted item requirement must not be empty");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("Counted item requirement count must be positive: " + count);
            }
        }
    }

    /**
     * One physical input slot and the amount consumed from it.
     */
    public record Assignment(int inputIndex, int amount) {

        public Assignment {
            if (inputIndex < 0) {
                throw new IllegalArgumentException("Input slot index must not be negative: " + inputIndex);
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("Input assignment amount must be positive: " + amount);
            }
        }
    }

    private static ObjectList<AggregatedInput> aggregate(ObjectList<ItemStack> inputs) {
        ObjectList<AggregatedInput> aggregated = new ObjectArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            ItemStack stack = inputs.get(index);
            if (stack.isEmpty()) {
                continue;
            }

            AggregatedInput existing = null;
            for (AggregatedInput candidate : aggregated) {
                if (ItemStack.isSameItemSameComponents(candidate.stack, stack)) {
                    existing = candidate;
                    break;
                }
            }
            if (existing == null) {
                aggregated.add(new AggregatedInput(stack, index));
            } else {
                existing.add(stack, index);
            }
        }
        return aggregated;
    }

    private static final class AggregatedInput {

        private final ItemStack stack;
        private final ObjectList<PhysicalInput> physicalInputs = new ObjectArrayList<>();
        private int count;

        private AggregatedInput(ItemStack stack, int index) {
            this.stack = stack.copy();
            this.count = stack.getCount();
            this.physicalInputs.add(new PhysicalInput(index, stack.getCount()));
        }

        private void add(ItemStack stack, int index) {
            this.count = Math.addExact(this.count, stack.getCount());
            this.stack.setCount(this.count);
            this.physicalInputs.add(new PhysicalInput(index, stack.getCount()));
        }
    }

    private record PhysicalInput(int index, int amount) {}

    private static final class FlowNetwork {

        private final ObjectList<ObjectList<Edge>> graph;
        private final int[] levels;
        private final int[] cursors;

        private FlowNetwork(int nodeCount) {
            this.graph = new ObjectArrayList<>(nodeCount);
            for (int index = 0; index < nodeCount; index++) {
                this.graph.add(new ObjectArrayList<>());
            }
            this.levels = new int[nodeCount];
            this.cursors = new int[nodeCount];
        }

        private Edge addEdge(int from, int to, int capacity) {
            Edge forward = new Edge(to, capacity, this.graph.get(to).size());
            Edge reverse = new Edge(from, 0, this.graph.get(from).size());
            this.graph.get(from).add(forward);
            this.graph.get(to).add(reverse);
            return forward;
        }

        private int maxFlow(int source, int sink) {
            int result = 0;
            while (buildLevels(source, sink)) {
                Arrays.fill(this.cursors, 0);
                int pushed;
                while ((pushed = push(source, sink, Integer.MAX_VALUE)) > 0) {
                    result = Math.addExact(result, pushed);
                }
            }
            return result;
        }

        private boolean buildLevels(int source, int sink) {
            Arrays.fill(this.levels, -1);
            int[] queue = new int[this.graph.size()];
            int head = 0;
            int tail = 0;
            queue[tail++] = source;
            this.levels[source] = 0;
            while (head < tail) {
                int node = queue[head++];
                for (Edge edge : this.graph.get(node)) {
                    if (edge.capacity > 0 && this.levels[edge.to] < 0) {
                        this.levels[edge.to] = this.levels[node] + 1;
                        queue[tail++] = edge.to;
                    }
                }
            }
            return this.levels[sink] >= 0;
        }

        private int push(int node, int sink, int amount) {
            if (node == sink) {
                return amount;
            }
            ObjectList<Edge> edges = this.graph.get(node);
            for (; this.cursors[node] < edges.size(); this.cursors[node]++) {
                Edge edge = edges.get(this.cursors[node]);
                if (edge.capacity <= 0 || this.levels[edge.to] != this.levels[node] + 1) {
                    continue;
                }
                int pushed = push(edge.to, sink, Math.min(amount, edge.capacity));
                if (pushed <= 0) {
                    continue;
                }
                edge.capacity -= pushed;
                Edge reverse = this.graph.get(edge.to).get(edge.reverseIndex);
                reverse.capacity += pushed;
                return pushed;
            }
            return 0;
        }

        private static final class Edge {

            private final int to;
            private final int originalCapacity;
            private int capacity;
            private final int reverseIndex;

            private Edge(int to, int capacity, int reverseIndex) {
                this.to = to;
                this.capacity = capacity;
                this.originalCapacity = capacity;
                this.reverseIndex = reverseIndex;
            }

            private int flow() {
                return this.originalCapacity - this.capacity;
            }
        }
    }
}
