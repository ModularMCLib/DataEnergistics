package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.complement.TrinityFiringComplementOptimizer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.diagnostics.TrinitySolverFailureCapture;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Minimizes a structurally verified firing incumbent through exact non-negative integer reductions.
 * <p>
 * Sequential shifted-MIP implementation whose large request quantities remain model constants.
 */
public final class TrinityShiftedFiringOptimizer {

    /**
     * @return optimizer using sequential ojAlgo passes and exact BigInteger result verification
     */
    public static TrinityShiftedFiringOptimizer create() {
        return new TrinityShiftedFiringOptimizer(
                TrinityIntegerResultVerifier.create(),
                TrinityFiringComplementOptimizer.create());
    }

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final String UNSUPPORTED_PATTERN_KEY = "gui.data_energistics.trinity_planning.diagnostic.unsupported_pattern";
    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String TIMEOUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.timeout";
    private static final String NO_INTEGER_SOLUTION_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_integer_solution";
    private static final String INEXACT_RESULT_KEY = "gui.data_energistics.trinity_planning.diagnostic.inexact_result";

    private final TrinityIntegerResultVerifier integerVerifier;
    private final TrinityFiringComplementOptimizer complementOptimizer;

    TrinityShiftedFiringOptimizer(
                                  TrinityIntegerResultVerifier integerVerifier,
                                  TrinityFiringComplementOptimizer complementOptimizer) {
        this.integerVerifier = integerVerifier;
        this.complementOptimizer = complementOptimizer;
    }

    /**
     * @param component        unique-producer component
     * @param demand           complete component-wide lower bounds
     * @param available        immutable inventory snapshot
     * @param producibleInputs inputs supplied by predecessor DAG stages
     * @param firingUpperBound a structurally derived feasible upper vector; callers may claim a global proof only
     *                         when their component proof establishes that every better vector is componentwise lower
     * @param control          cancellation and shared deadline
     * @return exact optimum, non-terminal structural miss, or terminal shared-budget failure
     */
    public TrinityPlanningAttempt<TrinityFiringOptimization> optimize(
                                                                      TrinityStronglyConnectedComponent component,
                                                                      TrinityCycleDemand demand,
                                                                      Object2ObjectMap<AEKey, BigInteger> available,
                                                                      ObjectSet<AEKey> producibleInputs,
                                                                      Object2ObjectMap<TrinityPatternVariant, BigInteger> firingUpperBound,
                                                                      TrinityPlanningControl control) {
        if (component == null || demand == null || available == null || producibleInputs == null ||
                firingUpperBound == null || firingUpperBound.isEmpty() || control == null) {
            throw new IllegalArgumentException("A shifted Trinity firing request is incomplete");
        }
        ObjectSet<AEKey> internalKeys = FastUtilCollections.immutableSet(component.keys());
        ObjectList<TrinityPatternVariant> variants = component.cycleVariants().stream().sorted().collect(ObjectArrayList.toList());
        if (!FastUtilCollections.immutableSet(variants).containsAll(firingUpperBound.keySet()) || firingUpperBound.values().stream()
                .anyMatch(amount -> amount == null || amount.signum() < 0)) {
            return notApplicable(UNSUPPORTED_PATTERN_KEY);
        }
        Object2ObjectMap<TrinityPatternVariant, BigInteger> completeFiringUpperBound = completeFiringVector(
                variants,
                firingUpperBound);
        ObjectSet<AEKey> externalCostKeys = externalReserveKeys(variants, internalKeys, demand);
        ObjectLinkedOpenHashSet<AEKey> finiteExternal = new ObjectLinkedOpenHashSet<>();
        externalCostKeys.stream()
                .filter(key -> !producibleInputs.contains(key))
                .forEach(finiteExternal::add);
        ObjectSet<AEKey> finiteExternalKeys = FastUtilCollections.immutableSet(finiteExternal);
        for (TrinityPatternVariant variant : variants) {
            for (AEKey key : externalCostKeys) {
                if (variant.netChange().getOrDefault(key, ZERO).signum() > 0) {
                    return notApplicable(UNSUPPORTED_PATTERN_KEY);
                }
            }
        }

        ShiftedContext context = new ShiftedContext(
                variants,
                internalKeys,
                externalCostKeys,
                finiteExternalKeys,
                FastUtilCollections.immutableSet(producibleInputs),
                demand,
                available,
                completeFiringUpperBound,
                netChange(completeFiringUpperBound));
        int passes = 0;
        TrinityAlgorithmResult<SolvedShift> external = solve(
                context,
                ExternalPass.INSTANCE,
                control,
                ++passes);
        if (!external.successful()) {
            return unsuccessfulAttempt(external);
        }
        BigInteger optimalExternalSaving = external.value().externalSaving();

        TrinityAlgorithmResult<SolvedShift> seed = solve(
                context,
                new SeedPass(optimalExternalSaving),
                control,
                ++passes);
        if (!seed.successful()) {
            return unsuccessfulAttempt(seed);
        }
        BigInteger optimalSeed = seed.value().seedTotal();

        ObjectSet<TrinityPatternVariant> externallyFixed = new ObjectOpenHashSet<>();
        variants.stream()
                .filter(variant -> externalCost(variant, externalCostKeys).signum() > 0)
                .forEach(externallyFixed::add);
        Optional<Object2ObjectMap<TrinityPatternVariant, BigInteger>> complemented = this.complementOptimizer.minimize(
                component,
                demand,
                available,
                producibleInputs,
                completeFiringUpperBound,
                seed.value().reductions(),
                externallyFixed);
        if (complemented.isPresent()) {
            Object2ObjectMap<TrinityPatternVariant, BigInteger> firings = complemented.orElseThrow();
            Object2ObjectMap<AEKey, BigInteger> net = netChange(firings);
            if (!satisfiesDemand(net, demand) ||
                    !fitsAvailable(net, demand, available, internalKeys, finiteExternalKeys)) {
                return notApplicable(inexact("exact_conservation", "complement_vector").diagnostic());
            }
            return TrinityPlanningAttempt.provedOptimal(new TrinityFiringOptimization(
                    firings,
                    externalReserveTotal(net, demand, externalCostKeys),
                    optimalSeed));
        }

        TrinityAlgorithmResult<SolvedShift> firing = solve(
                context,
                new FiringPass(optimalExternalSaving, optimalSeed),
                control,
                ++passes);
        if (!firing.successful()) {
            return unsuccessfulAttempt(firing);
        }
        BigInteger optimalReduction = firing.value().reductionTotal();
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> fixedReductions = new Object2ObjectLinkedOpenHashMap<>();
        SolvedShift canonical = firing.value();
        for (TrinityPatternVariant variant : variants) {
            TrinityAlgorithmResult<SolvedShift> identity = solve(
                    context,
                    new IdentityPass(
                            optimalExternalSaving,
                            optimalSeed,
                            optimalReduction,
                            FastUtilCollections.immutableMap(new Object2ObjectLinkedOpenHashMap<>(fixedReductions)),
                            variant),
                    control,
                    ++passes);
            if (!identity.successful()) {
                return unsuccessfulAttempt(identity);
            }
            canonical = identity.value();
            fixedReductions.put(variant, canonical.reductions().getOrDefault(variant, ZERO));
        }
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> firings = new Object2ObjectLinkedOpenHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            BigInteger firingCount = completeFiringUpperBound.get(variant)
                    .subtract(canonical.reductions().getOrDefault(variant, ZERO));
            if (firingCount.signum() < 0) {
                return notApplicable(inexact("firing_lower", variant.patternIdentity().publicationEncoding())
                        .diagnostic());
            }
            if (firingCount.signum() > 0) {
                firings.put(variant, firingCount);
            }
        }
        Object2ObjectMap<AEKey, BigInteger> net = netChange(firings);
        if (!satisfiesDemand(net, demand) ||
                !fitsAvailable(net, demand, available, internalKeys, finiteExternalKeys)) {
            return notApplicable(inexact("exact_conservation", "shifted_vector").diagnostic());
        }
        return TrinityPlanningAttempt.provedOptimal(new TrinityFiringOptimization(
                FastUtilCollections.immutableMap(firings),
                externalReserveTotal(net, demand, externalCostKeys),
                optimalSeed));
    }

    private TrinityAlgorithmResult<SolvedShift> solve(
                                                      ShiftedContext context,
                                                      ShiftedPass pass,
                                                      TrinityPlanningControl control,
                                                      int passNumber) {
        if (control.cancellationRequested()) {
            return failure(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    CANCELLED_KEY,
                    FastUtilCollections.mapOf("passes", Integer.toString(passNumber - 1)));
        }
        if (control.deadlineExceeded()) {
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    TIMEOUT_KEY,
                    FastUtilCollections.mapOf("passes", Integer.toString(passNumber - 1)));
        }
        ModelData data = createModel(context, pass);
        configureDeadline(data.model(), control);
        Optimisation.Result result = TrinitySolverFailureCapture.solve(
                data.model(), Optimisation.Sense.MIN, "shifted_" + pass.getClass().getSimpleName());
        if (!result.getState().isOptimal()) {
            if (control.deadlineExceeded() || result.getState().isFeasible()) {
                return failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        TIMEOUT_KEY,
                        FastUtilCollections.mapOf("passes", Integer.toString(passNumber), "state", result.getState().name()));
            }
            return failure(
                    TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
                    NO_INTEGER_SOLUTION_KEY,
                    FastUtilCollections.mapOf("passes", Integer.toString(passNumber), "state", result.getState().name()));
        }

        ObjectArrayList<BigDecimal> rawValues = new ObjectArrayList<>(data.variables().size());
        for (Variable variable : data.variables()) {
            rawValues.add(result.get(data.model().indexOf(variable)));
        }
        TrinityAlgorithmResult<ObjectList<BigInteger>> verified = this.integerVerifier.verify(
                rawValues,
                data.model().options.integer().getIntegralityTolerance());
        if (!verified.successful()) {
            return TrinityAlgorithmResult.failure(verified.diagnostic());
        }
        SolvedShift solved = data.decode(verified.value(), context);
        if (!verifyPass(solved, pass)) {
            return inexact("objective_level", pass.getClass().getSimpleName());
        }
        return TrinityAlgorithmResult.success(solved);
    }

    private static void configureDeadline(ExpressionsBasedModel model, TrinityPlanningControl control) {
        if (!control.deadlineConfigured()) {
            return;
        }
        long remainingNanos = control.remainingNanos();
        long remainingMillis = Math.max(
                1L,
                TimeUnit.NANOSECONDS.toMillis(remainingNanos) +
                        (remainingNanos % 1_000_000L == 0L ? 0L : 1L));
        model.options.time_abort = remainingMillis;
        model.options.time_suffice = remainingMillis;
    }

    private static ModelData createModel(
                                         ShiftedContext context,
                                         ShiftedPass pass) {
        ExpressionsBasedModel model = new ExpressionsBasedModel();
        ObjectArrayList<Variable> variables = new ObjectArrayList<>();
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, Variable> reductions = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < context.variants().size(); index++) {
            TrinityPatternVariant variant = context.variants().get(index);
            Variable reduction = model.addVariable("reduction_" + index)
                    .lower(ZERO)
                    .upper(context.firingUpperBound().get(variant))
                    .integer();
            reductions.put(variant, reduction);
            variables.add(reduction);
        }
        Object2ObjectLinkedOpenHashMap<AEKey, Variable> seeds = new Object2ObjectLinkedOpenHashMap<>();
        int seedIndex = 0;
        for (AEKey key : context.internalKeys()) {
            Variable seed = model.addVariable("seed_" + seedIndex++)
                    .lower(ZERO)
                    .upper(seedUpperBound(context, key))
                    .integer();
            seeds.put(key, seed);
            variables.add(seed);
        }

        int constraintIndex = 0;
        for (AEKey key : context.internalKeys()) {
            Expression conservation = model.addExpression("internal_" + constraintIndex++);
            setShiftedNet(conservation, reductions, key);
            conservation.set(seeds.get(key), BigInteger.ONE);
            conservation.lower(context.demand().finalBalanceLowerBounds()
                    .getOrDefault(key, ZERO)
                    .subtract(context.baselineNet().getOrDefault(key, ZERO)));
        }
        for (AEKey key : context.finiteExternalKeys()) {
            Expression finiteInput = model.addExpression("external_" + constraintIndex++);
            setShiftedNet(finiteInput, reductions, key);
            finiteInput.lower(context.demand().finalBalanceLowerBounds()
                    .getOrDefault(key, ZERO)
                    .subtract(context.available().getOrDefault(key, ZERO))
                    .subtract(context.baselineNet().getOrDefault(key, ZERO)));
        }
        for (Object2ObjectMap.Entry<AEKey, BigInteger> bound : context.demand().requiredNetChangeLowerBounds().object2ObjectEntrySet()) {
            Expression requiredNet = model.addExpression("required_net_" + constraintIndex++);
            setShiftedNet(requiredNet, reductions, bound.getKey());
            requiredNet.lower(bound.getValue().subtract(
                    context.baselineNet().getOrDefault(bound.getKey(), ZERO)));
        }

        Expression externalSaving = model.addExpression("external_saving");
        reductions.forEach((variant, variable) -> {
            BigInteger saving = externalCost(variant, context.externalCostKeys());
            if (saving.signum() > 0) {
                externalSaving.set(variable, saving);
            }
        });
        Expression seedTotal = expression(model, "seed_total", seeds.values());
        seedTotal.lower(minimumFirstInternalInput(context.variants(), context.internalKeys()));
        Expression reductionTotal = expression(model, "reduction_total", reductions.values());

        if (pass instanceof ExternalPass) {
            externalSaving.weight(BigDecimal.ONE.negate());
        } else if (pass instanceof SeedPass seedPass) {
            externalSaving.lower(seedPass.externalSaving());
            seedTotal.weight(BigDecimal.ONE);
        } else if (pass instanceof FiringPass firingPass) {
            externalSaving.lower(firingPass.externalSaving());
            seedTotal.upper(firingPass.seedTotal());
            reductionTotal.weight(BigDecimal.ONE.negate());
        } else if (pass instanceof IdentityPass identityPass) {
            externalSaving.lower(identityPass.externalSaving());
            seedTotal.upper(identityPass.seedTotal());
            reductionTotal.lower(identityPass.reductionTotal());
            identityPass.fixedReductions().forEach((variant, value) -> reductions.get(variant)
                    .lower(value)
                    .upper(value));
            model.addExpression("identity_objective")
                    .set(reductions.get(identityPass.variant()), BigInteger.ONE)
                    .weight(BigDecimal.ONE);
        } else {
            throw new IllegalStateException("Unknown shifted Trinity optimization pass");
        }
        return new ModelData(
                model,
                FastUtilCollections.immutableList(variables),
                FastUtilCollections.immutableMap(reductions),
                FastUtilCollections.immutableMap(seeds));
    }

    private static void setShiftedNet(
                                      Expression expression,
                                      Object2ObjectMap<TrinityPatternVariant, Variable> reductions,
                                      AEKey key) {
        reductions.forEach((variant, variable) -> {
            BigInteger coefficient = variant.netChange().getOrDefault(key, ZERO).negate();
            if (coefficient.signum() != 0) {
                expression.set(variable, coefficient);
            }
        });
    }

    /**
     * Uses the finite verified incumbent as the bound for predecessor-produced cycle seeds. Supplying every bounded
     * internal input up front is sufficient for any non-negative reduction of that incumbent.
     */
    private static BigInteger seedUpperBound(ShiftedContext context, AEKey key) {
        BigInteger stored = context.available().getOrDefault(key, ZERO);
        if (!context.producibleInputs().contains(key)) {
            return stored;
        }
        BigInteger upstream = context.firingUpperBound().entrySet().stream()
                .map(entry -> entry.getKey().inputs()
                        .getOrDefault(key, ZERO)
                        .multiply(entry.getValue()))
                .reduce(ZERO, BigInteger::add);
        return stored.add(upstream);
    }

    private static Expression expression(
                                         ExpressionsBasedModel model,
                                         String name,
                                         Iterable<Variable> variables) {
        Expression expression = model.addExpression(name);
        for (Variable variable : variables) {
            expression.set(variable, BigInteger.ONE);
        }
        return expression;
    }

    private static boolean verifyPass(SolvedShift solved, ShiftedPass pass) {
        if (pass instanceof SeedPass seedPass) {
            return solved.externalSaving().equals(seedPass.externalSaving());
        }
        if (pass instanceof FiringPass firingPass) {
            return solved.externalSaving().equals(firingPass.externalSaving()) &&
                    solved.seedTotal().equals(firingPass.seedTotal());
        }
        if (pass instanceof IdentityPass identityPass) {
            return solved.externalSaving().equals(identityPass.externalSaving()) &&
                    solved.seedTotal().equals(identityPass.seedTotal()) &&
                    solved.reductionTotal().equals(identityPass.reductionTotal()) &&
                    identityPass.fixedReductions().entrySet().stream().allMatch(entry -> solved.reductions()
                            .getOrDefault(entry.getKey(), ZERO)
                            .equals(entry.getValue()));
        }
        return true;
    }

    private static ObjectSet<AEKey> externalReserveKeys(
                                                        ObjectList<TrinityPatternVariant> variants,
                                                        ObjectSet<AEKey> internalKeys,
                                                        TrinityCycleDemand demand) {
        ObjectLinkedOpenHashSet<AEKey> external = new ObjectLinkedOpenHashSet<>();
        variants.forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(external::add));
        demand.finalBalanceLowerBounds().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(external::add);
        return FastUtilCollections.immutableSet(external);
    }

    private static BigInteger externalCost(TrinityPatternVariant variant, ObjectSet<AEKey> externalReserveKeys) {
        return externalReserveKeys.stream()
                .map(key -> variant.netChange().getOrDefault(key, ZERO).negate())
                .reduce(ZERO, BigInteger::add);
    }

    private static BigInteger externalReserveTotal(
                                                   Object2ObjectMap<AEKey, BigInteger> net,
                                                   TrinityCycleDemand demand,
                                                   ObjectSet<AEKey> externalReserveKeys) {
        return externalReserveKeys.stream()
                .map(key -> demand.finalBalanceLowerBounds()
                        .getOrDefault(key, ZERO)
                        .subtract(net.getOrDefault(key, ZERO))
                        .max(ZERO))
                .reduce(ZERO, BigInteger::add);
    }

    private static BigInteger minimumFirstInternalInput(
                                                        ObjectList<TrinityPatternVariant> variants,
                                                        ObjectSet<AEKey> internalKeys) {
        return variants.stream()
                .map(variant -> variant.inputs().object2ObjectEntrySet().stream()
                        .filter(entry -> internalKeys.contains(entry.getKey()))
                        .map(entry -> entry.getValue())
                        .reduce(ZERO, BigInteger::add))
                .filter(amount -> amount.signum() > 0)
                .min(BigInteger::compareTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "A shifted Trinity component must consume an internal key"));
    }

    private static Object2ObjectMap<AEKey, BigInteger> netChange(Object2ObjectMap<TrinityPatternVariant, BigInteger> firings) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> net = new Object2ObjectLinkedOpenHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return FastUtilCollections.immutableMap(net);
    }

    private static Object2ObjectMap<TrinityPatternVariant, BigInteger> completeFiringVector(
                                                                                            ObjectList<TrinityPatternVariant> variants,
                                                                                            Object2ObjectMap<TrinityPatternVariant, BigInteger> sparse) {
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> complete = new Object2ObjectLinkedOpenHashMap<>();
        variants.forEach(variant -> complete.put(variant, sparse.getOrDefault(variant, ZERO)));
        return FastUtilCollections.immutableMap(complete);
    }

    private static boolean satisfiesDemand(Object2ObjectMap<AEKey, BigInteger> net, TrinityCycleDemand demand) {
        return demand.requiredNetChangeLowerBounds().entrySet().stream().allMatch(entry -> net
                .getOrDefault(entry.getKey(), ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static boolean fitsAvailable(
                                         Object2ObjectMap<AEKey, BigInteger> net,
                                         TrinityCycleDemand demand,
                                         Object2ObjectMap<AEKey, BigInteger> available,
                                         ObjectSet<AEKey> internalKeys,
                                         ObjectSet<AEKey> externalReserveKeys) {
        ObjectLinkedOpenHashSet<AEKey> bounded = new ObjectLinkedOpenHashSet<>(internalKeys);
        bounded.addAll(externalReserveKeys);
        return bounded.stream().allMatch(key -> available.getOrDefault(key, ZERO)
                .add(net.getOrDefault(key, ZERO))
                .compareTo(demand.finalBalanceLowerBounds().getOrDefault(key, ZERO)) >= 0);
    }

    private static <T> TrinityAlgorithmResult<T> inexact(String constraint, String value) {
        return failure(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                INEXACT_RESULT_KEY,
                FastUtilCollections.mapOf("constraint", constraint, "value", value));
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String translationKey,
                                                         Object2ObjectMap<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.translatable(translationKey),
                metadata));
    }

    private static <T> TrinityPlanningAttempt<T> notApplicable(String detail) {
        return notApplicable(TrinityPlanningDiagnostic.ofTranslationKey(
                TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN,
                detail));
    }

    private static <T> TrinityPlanningAttempt<T> notApplicable(TrinityPlanningDiagnostic diagnostic) {
        return TrinityPlanningAttempt.notApplicable(diagnostic);
    }

    private static <T> TrinityPlanningAttempt<T> unsuccessfulAttempt(TrinityAlgorithmResult<?> result) {
        TrinityPlanningDiagnostic diagnostic = result.diagnostic();
        if (diagnostic.code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED ||
                diagnostic.code() == TrinityPlanningDiagnosticCode.MIP_TIMEOUT) {
            return TrinityPlanningAttempt.terminal(diagnostic);
        }
        return TrinityPlanningAttempt.notApplicable(diagnostic);
    }

    private sealed interface ShiftedPass
                                         permits ExternalPass, SeedPass, FiringPass, IdentityPass {}

    private enum ExternalPass implements ShiftedPass {
        INSTANCE
    }

    private record SeedPass(BigInteger externalSaving) implements ShiftedPass {}

    private record FiringPass(BigInteger externalSaving, BigInteger seedTotal) implements ShiftedPass {}

    private record IdentityPass(
                                BigInteger externalSaving,
                                BigInteger seedTotal,
                                BigInteger reductionTotal,
                                Object2ObjectMap<TrinityPatternVariant, BigInteger> fixedReductions,
                                TrinityPatternVariant variant)
            implements ShiftedPass {}

    private record ShiftedContext(
                                  ObjectList<TrinityPatternVariant> variants,
                                  ObjectSet<AEKey> internalKeys,
                                  ObjectSet<AEKey> externalCostKeys,
                                  ObjectSet<AEKey> finiteExternalKeys,
                                  ObjectSet<AEKey> producibleInputs,
                                  TrinityCycleDemand demand,
                                  Object2ObjectMap<AEKey, BigInteger> available,
                                  Object2ObjectMap<TrinityPatternVariant, BigInteger> firingUpperBound,
                                  Object2ObjectMap<AEKey, BigInteger> baselineNet) {}

    private record ModelData(
                             ExpressionsBasedModel model,
                             ObjectList<Variable> variables,
                             Object2ObjectMap<TrinityPatternVariant, Variable> reductions,
                             Object2ObjectMap<AEKey, Variable> seeds) {

        private SolvedShift decode(ObjectList<BigInteger> values, ShiftedContext context) {
            Object2ObjectLinkedOpenHashMap<Variable, BigInteger> byVariable = new Object2ObjectLinkedOpenHashMap<>();
            for (int index = 0; index < this.variables.size(); index++) {
                byVariable.put(this.variables.get(index), values.get(index));
            }
            Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, BigInteger> decodedReductions = new Object2ObjectLinkedOpenHashMap<>();
            this.reductions.forEach((variant, variable) -> {
                BigInteger value = byVariable.get(variable);
                if (value.signum() > 0) {
                    decodedReductions.put(variant, value);
                }
            });
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> decodedSeeds = new Object2ObjectLinkedOpenHashMap<>();
            this.seeds.forEach((key, variable) -> {
                BigInteger value = byVariable.get(variable);
                if (value.signum() > 0) {
                    decodedSeeds.put(key, value);
                }
            });
            BigInteger externalSaving = decodedReductions.entrySet().stream()
                    .map(entry -> externalCost(entry.getKey(), context.externalCostKeys())
                            .multiply(entry.getValue()))
                    .reduce(ZERO, BigInteger::add);
            BigInteger reductionTotal = decodedReductions.values().stream().reduce(ZERO, BigInteger::add);
            BigInteger seedTotal = decodedSeeds.values().stream().reduce(ZERO, BigInteger::add);
            return new SolvedShift(
                    FastUtilCollections.immutableMap(decodedReductions),
                    FastUtilCollections.immutableMap(decodedSeeds),
                    externalSaving,
                    seedTotal,
                    reductionTotal);
        }
    }

    private record SolvedShift(
                               Object2ObjectMap<TrinityPatternVariant, BigInteger> reductions,
                               Object2ObjectMap<AEKey, BigInteger> seeds,
                               BigInteger externalSaving,
                               BigInteger seedTotal,
                               BigInteger reductionTotal) {}
}
