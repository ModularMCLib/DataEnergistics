package com.fish_dan_.data_energistics.recipe.reassembler;

import com.fish_dan_.data_energistics.registry.DERecipes;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class DataRipperReassemblerRecipe implements Recipe<DataRipperReassemblerRecipeInput> {

    public static final int PROCESS_TICKS = 200;
    public static final int ITEM_INPUT_SLOTS = 9;
    public static final int KEY_INPUT_SLOTS = 1;
    public static final int FLUID_INPUT_SLOTS = 2;
    public static final int ITEM_OUTPUT_SLOTS = 3;
    public static final int KEY_OUTPUT_SLOTS = 1;
    public static final int FLUID_OUTPUT_SLOTS = 2;
    public static final int KEY_INPUT_SLOT_INDEX = ITEM_INPUT_SLOTS;
    public static final long MAX_FLUID_AMOUNT = 51_200L;
    public static final long MAX_RESOURCE_AMOUNT = 51_200_000L;
    @Getter
    private final NonNullList<DataRipperReassemblerIngredient> itemInputs;
    @Getter
    private final List<GenericStack> fluidInputs;
    private final List<DataReassemblerItemOutput> itemOutputs;
    @Getter
    private final List<GenericStack> fluidOutputs;
    @Getter
    private final int processTicks;
    @Nullable
    private final GenericStack keyInput;
    @Nullable
    private final GenericStack keyOutput;

    public DataRipperReassemblerRecipe(List<DataRipperReassemblerIngredient> itemInputs,
                                       List<GenericStack> fluidInputs,
                                       List<DataReassemblerItemOutput> itemOutputs,
                                       List<GenericStack> fluidOutputs,
                                       int processTicks,
                                       @Nullable GenericStack keyInput,
                                       @Nullable GenericStack keyOutput) {
        validateRecipe(itemInputs, fluidInputs, itemOutputs, fluidOutputs, processTicks, keyInput, keyOutput);
        this.itemInputs = NonNullList.copyOf(itemInputs);
        this.fluidInputs = ObjectLists.unmodifiable(new ObjectArrayList<>(fluidInputs));
        this.itemOutputs = ObjectLists.unmodifiable(new ObjectArrayList<>(itemOutputs));
        this.fluidOutputs = ObjectLists.unmodifiable(new ObjectArrayList<>(fluidOutputs));
        this.processTicks = processTicks;
        this.keyInput = keyInput;
        this.keyOutput = keyOutput;
    }

    private static void validateRecipe(List<DataRipperReassemblerIngredient> itemInputs,
                                       List<GenericStack> fluidInputs,
                                       List<DataReassemblerItemOutput> itemOutputs,
                                       List<GenericStack> fluidOutputs,
                                       int processTicks,
                                       @Nullable GenericStack keyInput,
                                       @Nullable GenericStack keyOutput) {
        if (itemInputs.size() > ITEM_INPUT_SLOTS) {
            throw new IllegalArgumentException("Data reassembler supports at most " + ITEM_INPUT_SLOTS + " item inputs");
        }
        if (fluidInputs.size() > FLUID_INPUT_SLOTS) {
            throw new IllegalArgumentException("Data reassembler supports at most " + FLUID_INPUT_SLOTS + " fluid inputs");
        }
        if (itemOutputs.size() > ITEM_OUTPUT_SLOTS) {
            throw new IllegalArgumentException("Data reassembler supports at most " + ITEM_OUTPUT_SLOTS + " item outputs");
        }
        if (fluidOutputs.size() > FLUID_OUTPUT_SLOTS) {
            throw new IllegalArgumentException("Data reassembler supports at most " + FLUID_OUTPUT_SLOTS + " fluid outputs");
        }
        validateFluids(fluidInputs, "input");
        validateFluids(fluidOutputs, "output");
        validateResource(keyInput, "input");
        validateResource(keyOutput, "output");
        if (itemInputs.isEmpty() && fluidInputs.isEmpty() && keyInput == null) {
            throw new IllegalArgumentException("Data reassembler recipe must define at least one input");
        }
        if (itemOutputs.isEmpty() && fluidOutputs.isEmpty() && keyOutput == null) {
            throw new IllegalArgumentException("Data reassembler recipe must define at least one output");
        }
        if (processTicks <= 0) {
            throw new IllegalArgumentException("Data reassembler duration must be greater than 0: " + processTicks);
        }
    }

    private static void validateFluids(List<GenericStack> fluids, String role) {
        for (GenericStack fluid : fluids) {
            if (!(fluid.what() instanceof AEFluidKey) || fluid.amount() <= 0L || fluid.amount() > MAX_FLUID_AMOUNT) {
                throw new IllegalArgumentException(
                        "Data reassembler fluid " + role + " must be a positive fluid stack within " +
                                MAX_FLUID_AMOUNT + ": " + fluid);
            }
        }
    }

    private static void validateResource(@Nullable GenericStack resource, String role) {
        if (resource == null) {
            return;
        }
        if (resource.what() instanceof AEItemKey || resource.what() instanceof AEFluidKey ||
                resource.amount() <= 0L || resource.amount() > MAX_RESOURCE_AMOUNT) {
            throw new IllegalArgumentException(
                    "Data reassembler resource " + role + " must be a positive custom resource within " +
                            MAX_RESOURCE_AMOUNT + ": " + resource);
        }
    }

    @Override
    public boolean matches(DataRipperReassemblerRecipeInput input, Level level) {
        if (!matchesKeyInput(input.keyInputs())) {
            return false;
        }
        if (!matchesFluidInputs(input.fluidInputs())) {
            return false;
        }
        return findMatchingItemInputs(input.items()) != null;
    }

    private ObjectList<DataRipperReassemblerIngredient> getItemInputsForMatching() {
        ObjectList<DataRipperReassemblerIngredient> matchingOrder = new ObjectArrayList<>(this.itemInputs);
        int segmentStart = 0;
        for (int index = 0; index <= matchingOrder.size(); index++) {
            if (index == matchingOrder.size() || getItemIngredientMatchPriority(matchingOrder.get(index).ingredient()) == ItemIngredientMatchPriority.UNKNOWN) {
                matchingOrder.subList(segmentStart, index).sort(DataRipperReassemblerRecipe::compareKnownItemIngredients);
                segmentStart = index + 1;
            }
        }
        return matchingOrder;
    }

    private static int compareKnownItemIngredients(DataRipperReassemblerIngredient left,
                                                   DataRipperReassemblerIngredient right) {
        ItemIngredientMatchPriority leftPriority = getItemIngredientMatchPriority(left.ingredient());
        ItemIngredientMatchPriority rightPriority = getItemIngredientMatchPriority(right.ingredient());
        int priorityComparison = leftPriority.compareTo(rightPriority);
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        if (leftPriority == ItemIngredientMatchPriority.EXPLICIT) {
            return Integer.compare(left.ingredient().getItems().length, right.ingredient().getItems().length);
        }
        return 0;
    }

    private static ItemIngredientMatchPriority getItemIngredientMatchPriority(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            return ItemIngredientMatchPriority.UNKNOWN;
        }
        Ingredient.Value[] values = ingredient.getValues();
        if (values.length == 0) {
            return ItemIngredientMatchPriority.UNKNOWN;
        }
        if (values.length == 1 && values[0] instanceof Ingredient.ItemValue) {
            return ItemIngredientMatchPriority.EXACT;
        }
        boolean containsTag = false;
        for (Ingredient.Value value : values) {
            if (value instanceof Ingredient.TagValue) {
                containsTag = true;
            } else if (!(value instanceof Ingredient.ItemValue)) {
                return ItemIngredientMatchPriority.UNKNOWN;
            }
        }
        return containsTag ? ItemIngredientMatchPriority.TAG : ItemIngredientMatchPriority.EXPLICIT;
    }

    private enum ItemIngredientMatchPriority {
        EXACT,
        EXPLICIT,
        TAG,
        UNKNOWN
    }

    @Override
    public ItemStack assemble(DataRipperReassemblerRecipeInput input, HolderLookup.Provider registries) {
        NonNullList<ItemStack> outputs = this.getCraftedItemOutputs();
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.itemOutputs.isEmpty() ? ItemStack.EMPTY : this.itemOutputs.getFirst().stack();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> expanded = NonNullList.create();
        for (DataRipperReassemblerIngredient countedIngredient : this.itemInputs) {
            for (int i = 0; i < countedIngredient.count(); i++) {
                expanded.add(countedIngredient.ingredient());
            }
        }
        return expanded;
    }

    @Nullable
    public Object2LongMap<AEFluidKey> getMergedFluidInputAmounts() {
        Object2LongMap<AEFluidKey> merged = new Object2LongLinkedOpenHashMap<>();
        for (GenericStack fluidInput : this.fluidInputs) {
            if (!(fluidInput.what() instanceof AEFluidKey fluidKey) || fluidInput.amount() <= 0) {
                return null;
            }
            long current = merged.getOrDefault(fluidKey, 0L);
            if (fluidInput.amount() > Long.MAX_VALUE - current) {
                return null;
            }
            merged.put(fluidKey, current + fluidInput.amount());
        }
        return merged;
    }

    public NonNullList<ItemStack> getItemOutputs() {
        NonNullList<ItemStack> outputs = NonNullList.create();
        this.itemOutputs.stream().map(DataReassemblerItemOutput::stack).forEach(outputs::add);
        return outputs;
    }

    public List<DataReassemblerItemOutput> getItemOutputDefinitions() {
        return this.itemOutputs;
    }

    public NonNullList<ItemStack> getCraftedItemOutputs() {
        NonNullList<ItemStack> outputs = NonNullList.create();
        this.itemOutputs.stream().map(DataReassemblerItemOutput::createStack).forEach(outputs::add);
        return outputs;
    }

    @Nullable
    public GenericStack getKeyInput() {
        return this.keyInput;
    }

    @Nullable
    public GenericStack getKeyOutput() {
        return this.keyOutput;
    }

    private boolean matchesKeyInput(List<@Nullable GenericStack> inputKeys) {
        long available = 0L;
        for (GenericStack inputKey : inputKeys) {
            if (inputKey == null || inputKey.amount() <= 0L) {
                continue;
            }
            if (this.keyInput == null || !this.keyInput.what().equals(inputKey.what())) {
                return false;
            }
            if (Long.MAX_VALUE - available < inputKey.amount()) {
                return true;
            }
            available += inputKey.amount();
        }
        return this.keyInput == null || available >= this.keyInput.amount();
    }

    private boolean matchesFluidInputs(List<GenericStack> inputFluids) {
        Object2LongMap<AEFluidKey> required = getMergedFluidInputAmounts();
        if (required == null) {
            return false;
        }

        Object2LongMap<AEFluidKey> available = new Object2LongOpenHashMap<>();
        for (GenericStack fluid : inputFluids) {
            if (!(fluid.what() instanceof AEFluidKey fluidKey) || fluid.amount() <= 0) {
                return false;
            }
            if (!required.containsKey(fluidKey)) {
                return false;
            }
            available.mergeLong(fluidKey, fluid.amount(), Long::sum);
        }

        for (Object2LongMap.Entry<AEFluidKey> requirement : required.object2LongEntrySet()) {
            if (available.getOrDefault(requirement.getKey(), 0L) < requirement.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Matches all non-empty item stacks as one assignment instead of accepting a recipe from only a subset of the
     * machine inputs. The backtracking assignment is required for overlapping tags and keeps the same assignment for
     * every ingredient before a recipe is accepted.
     * <p>
     * Finds a complete assignment for the recipe's item requirements.
     *
     * <p>
     * The assignment is also used by the machine when it reserves inputs. Keeping the match and reservation on
     * the same flow result prevents an ingredient that overlaps a tag from being matched one way and consumed another
     * way.
     * </p>
     */
    public @Nullable ObjectList<ItemInputAssignment> findMatchingItemInputs(List<ItemStack> inputItems) {
        ObjectList<Integer> inputIndexes = new ObjectArrayList<>();
        ObjectList<ItemStack> nonEmpty = new ObjectArrayList<>();
        for (int index = 0; index < inputItems.size(); index++) {
            ItemStack stack = inputItems.get(index);
            if (!stack.isEmpty()) {
                inputIndexes.add(index);
                nonEmpty.add(stack.copy());
            }
        }
        ObjectList<DataRipperReassemblerIngredient> requirements = getItemInputsForMatching();
        if (nonEmpty.isEmpty() || requirements.isEmpty()) {
            return nonEmpty.isEmpty() && requirements.isEmpty() ? ObjectLists.emptyList() : null;
        }

        for (ItemStack input : nonEmpty) {
            boolean recognized = false;
            for (DataRipperReassemblerIngredient requirement : requirements) {
                if (requirement.ingredient().test(input)) {
                    recognized = true;
                    break;
                }
            }
            if (!recognized) {
                return null;
            }
        }

        int source = 0;
        int requirementStart = 1;
        int inputStart = requirementStart + requirements.size();
        int sink = inputStart + nonEmpty.size();
        ItemFlowNetwork network = new ItemFlowNetwork(sink + 1);
        ObjectList<ItemAssignmentEdge> assignmentEdges = new ObjectArrayList<>();
        int requiredAmount = 0;
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            DataRipperReassemblerIngredient requirement = requirements.get(requirementIndex);
            requiredAmount = Math.addExact(requiredAmount, requirement.count());
            network.addEdge(source, requirementStart + requirementIndex, requirement.count());
            for (int inputIndex = 0; inputIndex < nonEmpty.size(); inputIndex++) {
                if (requirement.ingredient().test(nonEmpty.get(inputIndex))) {
                    ItemFlowNetwork.Edge edge = network.addEdge(
                            requirementStart + requirementIndex,
                            inputStart + inputIndex,
                            nonEmpty.get(inputIndex).getCount());
                    assignmentEdges.add(new ItemAssignmentEdge(inputIndexes.get(inputIndex), edge));
                }
            }
        }
        ObjectList<ItemFlowNetwork.Edge> inputCapacityEdges = new ObjectArrayList<>(nonEmpty.size());
        for (int inputIndex = 0; inputIndex < nonEmpty.size(); inputIndex++) {
            inputCapacityEdges.add(network.addEdge(
                    inputStart + inputIndex,
                    sink,
                    nonEmpty.get(inputIndex).getCount()));
        }
        if (network.maxFlow(source, sink) != requiredAmount) {
            return null;
        }
        for (ItemFlowNetwork.Edge inputCapacityEdge : inputCapacityEdges) {
            if (inputCapacityEdge.flow() <= 0) {
                return null;
            }
        }

        ObjectList<ItemInputAssignment> assignments = new ObjectArrayList<>();
        for (ItemAssignmentEdge assignmentEdge : assignmentEdges) {
            if (assignmentEdge.edge().flow() > 0) {
                assignments.add(new ItemInputAssignment(
                        assignmentEdge.inputIndex(), assignmentEdge.edge().flow()));
            }
        }
        return ObjectLists.unmodifiable(assignments);
    }

    /** One physical input slot and the amount assigned to one or more recipe requirements. */
    public record ItemInputAssignment(int inputIndex, int amount) {}

    private record ItemAssignmentEdge(int inputIndex, ItemFlowNetwork.Edge edge) {}

    private static final class ItemFlowNetwork {

        private final ObjectList<ObjectList<Edge>> graph;
        private final int[] levels;
        private final int[] cursors;

        private ItemFlowNetwork(int nodeCount) {
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
            forward.reverseIndex = this.graph.get(to).size();
            reverse.reverseIndex = this.graph.get(from).size();
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
            private int reverseIndex;

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

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.DATA_RIPPER_REASSEMBLER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return DERecipes.DATA_RIPPER_REASSEMBLER_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
