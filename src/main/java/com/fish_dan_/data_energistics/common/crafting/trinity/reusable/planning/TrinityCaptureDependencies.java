package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.cache.TrinityCaptureCache;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Server-thread request closure over an immutable grid catalog. Each selected pattern is captured once per model
 * generation. Validated component/tool bindings may discover further producers; those enter the next capture wave.
 */
final class TrinityCaptureDependencies {

    private final TrinityCraftingGraphSnapshot catalog;
    private final AEKey target;
    private final TrinitySameItemPolicy policy;
    private final TrinityCaptureCache cache;
    private final Predicate<TrinityCraftingGraphPattern> ruleCandidate;
    private int ruleIndex;
    private boolean initialWave = true;
    private @Nullable TrinityCraftingGraphSnapshot cachedWave;
    private final ObjectOpenHashSet<AEKey> inputs = new ObjectOpenHashSet<>();
    private final ObjectOpenHashSet<TrinityPatternIdentity> selected = new ObjectOpenHashSet<>();
    private final ObjectArrayFIFOQueue<TrinityCraftingGraphPattern> pending = new ObjectArrayFIFOQueue<>();
    private final ObjectArrayList<TrinityCraftingGraphPattern> wave = new ObjectArrayList<>();
    private final ObjectArrayList<TrinityCraftingGraphPattern> captured = new ObjectArrayList<>();
    private final Object2ObjectLinkedOpenHashMap<TrinityPatternIdentity, TrinityPlanningDiagnostic> fallbacks = new Object2ObjectLinkedOpenHashMap<>();

    TrinityCaptureDependencies(TrinityCraftingGraphSnapshot catalog, AEKey target, TrinityCaptureCache cache,
                               Predicate<TrinityCraftingGraphPattern> ruleCandidate) {
        this.catalog = catalog;
        this.target = target;
        this.cache = cache;
        this.ruleCandidate = ruleCandidate;
        var seed = cache.seed(target);
        if (seed == null) {
            this.policy = catalog.sameItemPolicy(target);
            include(target, this.policy.allowsSameItem(target));
        } else {
            this.policy = seed.policy();
            this.ruleIndex = catalog.patterns().size();
            this.cachedWave = seed.graph();
            for (var pattern : seed.graph().patterns()) this.selected.add(pattern.identity());
        }
    }

    TrinitySameItemPolicy policy() {
        return this.policy;
    }

    /** Null means the tick slice expired, while an empty graph means the dependency closure is complete. */
    @Nullable
    TrinityCraftingGraphSnapshot advance(long slice, LongSupplier clock, TrinityPlanningControl control) {
        if (control.cancellationRequested()) return null;
        if (this.cachedWave != null) {
            var result = this.cachedWave;
            this.cachedWave = null;
            this.initialWave = false;
            return result;
        }
        long started = clock.getAsLong();
        // Rules can expose outputs absent from the publication. Keep every possible rule producer,
        // but avoid recipe/provider/inventory expansion for unrelated ordinary patterns.
        while (this.ruleIndex < this.catalog.patterns().size()) {
            if (control.cancellationRequested()) return null;
            var pattern = this.catalog.patterns().get(this.ruleIndex++);
            if (this.cache.mayHaveRule(pattern, this.ruleCandidate)) select(pattern);
            if (clock.getAsLong() - started >= slice) return null;
        }
        while (!this.pending.isEmpty()) {
            if (control.cancellationRequested()) return null;
            TrinityCraftingGraphPattern pattern = this.pending.dequeue();
            this.wave.add(pattern);
            includeInputs(pattern);
            if (clock.getAsLong() - started >= slice) return null;
        }
        Object2ObjectLinkedOpenHashMap<TrinityPatternIdentity, TrinityPlanningDiagnostic> diagnostics = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityCraftingGraphPattern pattern : this.wave) {
            TrinityPlanningDiagnostic diagnostic = this.catalog.reusableInputFallbacks().get(pattern.identity());
            if (diagnostic != null) diagnostics.put(pattern.identity(), diagnostic);
        }
        TrinityCraftingGraphSnapshot result = new TrinityCraftingGraphSnapshot(this.catalog.revision(), this.wave, diagnostics);
        if (this.initialWave) {
            this.cache.remember(this.target, result, this.policy);
            this.initialWave = false;
        }
        this.wave.clear();
        return result;
    }

    void accept(TrinityCraftingGraphSnapshot completed) {
        this.captured.addAll(completed.patterns());
        this.fallbacks.putAll(completed.reusableInputFallbacks());
        completed.patterns().forEach(this::includeInputs);
    }

    TrinityCraftingGraphSnapshot result() {
        return new TrinityCraftingGraphSnapshot(this.catalog.revision(), this.captured, this.fallbacks)
                .reachableSubgraph(this.target);
    }

    private void includeInputs(TrinityCraftingGraphPattern pattern) {
        if (pattern.reusableBindings().isEmpty()) {
            for (var input : pattern.inputs()) {
                for (var alternative : input.alternatives()) includeInput(alternative.stack().what());
            }
        } else {
            for (var assignment : pattern.reusableBindings()) {
                for (var binding : assignment) includeInput(binding.template().what());
            }
        }
    }

    private void includeInput(AEKey key) {
        if (this.inputs.add(key)) include(key, true);
    }

    private void include(AEKey key, boolean componentCandidates) {
        var producers = componentCandidates && key instanceof AEItemKey item ?
                this.catalog.captureProducersForItem(item.getItem()) : this.catalog.patternsProducing(key);
        for (TrinityCraftingGraphPattern pattern : producers) {
            select(pattern);
        }
    }

    private void select(TrinityCraftingGraphPattern pattern) {
        if (this.selected.add(pattern.identity())) this.pending.enqueue(pattern);
    }
}
