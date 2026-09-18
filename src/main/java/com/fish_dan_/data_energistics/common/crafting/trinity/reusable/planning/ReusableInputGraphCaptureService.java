package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.capture.TrinityRecipeInputCapture;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.cache.TrinityCaptureCache;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/** Per-grid fair queue of request-local rule captures. All world reads and future completion run on the server. */
public final class ReusableInputGraphCaptureService {

    /**
     * Live server-thread capture boundary. Every method is read-only and called on the owning server
     * thread. Returned pattern/provider references may survive only inside the active capture task;
     * returned graph and rule values must already be immutable. Model changes must advance modelEpoch
     * or replace the frozen rules lookup so a capture can reject mixed generations.
     */
    public interface Source {

        /** @return latest complete graph, or empty while no publication has finished rebuilding */
        Optional<TrinityCraftingGraphSnapshot> graph();

        /**
         * @return live identity index used to verify revisions and resolve providers without advancing routing cursors
         */
        CraftingProviderPublicationIndex publications();

        /** @return stable list snapshot of live patterns advertised for this exact primary output */
        List<IPatternDetails> patternsFor(AEKey primaryOutput);

        /**
         * @return snapshot of visible physical item keys, without asserting available quantity or extraction rights
         *         This enumeration occurs once per capture generation. Its current synchronous network-copy cost is
         *         separate from the resumable rule-expansion cursor and must be included in provider budget audits.
         */
        List<AEItemKey> visibleItemKeys();

        /** @return frozen rule lookup with stable object identity until registrations change */
        ReusableInputRules rules();

        /** @return whether the current frozen lookup contains any registered rule sources */
        boolean hasRules();

        /** @return monotonic generation covering recipe/tag and other rule-model changes */
        long modelEpoch();

        /** @return authoritative recipe identity for this live pattern, or empty when no resolver proves one */
        Optional<ResourceLocation> recipeId(IPatternDetails pattern);
    }

    private final Source source;
    private final LongSupplier nanoClock;
    private final ObjectArrayFIFOQueue<Task> pending = new ObjectArrayFIFOQueue<>();
    private @Nullable TrinityCaptureCache cache;

    public ReusableInputGraphCaptureService(Source source, LongSupplier nanoClock) {
        this.source = source;
        this.nanoClock = nanoClock;
    }

    public CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> submit(
                                                                                          ServerLevel level, IActionSource actor, AEKey target, List<AEItemKey> additionalStates,
                                                                                          TrinityPlanningLimits limits) {
        Task task = new Task(level, actor, target, additionalStates, limits);
        pending.enqueue(task);
        return task.future;
    }

    /** Spends one shared tick allowance; unfinished requests rotate rather than restarting their cursor. */
    public void advance(long budgetNanos) {
        long started = nanoClock.getAsLong();
        int requests = pending.size();
        while (requests-- > 0 && !pending.isEmpty()) {
            long remaining = budgetNanos - (nanoClock.getAsLong() - started);
            if (remaining <= 0L) {
                return;
            }
            Task task = pending.dequeue();
            if (!task.future.isDone()) {
                try {
                    task.advance(remaining);
                } catch (RuntimeException exception) {
                    Data_Energistics.LOGGER.error("Reusable planning capture failed for {}", task.target, exception);
                    task.future.complete(failure(TrinityPlanningDiagnosticCode.INTERNAL_ERROR, "internal_error", exception.getClass().getSimpleName()));
                }
                if (!task.future.isDone()) {
                    pending.enqueue(task);
                }
            }
        }
    }

    /** Called when the grid unloads; no active task may later resurrect work in that grid. */
    public void clear() {
        cache = null;
        while (!pending.isEmpty()) {
            pending.dequeue().future.cancel(false);
        }
    }

    private final class Task {

        private final ServerLevel level;
        private final IActionSource actor;
        private final AEKey target;
        private final List<AEItemKey> additionalStates;
        private final TrinityPlanningLimits limits;
        private final CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> future = new CompletableFuture<>();
        private final TrinityPlanningControl control;
        private @Nullable CaptureGeneration generation;
        private @Nullable TrinityCraftingGraphSnapshot base;
        private ReusableInputRules rules;
        private List<AEItemKey> inventory = List.of();
        private boolean inventoryCaptured;
        private boolean recipeInputsCaptured;
        private @Nullable TrinityRecipeInputCapture recipeInputCursor;
        private final List<TrinityCraftingGraphPattern> completed = new ObjectArrayList<>();
        private final List<List<Endpoint>> completedEndpoints = new ObjectArrayList<>();
        private final ObjectList<TrinityCraftingGraphPattern> validationPatterns = new ObjectArrayList<>();
        private final Map<TrinityPatternIdentity, TrinityPlanningDiagnostic> fallbacks = new Object2ObjectLinkedOpenHashMap<>();
        private final ObjectLinkedOpenHashSet<List<TrinityBoundPatternInput>> merged = new ObjectLinkedOpenHashSet<>();
        private List<Endpoint> endpoints = List.of();
        private int patternIndex;
        private int endpointIndex;
        private int expandedCount;
        private int validationIndex;
        private @Nullable ReusableInputPlanningCursor cursor;
        private @Nullable TrinityPlanningDiagnostic patternFallback;

        private Task(ServerLevel level, IActionSource actor, AEKey target, List<AEItemKey> additionalStates,
                     TrinityPlanningLimits limits) {
            this.level = level;
            this.actor = actor;
            this.target = target;
            this.additionalStates = List.copyOf(additionalStates);
            this.limits = limits;
            this.rules = source.rules();
            // Capture spans server ticks and may wait for a current publication. The initial solve time budget
            // must not expire that waiting time; advance() bounds each tick and the owning future cancels work.
            this.control = TrinityPlanningControl.unbounded(future::isCancelled);
        }

        private void advance(long slice) {
            long started = nanoClock.getAsLong();
            long remaining = slice;
            while (!future.isDone() && remaining > 0L) {
                var beforeBase = base;
                int beforePattern = patternIndex;
                int beforeEndpoint = endpointIndex;
                int beforeValidation = validationIndex;
                var beforeEndpoints = endpoints;
                var beforeCursor = cursor;
                step(remaining);
                remaining = slice - (nanoClock.getAsLong() - started);
                if (beforeBase == base && beforePattern == patternIndex && beforeEndpoint == endpointIndex &&
                        beforeValidation == validationIndex && beforeEndpoints == endpoints && beforeCursor == cursor) {
                    return;
                }
            }
        }

        private void step(long slice) {
            if (control.cancellationRequested()) {
                future.complete(failure(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED, "cancelled", "capture_cancelled"));
                return;
            }
            Optional<TrinityCraftingGraphSnapshot> current = source.graph();
            if (current.isEmpty() || current.orElseThrow().revision() != source.publications().publicationRevision()) {
                return;
            }
            CaptureGeneration capture = generation;
            if (capture == null || !capture.cache().matches(current.orElseThrow(), source.rules(), source.modelEpoch(), level.registryAccess())) {
                capture = restart(current.orElseThrow());
            }
            if (base == null) {
                base = capture.dependencies().advance(slice, nanoClock, control);
                if (base == null) return;
                if (base.patterns().isEmpty()) {
                    if (validationIndex < validationPatterns.size()) {
                        if (!completedEndpoints.get(validationIndex).equals(discover(validationPatterns.get(validationIndex)))) {
                            restart(current.orElseThrow());
                        } else {
                            validationIndex++;
                            base = null;
                        }
                    } else {
                        future.complete(TrinityAlgorithmResult.success(capture.dependencies().result()));
                    }
                }
                return;
            }
            if (!recipeInputsCaptured) {
                if (!inventoryCaptured) {
                    ObjectLinkedOpenHashSet<AEItemKey> states = new ObjectLinkedOpenHashSet<>(source.visibleItemKeys());
                    states.addAll(additionalStates);
                    inventory = List.copyOf(states);
                    inventoryCaptured = true;
                }
                if (recipeInputCursor == null) {
                    recipeInputCursor = new TrinityRecipeInputCapture(base, capture.catalog(), inventory, level,
                            source::patternsFor, source::recipeId, limits.maxBindingVariants(), control, capture.cache()::canonicalKey);
                }
                var captured = recipeInputCursor.advance(slice, nanoClock);
                if (captured != null) {
                    if (!captured.successful()) {
                        future.complete(captured);
                    } else {
                        base = captured.value();
                        fallbacks.putAll(base.reusableInputFallbacks());
                        recipeInputsCaptured = true;
                        recipeInputCursor = null;
                    }
                }
                return;
            }
            if (!source.hasRules()) {
                finishWave(capture.dependencies(), base);
                return;
            }
            if (patternIndex == base.patterns().size()) {
                finishWave(capture.dependencies(), new TrinityCraftingGraphSnapshot(base.revision(), completed, fallbacks));
                return;
            }
            TrinityCraftingGraphPattern pattern = base.patterns().get(patternIndex);
            if (cursor != null) {
                ReusableInputPlanningExpansion.Result result = cursor.advance(slice, nanoClock);
                if (result != null) {
                    acceptCapture(capture.dependencies(), result);
                }
                return;
            }
            if (endpointIndex < endpoints.size()) {
                Endpoint endpoint = endpoints.get(endpointIndex);
                List<GenericStack> actual = new ObjectArrayList<>(pattern.inputs().size());
                int firstItem = -1;
                for (int slot = 0; slot < pattern.inputs().size(); slot++) {
                    var input = pattern.inputs().get(slot);
                    GenericStack template = input.alternatives().getFirst().stack();
                    actual.add(new GenericStack(template.what(), Math.multiplyExact(template.amount(), input.multiplier())));
                    if (firstItem < 0 && template.what() instanceof AEItemKey) {
                        firstItem = slot;
                    }
                }
                if (firstItem < 0) {
                    endpointIndex = endpoints.size();
                    return;
                }
                cursor = new ReusableInputPlanningCursor(ReusableInputContext.builder()
                        .pattern(endpoint.pattern()).actualInput(actual.get(firstItem)).exactInputs(actual).inputSlot(firstItem)
                        .ownership(ReusableInputContext.Ownership.CPU_SUPPLIED).actionSource(actor).level(level)
                        .recipeId(endpoint.recipeId()).machineMode(endpoint.target().mode()).target(endpoint.target().route()).build(),
                        inventory, rules, limits.maxBindingVariants(), control, pattern.reusableBindings());
                return;
            }
            if (endpoints.isEmpty() && endpointIndex == 0) {
                endpoints = discover(pattern);
                if (!endpoints.isEmpty()) {
                    return;
                }
            } else if (!endpoints.equals(discover(pattern))) {
                restart(current.orElseThrow());
                return;
            }
            if (merged.size() > limits.maxBindingVariants() - expandedCount) {
                retainLegacy(TrinityPlanningDiagnosticCode.VARIANT_LIMIT, "variant_limit", "request_binding_limit");
            }
            if (patternFallback != null) {
                fallbacks.put(pattern.identity(), patternFallback);
            }
            expandedCount += merged.size();
            completed.add(patternFallback != null || merged.isEmpty() ? pattern :
                    new TrinityCraftingGraphPattern(pattern.identity(), pattern.publication(), List.copyOf(merged)));
            completedEndpoints.add(endpoints);
            validationPatterns.add(pattern);
            patternIndex++;
            endpoints = List.of();
            endpointIndex = 0;
            merged.clear();
            patternFallback = null;
        }

        private CaptureGeneration restart(TrinityCraftingGraphSnapshot graph) {
            base = null;
            rules = source.rules();
            long epoch = source.modelEpoch();
            if (cache == null || !cache.matches(graph, rules, epoch, level.registryAccess())) {
                cache = new TrinityCaptureCache(graph, rules, epoch, level.registryAccess());
            }
            generation = new CaptureGeneration(graph, new TrinityCaptureDependencies(graph, target, cache, this::mayHaveRule), cache);
            inventory = List.of();
            inventoryCaptured = false;
            recipeInputsCaptured = false;
            recipeInputCursor = null;
            completed.clear();
            completedEndpoints.clear();
            validationPatterns.clear();
            fallbacks.clear();
            merged.clear();
            endpoints = List.of();
            patternIndex = 0;
            endpointIndex = 0;
            expandedCount = 0;
            validationIndex = 0;
            cursor = null;
            patternFallback = null;
            return generation;
        }

        private void finishWave(TrinityCaptureDependencies dependencies, TrinityCraftingGraphSnapshot graph) {
            dependencies.accept(graph);
            base = null;
            recipeInputsCaptured = false;
            recipeInputCursor = null;
            completed.clear();
            fallbacks.clear();
            patternIndex = 0;
        }

        private void acceptCapture(TrinityCaptureDependencies dependencies, ReusableInputPlanningExpansion.Result result) {
            cursor = null;
            endpointIndex++;
            if (result instanceof ReusableInputPlanningExpansion.Stopped stopped) {
                switch (stopped.reason()) {
                    case BINDING_LIMIT -> retainLegacy(TrinityPlanningDiagnosticCode.VARIANT_LIMIT, "variant_limit", "capture_binding_limit");
                    case DEADLINE -> future.complete(failure(TrinityPlanningDiagnosticCode.MIP_TIMEOUT, "timeout", "capture_deadline"));
                    case CANCELLED -> future.complete(failure(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED, "cancelled", "capture_cancelled"));
                }
                return;
            }
            var captured = (ReusableInputPlanningExpansion.Captured) result;
            if (captured.hasReusableInputs()) {
                TrinitySameItemPolicy policy = dependencies.policy();
                for (List<TrinityBoundPatternInput> assignment : captured.bindings()) {
                    if (assignment.stream().anyMatch(input -> input.reusableRule() == null && policy.allowsSameItem(input.template().what()))) {
                        retainLegacy(TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN, "unsupported_pattern", "component_aliased_material");
                        return;
                    }
                    merged.add(assignment);
                }
            }
        }

        private void retainLegacy(TrinityPlanningDiagnosticCode code, String translation, String reason) {
            patternFallback = new TrinityPlanningDiagnostic(code,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic." + translation),
                    Map.of("phase", "reusable_input_capture", "reason", reason, "action", "legacy_pattern"));
            merged.clear();
            endpointIndex = endpoints.size();
        }

        private boolean mayHaveRule(TrinityCraftingGraphPattern pattern) {
            if (!source.hasRules()) return false;
            for (IPatternDetails live : source.patternsFor(pattern.outputs().getFirst().what())) {
                if (live.getDefinition().equals(pattern.definition()) &&
                        TrinityPatternPublicationSignature.capture(live).equals(pattern.publication()) &&
                        rules.mayMatch(live, source.recipeId(live))) return true;
            }
            return false;
        }

        private List<Endpoint> discover(TrinityCraftingGraphPattern pattern) {
            List<Endpoint> result = new ObjectArrayList<>();
            ObjectLinkedOpenHashSet<Target> targets = new ObjectLinkedOpenHashSet<>();
            for (IPatternDetails live : source.patternsFor(pattern.outputs().getFirst().what())) {
                if (!live.getDefinition().equals(pattern.definition()) ||
                        !TrinityPatternPublicationSignature.capture(live).equals(pattern.publication())) {
                    continue;
                }
                Optional<ResourceLocation> recipeId = source.recipeId(live);
                if (!rules.mayMatch(live, recipeId)) {
                    continue;
                }
                for (var providerId : source.publications().providerIdsFor(live)) {
                    var provider = source.publications().resolveLiveProvider(providerId);
                    if (provider == null) {
                        continue;
                    }
                    ReusableCraftingProviderAdapter supported = CountedCraftingProviderAdapters.reusableAdapter(provider);
                    if (supported == null) {
                        continue;
                    }
                    for (Target executionTarget : supported.reusableTargetsFast(live, actor, level)) {
                        if (targets.add(executionTarget)) {
                            result.add(new Endpoint(live, supported, executionTarget, recipeId));
                        }
                    }
                }
            }
            result.sort(Comparator.comparing((Endpoint endpoint) -> endpoint.target().persistentIdentity())
                    .thenComparing(endpoint -> endpoint.target().route().stableIdentity())
                    .thenComparing(endpoint -> endpoint.target().mode().map(ResourceLocation::toString).orElse("")));
            return List.copyOf(result);
        }
    }

    private record CaptureGeneration(TrinityCraftingGraphSnapshot catalog, TrinityCaptureDependencies dependencies,
                                     TrinityCaptureCache cache) {}

    private record Endpoint(IPatternDetails pattern, ReusableCraftingProviderAdapter adapter, Target target,
                            Optional<ResourceLocation> recipeId) {}

    private static TrinityAlgorithmResult<TrinityCraftingGraphSnapshot> failure(TrinityPlanningDiagnosticCode code,
                                                                                String translation, String reason) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(code,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic." + translation),
                Map.of("phase", "reusable_input_capture", "reason", reason)));
    }
}
