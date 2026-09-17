package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCanonicalNbt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.trinity.pattern.RoutedCraftingPatternDetails;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Alternative;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Input;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AECraftingPattern;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Request-local, server-thread capture of component-bearing crafting ingredients. Recipe display samples are not
 * exhaustive: non-strict component ingredients may also accept real stock and published intermediate outputs.
 * World references remain inside this cursor; the result retains only immutable keys and the original identities.
 */
public final class TrinityRecipeInputCapture {

    private final TrinityCraftingGraphSnapshot graph;
    private final ServerLevel level;
    private final Function<AEKey, List<IPatternDetails>> patternsFor;
    private final Function<IPatternDetails, Optional<ResourceLocation>> recipeId;
    private final TrinityPlanningControl control;
    private final int limit;
    private final Thread owner = Thread.currentThread();
    private final Iterator<AEItemKey> inventory;
    private final Iterator<AEKey> graphKeys;
    private final Object2ObjectLinkedOpenHashMap<Item, Object2ObjectAVLTreeMap<String, AEItemKey>> candidates = new Object2ObjectLinkedOpenHashMap<>();
    private final List<TrinityCraftingGraphPattern> completed = new ObjectArrayList<>();
    private final List<Input> inputs = new ObjectArrayList<>();
    private final List<List<TrinityBoundPatternInput>> bindings = new ObjectArrayList<>();
    private final Map<TrinityPatternIdentity, TrinityPlanningDiagnostic> fallbacks;
    private final ObjectLinkedOpenHashSet<Alternative> alternatives = new ObjectLinkedOpenHashSet<>();
    private IPatternDetails.IInput[] liveInputs = new IPatternDetails.IInput[0];
    private @Nullable AECraftingPattern craftingPattern;
    private @Nullable CraftingRecipe recipe;
    private int[] selected = new int[0];
    private Iterator<AEItemKey> matchingItems = ObjectList.<AEItemKey>of().iterator();
    private boolean indexed;
    private boolean capturingPattern;
    private boolean capturingSlot;
    private int patternIndex;
    private int slot;
    private int templateIndex;
    private @Nullable TrinityAlgorithmResult<TrinityCraftingGraphSnapshot> result;

    public TrinityRecipeInputCapture(TrinityCraftingGraphSnapshot graph, List<AEItemKey> inventory,
                                     ServerLevel level, Function<AEKey, List<IPatternDetails>> patternsFor,
                                     Function<IPatternDetails, Optional<ResourceLocation>> recipeId,
                                     int limit, TrinityPlanningControl control) {
        if (limit <= 0) throw new IllegalArgumentException("Recipe input capture requires a positive variant limit");
        this.graph = graph;
        this.fallbacks = new Object2ObjectLinkedOpenHashMap<>(graph.reusableInputFallbacks());
        this.inventory = inventory.iterator();
        this.graphKeys = graph.keys().iterator();
        this.level = level;
        this.patternsFor = patternsFor;
        this.recipeId = recipeId;
        this.limit = limit;
        this.control = control;
    }

    /** Returns null when the tick budget expires; callers must discard this cursor if the publication changes. */
    public @Nullable TrinityAlgorithmResult<TrinityCraftingGraphSnapshot> advance(long sliceNanos, LongSupplier nanoClock) {
        if (Thread.currentThread() != this.owner || sliceNanos <= 0) {
            throw new IllegalStateException("Recipe input capture requires its server thread and a positive tick budget");
        }
        long started = nanoClock.getAsLong();
        do {
            if (this.result != null) return this.result;
            if (this.control.cancellationRequested()) return fail(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED, "cancelled");
            if (this.control.deadlineExceeded()) return fail(TrinityPlanningDiagnosticCode.MIP_TIMEOUT, "timeout");
            step();
        } while (nanoClock.getAsLong() - started < sliceNanos);
        return this.result;
    }

    private void step() {
        if (!this.indexed) {
            if (this.inventory.hasNext()) index(this.inventory.next());
            else if (this.graphKeys.hasNext()) {
                if (this.graphKeys.next() instanceof AEItemKey item) index(item);
            } else this.indexed = true;
            return;
        }
        if (this.patternIndex == this.graph.patterns().size()) {
            this.result = TrinityAlgorithmResult.success(new TrinityCraftingGraphSnapshot(
                    this.graph.revision(), this.completed, this.fallbacks));
            return;
        }
        var pattern = this.graph.patterns().get(this.patternIndex);
        if (!this.capturingPattern) {
            this.liveInputs = resolveInputs(pattern);
            if (this.liveInputs.length == 0) {
                finish(pattern);
                return;
            }
            this.capturingPattern = true;
        }
        if (this.slot == pattern.inputs().size()) {
            if (this.inputs.equals(pattern.inputs())) {
                finish(pattern);
            } else if (this.selected.length == 0) {
                long combinations = 1;
                for (Input input : this.inputs) {
                    combinations *= input.alternatives().size();
                    if (combinations > this.limit) {
                        retainPublished(pattern, TrinityPlanningDiagnosticCode.VARIANT_LIMIT, "variant_limit");
                        return;
                    }
                }
                this.selected = new int[this.inputs.size()];
            } else {
                captureBinding(pattern);
            }
            return;
        }
        Input input = pattern.inputs().get(this.slot);
        if (!this.capturingSlot) {
            this.alternatives.addAll(input.alternatives());
            this.matchingItems = candidatesFor(input.alternatives().getFirst().stack().what());
            this.capturingSlot = true;
        }
        if (this.templateIndex == input.alternatives().size()) {
            this.inputs.add(new Input(input.multiplier(), new ObjectImmutableList<>(this.alternatives)));
            this.alternatives.clear();
            this.templateIndex = 0;
            this.capturingSlot = false;
            this.slot++;
            return;
        }
        Alternative template = input.alternatives().get(this.templateIndex);
        if (this.matchingItems.hasNext()) {
            AEItemKey candidate = this.matchingItems.next();
            if (this.liveInputs[this.slot].isValid(candidate, this.level)) {
                this.alternatives.add(new Alternative(new GenericStack(candidate, template.stack().amount()),
                        this.liveInputs[this.slot].getRemainingKey(candidate)));
                if (this.alternatives.size() > this.limit) retainPublished(pattern, TrinityPlanningDiagnosticCode.VARIANT_LIMIT, "variant_limit");
            }
        } else {
            this.templateIndex++;
            if (this.templateIndex < input.alternatives().size()) {
                this.matchingItems = candidatesFor(input.alternatives().get(this.templateIndex).stack().what());
            }
        }
    }

    private void index(AEItemKey key) {
        this.candidates.computeIfAbsent(key.getItem(), ignored -> new Object2ObjectAVLTreeMap<>())
                .put(TrinityCanonicalNbt.encode(key.toTagGeneric(this.level.registryAccess())), key);
    }

    private Iterator<AEItemKey> candidatesFor(AEKey key) {
        if (!(key instanceof AEItemKey item)) return ObjectList.<AEItemKey>of().iterator();
        var indexedItems = this.candidates.get(item.getItem());
        return indexedItems == null ? ObjectList.<AEItemKey>of().iterator() : indexedItems.values().iterator();
    }

    private IPatternDetails.IInput[] resolveInputs(TrinityCraftingGraphPattern pattern) {
        for (IPatternDetails live : this.patternsFor.apply(pattern.outputs().getFirst().what())) {
            IPatternDetails original = live instanceof RoutedCraftingPatternDetails routed ? routed.delegate() : live;
            if (!(original instanceof AECraftingPattern crafting) || !crafting.canSubstitute ||
                    !TrinityPatternPublicationSignature.capture(live).equals(pattern.publication()))
                continue;
            var recipe = this.recipeId.apply(live).flatMap(this.level.getRecipeManager()::byKey);
            // Standard recipes have item-based remainders. Each complete assignment is also checked below.
            if (recipe.isEmpty() || recipe.get().value().getClass() != ShapedRecipe.class &&
                    recipe.get().value().getClass() != ShapelessRecipe.class)
                continue;
            this.craftingPattern = crafting;
            this.recipe = (CraftingRecipe) recipe.get().value();
            return live.getInputs();
        }
        return new IPatternDetails.IInput[0];
    }

    private void captureBinding(TrinityCraftingGraphPattern pattern) {
        if (this.craftingPattern == null || this.recipe == null) {
            throw new IllegalStateException("Recipe candidate capture has no live crafting recipe");
        }
        List<TrinityBoundPatternInput> assignment = new ObjectArrayList<>(this.inputs.size());
        KeyCounter[] counters = new KeyCounter[this.inputs.size()];
        for (int index = 0; index < counters.length; index++) {
            Input input = this.inputs.get(index);
            Alternative alternative = input.alternatives().get(this.selected[index]);
            assignment.add(new TrinityBoundPatternInput(index, this.selected[index], alternative.stack(),
                    input.multiplier(), alternative.remainingKey()));
            counters[index] = new KeyCounter();
            counters[index].add(alternative.stack().what(), Math.multiplyExact(alternative.stack().amount(), input.multiplier()));
        }
        List<ItemStack> grid = new ObjectArrayList<>(9);
        for (int index = 0; index < 9; index++) grid.add(ItemStack.EMPTY);
        this.craftingPattern.fillCraftingGrid(counters, (index, stack) -> {
            // A valid fluid alternative stands for the encoded container in the native recipe grid.
            if (GenericStack.isWrapped(stack)) {
                var encoded = this.craftingPattern.getSparseInputs().get(index);
                if (encoded == null || !(encoded.what() instanceof AEItemKey item)) {
                    throw new IllegalStateException("Crafting fluid alternative has no encoded item container");
                }
                grid.set(index, item.toStack());
            } else grid.set(index, stack);
        });
        boolean consumed = true;
        for (KeyCounter counter : counters) {
            counter.removeZeros();
            consumed &= counter.isEmpty();
        }
        CraftingInput nativeInput = CraftingInput.ofPositioned(3, 3, grid).input();
        if (consumed && this.recipe.matches(nativeInput, this.level) &&
                pattern.outputs().getFirst().equals(GenericStack.fromItemStack(this.recipe.assemble(nativeInput, this.level.registryAccess())))) {
            this.bindings.add(new ObjectImmutableList<>(assignment));
        }
        int index = this.selected.length - 1;
        while (index >= 0 && ++this.selected[index] == this.inputs.get(index).alternatives().size()) {
            this.selected[index--] = 0;
        }
        if (index < 0) {
            if (this.bindings.isEmpty()) retainPublished(pattern, TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN, "unsupported_pattern");
            else finish(new TrinityCraftingGraphPattern(pattern.identity(), pattern.publication(), this.bindings));
        }
    }

    private void finish(TrinityCraftingGraphPattern pattern) {
        this.completed.add(pattern);
        this.patternIndex++;
        this.inputs.clear();
        this.bindings.clear();
        this.alternatives.clear();
        this.templateIndex = 0;
        this.selected = new int[0];
        this.craftingPattern = null;
        this.recipe = null;
        this.slot = 0;
        this.capturingPattern = false;
        this.capturingSlot = false;
        this.matchingItems = ObjectList.<AEItemKey>of().iterator();
    }

    private void retainPublished(TrinityCraftingGraphPattern pattern, TrinityPlanningDiagnosticCode code, String translation) {
        // An unrelated, expensive recipe must not reject every request on the grid. No partial candidates escape.
        this.fallbacks.put(pattern.identity(), new TrinityPlanningDiagnostic(code,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic." + translation),
                Object2ObjectMaps.unmodifiable(new Object2ObjectArrayMap<>(
                        new String[] { "phase", "limit", "action" },
                        new String[] { "recipe_input_capture", Integer.toString(this.limit), "legacy_pattern" }))));
        finish(pattern);
    }

    private TrinityAlgorithmResult<TrinityCraftingGraphSnapshot> fail(TrinityPlanningDiagnosticCode code, String translation) {
        this.result = TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(code,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic." + translation),
                Object2ObjectMaps.unmodifiable(new Object2ObjectArrayMap<>(
                        new String[] { "phase", "limit" },
                        new String[] { "recipe_input_capture", Integer.toString(this.limit) }))));
        return this.result;
    }
}
