package com.fish_dan_.data_energistics.recipe.reassembler;

import com.fish_dan_.data_energistics.recipe.matching.CountedItemInputMatcher;
import com.fish_dan_.data_energistics.registry.DERecipes;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

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
import lombok.Getter;
import org.jspecify.annotations.Nullable;

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
    private final ObjectList<DataRipperReassemblerIngredient> itemInputs;
    @Getter
    private final ObjectList<GenericStack> fluidInputs;
    private final ObjectList<DataReassemblerItemOutput> itemOutputs;
    @Getter
    private final ObjectList<GenericStack> fluidOutputs;
    @Getter
    private final int processTicks;
    @Nullable
    private final GenericStack keyInput;
    @Nullable
    private final GenericStack keyOutput;

    public DataRipperReassemblerRecipe(ObjectList<DataRipperReassemblerIngredient> itemInputs,
                                       ObjectList<GenericStack> fluidInputs,
                                       ObjectList<DataReassemblerItemOutput> itemOutputs,
                                       ObjectList<GenericStack> fluidOutputs,
                                       int processTicks,
                                       @Nullable GenericStack keyInput,
                                       @Nullable GenericStack keyOutput) {
        validateRecipe(itemInputs, fluidInputs, itemOutputs, fluidOutputs,
                processTicks, keyInput, keyOutput);
        this.itemInputs = FastUtilCollections.immutableList(itemInputs);
        this.fluidInputs = FastUtilCollections.immutableList(fluidInputs);
        this.itemOutputs = FastUtilCollections.immutableList(itemOutputs);
        this.fluidOutputs = FastUtilCollections.immutableList(fluidOutputs);
        this.processTicks = processTicks;
        this.keyInput = keyInput;
        this.keyOutput = keyOutput;
    }

    private static void validateRecipe(ObjectList<DataRipperReassemblerIngredient> itemInputs,
                                       ObjectList<GenericStack> fluidInputs,
                                       ObjectList<DataReassemblerItemOutput> itemOutputs,
                                       ObjectList<GenericStack> fluidOutputs,
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

    private static void validateFluids(ObjectList<GenericStack> fluids, String role) {
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

    public ObjectList<DataReassemblerItemOutput> getItemOutputDefinitions() {
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

    private boolean matchesKeyInput(ObjectList<@Nullable GenericStack> inputKeys) {
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

    private boolean matchesFluidInputs(ObjectList<GenericStack> inputFluids) {
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

    /** Finds physical input slots after aggregating identical stacks and assigns the required amounts to them. */
    public @Nullable ObjectList<ItemInputAssignment> findMatchingItemInputs(ObjectList<ItemStack> inputItems) {
        ObjectList<CountedItemInputMatcher.Assignment> assignments = CountedItemInputMatcher.match(
                getItemInputsForMatching(),
                inputItems);
        if (assignments == null) {
            return null;
        }

        return CountedItemInputMatcher.mapAssignments(assignments, ItemInputAssignment::new);
    }

    /** One physical input slot and the amount assigned to one or more recipe requirements. */
    public record ItemInputAssignment(int inputIndex, int amount) {}

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
